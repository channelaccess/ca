package org.epics.ca.impl;

import com.lmax.disruptor.EventFactory;
import org.epics.ca.*;
import org.epics.ca.data.Metadata;
import org.epics.ca.impl.TypeSupports.TypeSupport;
import org.epics.ca.impl.monitor.MonitorNotificationService;
import org.epics.ca.impl.monitor.MonitorNotificationServiceFactory;
import org.epics.ca.impl.requests.MonitorRequest;
import org.epics.ca.impl.requests.ReadNotifyRequest;
import org.epics.ca.impl.requests.WriteNotifyRequest;
import org.epics.ca.util.Holder;
import org.epics.ca.util.IntHashMap;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChannelImpl<T> implements Channel<T>, TransportClient
{
   // Get Logger
   private static final Logger logger = Logger.getLogger( ChannelImpl.class.getName() );

   protected final ContextImpl context;
   protected final String name;
   protected final Class<T> channelType;
   protected final int priority;

   protected final int cid;

   protected final int INVALID_SID = 0xFFFFFFFF;
   protected int sid = INVALID_SID;

   protected TCPTransport transport;

   protected final Map<String, Object> properties = new HashMap<String, Object> ();

   protected final AtomicReference<ConnectionState> connectionState =
         new AtomicReference<ConnectionState> (ConnectionState.NEVER_CONNECTED);

   protected final AtomicReference<AccessRights> accessRights =
         new AtomicReference<AccessRights> (AccessRights.NO_RIGHTS);

   protected final IntHashMap<ResponseRequest> responseRequests = new IntHashMap<ResponseRequest> ();

   protected final TypeSupport<T> typeSupport;

   protected final AtomicBoolean connectIssueed = new AtomicBoolean (false);
   protected final AtomicReference<CompletableFuture<Channel<T>>> connectFuture = new AtomicReference<> ();

   protected boolean allowCreation = false;

   protected volatile int nativeElementCount = 0;

   // on every connection loss the value gets incremented
   private final AtomicInteger connectionLossId = new AtomicInteger ();

   @SuppressWarnings( "unchecked" )
   private class DynamicTypeSupport implements TypeSupports.TypeSupport<T>
   {
      @SuppressWarnings( "rawtypes" )
      private AtomicReference<TypeSupport> delegate = new AtomicReference<> ();

      public void setDelegate( @SuppressWarnings( "rawtypes" ) TypeSupport typeSupport )
      {
         delegate.set (typeSupport);
      }

      @Override
      public T newInstance()
      {
         return (T) delegate.get ().newInstance ();
      }

      @Override
      public int getDataType()
      {
         return delegate.get ().getDataType ();
      }

      @Override
      public T deserialize( ByteBuffer buffer, T object, int count )
      {
         return (T) delegate.get ().deserialize (buffer, object, count);
      }

      @Override
      public int getForcedElementCount()
      {
         return delegate.get ().getForcedElementCount ();
      }

      @Override
      public void serialize( ByteBuffer buffer, T object, int count )
      {
         delegate.get ().serialize (buffer, object, count);
      }

      @Override
      public int serializeSize( T object, int count )
      {
         return delegate.get ().serializeSize (object, count);
      }
   }

   @SuppressWarnings( "unchecked" )
   public ChannelImpl( ContextImpl context, String name, Class<T> channelType, int priority )
   {
      this.context = context;
      this.name = name;
      this.channelType = channelType;
      this.priority = priority;

      this.typeSupport = channelType.equals (Object.class) ? new DynamicTypeSupport () : (TypeSupport<T>) TypeSupports.getTypeSupport (channelType);
      if ( this.typeSupport == null )
         throw new RuntimeException ("unsupported channel data type " + channelType);

      this.cid = context.generateCID ();

      // register before issuing search request
      context.registerChannel (this);
   }

   private TypeSupport<?> getTypeSupport( Class<?> metaTypeClass, Class<?> typeClass )
   {
      TypeSupport<?> metaTypeSupport = (TypeSupport<?>) TypeSupports.getTypeSupport (metaTypeClass, typeClass);
      if ( metaTypeSupport == null )
      {
         // dynamic (generic channel) support
         if ( typeSupport instanceof ChannelImpl.DynamicTypeSupport )
         {
            Class<?> nativeType = (Class<?>) properties.get (Constants.ChannelProperties.nativeType.name ());
            metaTypeSupport = (TypeSupport<?>) TypeSupports.getTypeSupport (metaTypeClass, nativeType);
         }

         if ( metaTypeSupport == null )
            throw new RuntimeException ("unsupported channel metadata type " + metaTypeClass + "<" + typeClass + ">");
      }

      return metaTypeSupport;
   }

   @Override
   public void close()
   {
      if ( connectionState.getAndSet( ConnectionState.CLOSED) == ConnectionState.CLOSED )
         return;

      // stop searching...
      context.getChannelSearchManager ().unregisterChannel (this);

      // destroy IOs
      disconnectPendingIO (true);

      // release transport
      if ( transport != null )
      {
         try
         {
            Messages.clearChannelMessage (transport, cid, sid);
            transport.flush ();
         }
         catch ( Throwable th )
         {
            // noop
         }

         transport.release (this);
         transport = null;
      }
   }

   @Override
   public String getName()
   {
      return name;
   }

   @Override
   public ConnectionState getConnectionState()
   {
      return connectionState.get ();
   }

   public int getConnectionLossId()
   {
      return connectionLossId.get ();
   }

   @Override
   public AccessRights getAccessRights()
   {
      return accessRights.get ();
   }

   public int getCID()
   {
      return cid;
   }

   public int getSID()
   {
      return sid;
   }

   @Override
   public CompletableFuture<Channel<T>> connectAsync()
   {
      if ( !connectIssueed.getAndSet (true) )
      {
         // this has to be submitted immediately
         initiateSearch ();

         CompletableFuture<Channel<T>> future = new CompletableFuture<> ();
         connectFuture.set (future);
         return future;
      }
      else
         throw new IllegalStateException ("Connect already issued on this channel instance.");
   }

   @Override
   public Channel<T> connect()
   {
      try
      {
         return connectAsync ().get ();
      }
      catch ( Throwable th )
      {
         throw new RuntimeException ("Failed to connect.", th);
      }
   }

   protected final Map<ConnectionListener, BiConsumer<Channel<T>, Boolean>> connectionListeners =
         new HashMap<> ();

   class ConnectionListener implements Listener
   {

      @Override
      public void close()
      {
         synchronized ( connectionListeners )
         {
            connectionListeners.remove (this);
         }
      }
   }

   @Override
   public Listener addConnectionListener(
         BiConsumer<Channel<T>, Boolean> handler
   )
   {

      ConnectionListener cl = new ConnectionListener ();
      synchronized ( connectionListeners )
      {
         connectionListeners.put (cl, handler);
      }

      return cl;
   }

   protected final Map<AccessRightsListener, BiConsumer<Channel<T>, AccessRights>> accessRightsListeners =
         new HashMap<> ();

   class AccessRightsListener implements Listener
   {

      @Override
      public void close()
      {
         synchronized ( accessRightsListeners )
         {
            accessRightsListeners.remove (this);
         }
      }
   }

   @Override
   public Listener addAccessRightListener(
         BiConsumer<Channel<T>, AccessRights> handler
   )
   {

      AccessRightsListener arl = new AccessRightsListener ();
      synchronized ( accessRightsListeners )
      {
         accessRightsListeners.put (arl, handler);
      }

      return arl;
   }

   @Override
   public T get()
   {
      try
      {
         return getAsync ().get ();
      }
      catch ( Throwable th )
      {
         throw new RuntimeException ("Failed to do get.", th);
      }
   }

   @Override
   public void put( T value )
   {
      try
      {
         CompletableFuture<Status> call = putAsync (value);
         Status status = call.get ();
         if ( !status.isSuccessful () )
         {
            throw new RuntimeException (status.getMessage ());
         }
      }
      catch ( Throwable th )
      {
         throw new RuntimeException ("Failed to do put.", th);
      }
   }

   @Override
   public void putNoWait( T value )
   {

      TCPTransport t = connectionRequiredCheck ();

      // check read access
      AccessRights currentRights = getAccessRights ();
      if ( currentRights != AccessRights.WRITE &&
            currentRights != AccessRights.READ_WRITE )
         throw new IllegalStateException ("No write rights.");

      int count = typeSupport.getForcedElementCount ();
      if ( count == 0 )
         count = Array.getLength (value);

      Messages.writeMessage (t, sid, cid, typeSupport, value, count);
      transport.flush ();
   }

   @Override
   public CompletableFuture<T> getAsync()
   {

      TCPTransport t = connectionRequiredCheck ();

      // check read access
      AccessRights currentRights = getAccessRights ();
      if ( currentRights != AccessRights.READ &&
            currentRights != AccessRights.READ_WRITE )
         throw new IllegalStateException ("No read rights.");

      return new ReadNotifyRequest<T> (this, t, sid, typeSupport);
   }

   @Override
   public CompletableFuture<Status> putAsync( T value )
   {

      TCPTransport t = connectionRequiredCheck ();

      // check read access
      AccessRights currentRights = getAccessRights ();
      if ( currentRights != AccessRights.WRITE &&
            currentRights != AccessRights.READ_WRITE )
         throw new IllegalStateException ("No write rights.");

      int count = typeSupport.getForcedElementCount ();
      if ( count == 0 )
         count = Array.getLength (value);

      return new WriteNotifyRequest<T> (this, t, sid, typeSupport, value, count);
   }

   @SuppressWarnings( "unchecked" )
   @Override
   public <MT extends Metadata<T>> MT get( @SuppressWarnings( "rawtypes" ) Class<? extends Metadata> clazz )
   {
      try
      {
         return (MT) getAsync (clazz).get ();
      }
      catch ( Throwable th )
      {
         throw new RuntimeException( "Failed to do get.", th );
      }
   }

   @SuppressWarnings( { "rawtypes", "unchecked" } )
   @Override
   public <MT extends Metadata<T>> CompletableFuture<MT> getAsync( Class<? extends Metadata> clazz )
   {
      final TCPTransport t = connectionRequiredCheck ();
      final TypeSupport<MT> metaTypeSupport = (TypeSupport<MT>) getTypeSupport (clazz, channelType);

      // check read access
      AccessRights currentRights = getAccessRights ();
      if ( currentRights != AccessRights.READ && currentRights != AccessRights.READ_WRITE )
      {
         throw new IllegalStateException("No read rights.");
      }

      return new ReadNotifyRequest<MT> (this, t, sid, metaTypeSupport);
   }

   static class HolderEventFactory<TT> implements EventFactory<Holder<TT>>
   {
      private final TypeSupport<TT> typeSupport;
      public HolderEventFactory( TypeSupport<TT> typeSupport )
      {
         this.typeSupport = typeSupport;
      }

      @Override
      public Holder<TT> newInstance()
      {
         return new Holder<TT> (typeSupport.newInstance ());
      }
   }

   @Override
   public Monitor<T> addValueMonitor( Consumer<? super T> handler, int mask )
   {
      if ( mask == 0 )
      {
         throw new IllegalArgumentException("null mask");
      }

      final TCPTransport transport = connectionRequiredCheck ();

      final MonitorNotificationServiceFactory serviceFactory = context.getMonitorNotificationServiceFactory();
      final MonitorNotificationService<T> notifier = serviceFactory.<T> getServiceForConsumer( handler );

      return new MonitorRequest<T>(this, transport, typeSupport, mask, notifier, handler );
   }

   @SuppressWarnings( "rawtypes" )
   @Override
   public <MT extends Metadata<T>> Monitor<MT> addMonitor( Class<? extends Metadata> clazz, Consumer<MT> handler, int mask )
   {
      if ( mask == 0 )
      {
         throw new IllegalArgumentException("null mask");
      }

      final TCPTransport transport = connectionRequiredCheck ();

      @SuppressWarnings( "unchecked" )
      final TypeSupport<MT> metaTypeSupport = (TypeSupport<MT>) getTypeSupport (clazz, channelType);
      final MonitorNotificationServiceFactory serviceFactory = context.getMonitorNotificationServiceFactory();
      final MonitorNotificationService<MT> notifier  = serviceFactory.<MT>getServiceForConsumer( handler );

      return new MonitorRequest<MT> (this, transport, metaTypeSupport, mask, notifier, handler );
   }

   @Override
   public Map<String, Object> getProperties()
   {
      // NOTE: could use Collections.unmodifiableMap(m) here, but leave it writable
      // in case some code needs to tag channels
      return properties;
   }

   protected final AtomicReference<Object> timerIdRef = new AtomicReference<Object> ();

   public void setTimerId( Object timerId )
   {
      timerIdRef.set (timerId);
   }

   public Object getTimerId()
   {
      return timerIdRef.get ();
   }

   /**
    * Initiate search (connect) procedure.
    */
   public synchronized void initiateSearch()
   {
      allowCreation = true;
      context.getChannelSearchManager ().registerChannel (this);
   }

   /**
    * Create a channel, i.e. submit create channel request to the server.
    * This method is called after search is complete.
    * <code>sid</code>, <code>typeCode</code>, <code>elementCount</code> might not be
    * valid, this depends on protocol revision.
    *
    * @param transport the transport.
    * @param sid the CA server ID.
    * @param typeCode the CA DBR typecode.
    * @param elementCount the number of elements to be associated  with each CA get/put/monitor operation.
    */
   public void createChannel( TCPTransport transport, int sid, short typeCode, int elementCount )
   {

      synchronized ( this )
      {
         // do not allow duplicate creation to the same transport
         if ( !allowCreation )
            return;
         allowCreation = false;

         // check existing transport
         if ( this.transport != null && this.transport != transport )
         {
            disconnectPendingIO (false);
            this.transport.release (this);
         }
         else if ( this.transport == transport )
         {
            // request to sent create request to same transport, ignore
            // this happens when server is slower (processing search requests) than client generating it
            return;
         }

         this.transport = transport;

         // revision < v4.4 supply this info already now
         if ( transport.getMinorRevision () < 4 )
         {
            this.sid = sid;
            this.nativeElementCount = elementCount;
            properties.put (Constants.ChannelProperties.nativeTypeCode.name (), typeCode);
            properties.put (Constants.ChannelProperties.nativeElementCount.name (), elementCount);
         }

         properties.put (Constants.ChannelProperties.remoteAddress.name (), transport.getRemoteAddress ());

         // do not submit CreateChannelRequest here, connection loss while submitting and lock
         // on this channel instance may cause deadlock
      }

      try
      {
         Messages.createChannelMessage (transport, name, cid);
         // flush immediately
         transport.flush ();
      }
      catch ( Throwable th )
      {
         createChannelFailed ();
      }
   }

   public void setAccessRights( int rightsCode )
   {
      // code matches enum ordinal
      setAccessRights (AccessRights.values ()[ rightsCode ]);
   }

   class AccessRightsStatefullEventSource extends StatefullEventSource
   {
      @SuppressWarnings( "unchecked" )
      @Override
      public void dispatch()
      {

         final AccessRights acr = getAccessRights ();

         // copy listeners
         final BiConsumer<Channel<T>, AccessRights>[] listeners;
         synchronized ( accessRightsListeners )
         {
            listeners = (BiConsumer<Channel<T>, AccessRights>[]) new BiConsumer[ accessRightsListeners.size () ];
            accessRightsListeners.values ().toArray (listeners);
         }

         // dispatch
         for ( int i = 0; i < listeners.length; i++ )
         {
            try
            {
               listeners[ i ].accept (ChannelImpl.this, acr);
            }
            catch ( Throwable th )
            {
               logger.log( Level.WARNING, "Unexpected exception caught when dispatching access rights listener event.", th);
            }
         }
      }
   }

   protected final AccessRightsStatefullEventSource accessRightsEventSource =
         new AccessRightsStatefullEventSource ();

   public void setAccessRights( AccessRights rights )
   {
      AccessRights previousRights = accessRights.getAndSet (rights);
      if ( previousRights != rights )
      {
         context.enqueueStatefullEvent (accessRightsEventSource);
      }
   }

   class ConnectionStateStatefullEventSource extends StatefullEventSource
   {
      @SuppressWarnings( "unchecked" )
      @Override
      public void dispatch()
      {

         final boolean connected = (getConnectionState () == ConnectionState.CONNECTED);

         // copy listeners
         final BiConsumer<Channel<T>, Boolean>[] listeners;
         synchronized ( connectionListeners )
         {
            listeners = (BiConsumer<Channel<T>, Boolean>[]) new BiConsumer[ connectionListeners.size () ];
            connectionListeners.values ().toArray (listeners);
         }

         // dispatch
         for ( int i = 0; i < listeners.length; i++ )
         {
            try
            {
               listeners[ i ].accept (ChannelImpl.this, connected);
            }
            catch ( Throwable th )
            {
               logger.log( Level.WARNING, "Unexpected exception caught when dispatching connection listener event.", th);
            }
         }
      }
   }

   protected final ConnectionStateStatefullEventSource connectionStateEventSource =
         new ConnectionStateStatefullEventSource ();

   public void setConnectionState( ConnectionState state )
   {
      ConnectionState previousCS = connectionState.getAndSet (state);
      if ( previousCS != state )
      {
         CompletableFuture<Channel<T>> cf = connectFuture.getAndSet (null);
         if ( cf != null )
            cf.complete (this);

         context.enqueueStatefullEvent (connectionStateEventSource);
      }
   }

   protected TCPTransport connectionRequiredCheck()
   {
      TCPTransport t = getTransport ();
      if ( connectionState.get () != ConnectionState.CONNECTED || t == null )
         throw new IllegalStateException ("Channel not connected.");
      return t;
   }

   public void resubscribeSubscriptions( Transport transport )
   {
      ResponseRequest[] requests;
      synchronized ( responseRequests )
      {
         int count = responseRequests.size ();
         if ( count == 0 )
            return;
         requests = new ResponseRequest[ count ];
         requests = responseRequests.toArray (requests);
      }

      for ( int i = 0; i < requests.length; i++ )
      {
         try
         {
            if ( requests[ i ] instanceof MonitorRequest<?> )
               ((MonitorRequest<?>) requests[ i ]).resubscribe (transport);
         }
         catch ( Throwable th )
         {
            logger.log( Level.WARNING, "Unexpected exception caught during resubscription notification.", th );
         }
      }
   }

   /**
    * Called when channel created succeeded on the server.
    * <code>sid</code> might not be valid, this depends on protocol revision.
    *
    * @param sid the CA server ID.
    * @param typeCode the CA DBR typecode.
    * @param elementCount the number of elements to be associated with each CA get/put/monitor operation.
    *
    * @throws IllegalStateException if the channel was in an unexpected stated.
    */
   public synchronized void connectionCompleted( int sid, short typeCode, int elementCount ) throws IllegalStateException
   {
      // do this silently
      if ( connectionState.get () == ConnectionState.CLOSED )
         return;

      // revision < v4.1 do not have access rights, grant all
      if ( transport.getMinorRevision () < 1 )
         setAccessRights (AccessRights.READ_WRITE);

      // revision < v4.4 supply this info already now
      if ( transport.getMinorRevision () >= 4 )
      {
         this.sid = sid;
         this.nativeElementCount = elementCount;
         properties.put (Constants.ChannelProperties.nativeTypeCode.name (), typeCode);
         properties.put (Constants.ChannelProperties.nativeElementCount.name (), elementCount);
      }

      // dynamic (generic channel) support
      if ( typeSupport instanceof ChannelImpl.DynamicTypeSupport )
      {
         TypeSupport<?> nativeTypeSupport = TypeSupports.getTypeSupport (typeCode, elementCount);
         if ( nativeTypeSupport == null )
         {
            logger.log( Level.SEVERE, "Type support for typeCode=" + typeCode + ", elementCount=" + elementCount + " is not supported, switching to String/String[]");
            if ( elementCount > 1 )
               nativeTypeSupport = TypeSupports.getTypeSupport (String[].class);
            else
               nativeTypeSupport = TypeSupports.getTypeSupport (String.class);
         }

         ((DynamicTypeSupport) typeSupport).setDelegate (nativeTypeSupport);
      }

      properties.put (Constants.ChannelProperties.nativeType.name (), typeSupport.newInstance ().getClass ());

      // user might create monitors in listeners, so this has to be done before this can happen
      // however, it would not be nice if events would come before connection event is fired
      // but this cannot happen since transport (TCP) is serving in this thread
      resubscribeSubscriptions (transport);
      setConnectionState (ConnectionState.CONNECTED);
   }

   public void createChannelFailed()
   {
      // ... and search again
      initiateSearch ();
   }

   /**
    * Send search message.
    *
    * @param transport the transport.
    * @param buffer the buffer to send.
    *
    * @return success status.
    */
   public boolean generateSearchRequestMessage( Transport transport, ByteBuffer buffer )
   {
      return Messages.generateSearchRequestMessage (transport, buffer, name, cid);
   }

   public synchronized TCPTransport getTransport()
   {
      return transport;
   }

   public int getPriority()
   {
      return priority;
   }

   public int getNativeElementCount()
   {
      return nativeElementCount;
   }

   @Override
   public void transportClosed()
   {
      disconnect (true);
   }

   public synchronized void disconnect( boolean reconnect )
   {
      if ( connectionState.get () != ConnectionState.CONNECTED && transport == null )
         return;

      setConnectionState (ConnectionState.DISCONNECTED);

      connectionLossId.incrementAndGet ();

      disconnectPendingIO (false);

      // release transport
      if ( transport != null )
      {
         transport.release (this);
         transport = null;
      }

      if ( reconnect )
         initiateSearch ();

   }

   private void disconnectPendingIO( boolean destroy )
   {
      Status status = destroy ? Status.CHANDESTROY : Status.DISCONN;

      ResponseRequest[] requests;
      synchronized ( responseRequests )
      {
         requests = new ResponseRequest[ responseRequests.size () ];
         requests = responseRequests.toArray (requests);
      }

      for ( int i = 0; i < requests.length; i++ )
      {
         try
         {
            requests[ i ].exception ( status.getStatusCode (),null );
         }
         catch ( Throwable th )
         {
            logger.log( Level.WARNING,"Unexpected exception caught during disconnect/destroy notification.", th );
         }
      }
   }

   /**
    * Register a response request.
    *
    * @param responseRequest response request to register.
    */
   public void registerResponseRequest( ResponseRequest responseRequest )
   {
      synchronized ( responseRequests )
      {
         responseRequests.put (responseRequest.getIOID (), responseRequest);
      }
   }

   /**
    * Unregister a response request.
    *
    * @param responseRequest response request to unregister.
    */
   public void unregisterResponseRequest( ResponseRequest responseRequest )
   {
      synchronized ( responseRequests )
      {
         responseRequests.remove (responseRequest.getIOID ());
      }
   }

   public TypeSupport<T> getTypeSupport()
   {
      return typeSupport;
   }
}
