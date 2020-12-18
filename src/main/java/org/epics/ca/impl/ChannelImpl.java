/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.epics.ca.*;
import org.epics.ca.annotation.CaChannel;
import org.epics.ca.data.Metadata;
import org.epics.ca.impl.TypeSupports.TypeSupport;
import org.epics.ca.impl.monitor.MonitorNotificationService;
import org.epics.ca.impl.monitor.MonitorNotificationServiceFactory;
import org.epics.ca.impl.requests.MonitorRequest;
import org.epics.ca.impl.requests.ReadNotifyRequest;
import org.epics.ca.impl.requests.WriteNotifyRequest;
import org.epics.ca.util.IntHashMap;
import org.epics.ca.util.logging.LibraryLogManager;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;


/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class ChannelImpl<T> implements Channel<T>, TransportClient
{

/*- Public attributes --------------------------------------------------------*/
/*- Protected attributes -----------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private final Map<AccessRightsListener, BiConsumer<Channel<T>, AccessRights>> accessRightsListeners = new HashMap<>();

   private final ContextImpl context;
   private final String name;
   private final Class<T> channelType;
   private final int priority;

   private final int cid;

   private final int INVALID_SID = 0xFFFFFFFF;
   private int sid = INVALID_SID;

   private TcpTransport tcpTransport;

   private final Map<String, Object> properties = new HashMap<> ();

   private final AtomicReference<ConnectionState> connectionState = new AtomicReference<>( ConnectionState.NEVER_CONNECTED );
   private final AtomicReference<AccessRights> accessRights = new AtomicReference<>( AccessRights.NO_RIGHTS );
   private final AtomicReference<Object> timerIdRef = new AtomicReference<>();

   private final AccessRightsStatefullEventSource accessRightsEventSource = new AccessRightsStatefullEventSource ();

   private final IntHashMap<ResponseRequest> responseRequests = new IntHashMap<> ();

   private final TypeSupport<T> typeSupport;

   private final AtomicBoolean connectIssued = new AtomicBoolean (false);
   private final AtomicReference<CompletableFuture<Channel<T>>> connectFuture = new AtomicReference<> ();

   private final ConnectionStateStatefullEventSource connectionStateEventSource = new ConnectionStateStatefullEventSource();
   private final Map<ConnectionListener, BiConsumer<Channel<T>, Boolean>> connectionListeners =  new HashMap<>();

   private boolean allowCreation = false;

   private volatile int nativeElementCount = 0;

   private volatile int elementsToRead = 0;

   // on every connection loss the value gets incremented
   private final AtomicInteger connectionLossId = new AtomicInteger ();

   private static final Logger logger = LibraryLogManager.getLogger( ChannelImpl.class );


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   @SuppressWarnings( "unchecked" )
   public ChannelImpl( ContextImpl context, String name, Class<T> channelType, int priority )
   {
      this.context = context;
      this.name = name;
      this.channelType = channelType;
      this.priority = priority;

      this.typeSupport = channelType.equals (Object.class) ? new DynamicTypeSupport () : (TypeSupport<T>) TypeSupports.getTypeSupportForType( channelType );
      if ( this.typeSupport == null )
      {
         throw new RuntimeException( "Unsupported channel data type " + channelType );
      }

      this.cid = context.generateCID ();

      // register before issuing search request
      context.registerChannel( this );
   }


/*- Public interface methods -------------------------------------------------*/

   @Override
   public void transportClosed()
   {
      disconnect( true );
   }

   @Override
   public void close()
   {
      if ( connectionState.getAndSet( ConnectionState.CLOSED ) == ConnectionState.CLOSED )
      {
         return;
      }

      // stop searching...
      context.getChannelSearchManager().unregisterChannel( this );

      // destroy IOs
      disconnectPendingIO( true );

      // release transport
      if ( tcpTransport != null )
      {
         try
         {
            Messages.clearChannelMessage( tcpTransport, cid, sid );
            tcpTransport.flush();
         }
         catch ( Throwable th )
         {
            // noop
         }

         tcpTransport.release( this );
         tcpTransport = null;
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
      return connectionState.get();
   }

   @Override
   public AccessRights getAccessRights()
   {
      return accessRights.get();
   }

   @Override
   public CompletableFuture<Channel<T>> connectAsync()
   {
      if ( !connectIssued.getAndSet (true) )
      {
         // BUG FIX: it is important that the future is defined
         // before initiating the search. This avoids a race condition
         // which occurs when the JVM is under load.
         final CompletableFuture<Channel<T>> future = new CompletableFuture<>();
         connectFuture.set( future );

         initiateSearch();

         return future;
      }
      else
         throw new IllegalStateException( "Connect already issued on this channel instance." );
   }

   @Override
   public Channel<T> connect()
   {
      try
      {
         return connectAsync ().get();
      }
      catch ( Throwable th )
      {
         throw new RuntimeException( "Failed to connect.", th );
      }
   }

   @Override
   public Listener addConnectionListener( BiConsumer<Channel<T>, Boolean> handler )
   {

      final ConnectionListener cl = new ConnectionListener();
      synchronized ( connectionListeners )
      {
         connectionListeners.put (cl, handler);
      }

      return cl;
   }

   @Override
   public Listener addAccessRightListener( BiConsumer<Channel<T>, AccessRights> handler )
   {
      final AccessRightsListener arl = new AccessRightsListener();
      synchronized ( accessRightsListeners )
      {
         accessRightsListeners.put( arl, handler);
      }
      return arl;
   }

   @Override
   public T get()
   {
      try
      {
         return getAsync().get();
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
         final CompletableFuture<Status> call = putAsync (value);
         final Status status = call.get ();
         if ( !status.isSuccessful () )
         {
            throw new RuntimeException (status.getMessage ());
         }
      }
      catch ( Throwable th )
      {
         throw new RuntimeException( "Failed to do put.", th );
      }
   }

   @Override
   public void putNoWait( T value )
   {
      final TcpTransport tcpTransport = getTcpTransportIfConnected();

      // check write access
      final AccessRights currentRights = getAccessRights();
      final boolean haveWriteRights = ( currentRights == AccessRights.WRITE ) || (currentRights == AccessRights.READ_WRITE );

      Validate.validState( haveWriteRights, "No write rights." );

      int count = typeSupport.getForcedElementCount();
      if ( count == 0 )
      {
         count = Array.getLength( value );
      }

      Messages.writeMessage( tcpTransport, sid, cid, typeSupport, value, count );
      tcpTransport.flush();
   }

   @Override
   public CompletableFuture<T> getAsync()
   {
      final TcpTransport tcpTransport = getTcpTransportIfConnected();

      // check read access
      final AccessRights currentRights = getAccessRights();
      final boolean haveReadRights = ( currentRights == AccessRights.READ ) || (currentRights == AccessRights.READ_WRITE );

      Validate.validState( haveReadRights, "No read rights." );

      return new ReadNotifyRequest<>(this, tcpTransport, sid, typeSupport );
   }

   @Override
   public CompletableFuture<Status> putAsync( T value )
   {
      final TcpTransport tcpTransport = getTcpTransportIfConnected();

      // check write access
      final AccessRights currentRights = getAccessRights();
      final boolean haveWriteRights = ( currentRights == AccessRights.WRITE ) || (currentRights == AccessRights.READ_WRITE );

      Validate.validState( haveWriteRights, "No write rights." );

      int count = typeSupport.getForcedElementCount ();
      if ( count == 0 )
      {
         count = Array.getLength( value );
      }

      return new WriteNotifyRequest<>(this, tcpTransport, sid, typeSupport, value, count);
   }

   @SuppressWarnings( "unchecked" )
   @Override
   public <MT extends Metadata<T>> MT get( @SuppressWarnings( "rawtypes" ) Class<? extends Metadata> clazz )
   {
      try
      {
         return (MT) getAsync( clazz ).get ();
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
      final TcpTransport tcpTransport = getTcpTransportIfConnected();
      final TypeSupport<MT> metaTypeSupport = (TypeSupport<MT>) getTypeSupport (clazz, channelType);

      // check read access
      final AccessRights currentRights = getAccessRights();
      final boolean haveReadRights = ( currentRights == AccessRights.READ ) || (currentRights == AccessRights.READ_WRITE );

      Validate.validState( haveReadRights, "No read rights." );

      return new ReadNotifyRequest<>(this, tcpTransport, sid, metaTypeSupport );
   }


   @Override
   public Monitor<T> addValueMonitor( Consumer<? super T> handler, int mask )
   {
      Validate.isTrue( mask != 0, "The mask cannot be zero." );

      final TcpTransport tcpTransport = getTcpTransportIfConnected();

      final MonitorNotificationServiceFactory serviceFactory = context.getMonitorNotificationServiceFactory();
      final MonitorNotificationService<T> notifier = serviceFactory.getServiceForConsumer( handler );

      return new MonitorRequest<>(this, tcpTransport, typeSupport, mask, notifier, handler );
   }

   @SuppressWarnings( "rawtypes" )
   @Override
   public <MT extends Metadata<T>> Monitor<MT> addMonitor( Class<? extends Metadata> clazz, Consumer<MT> handler, int mask )
   {
      Validate.isTrue( mask != 0, "The mask cannot be zero." );

      final TcpTransport tcpTransport = getTcpTransportIfConnected();

      @SuppressWarnings( "unchecked" )
      final TypeSupport<MT> metaTypeSupport = (TypeSupport<MT>) getTypeSupport(clazz, channelType );
      final MonitorNotificationServiceFactory serviceFactory = context.getMonitorNotificationServiceFactory();
      final MonitorNotificationService<MT> notifier  = serviceFactory.getServiceForConsumer(handler );

      return new MonitorRequest<>(this, tcpTransport, metaTypeSupport, mask, notifier, handler );
   }

   @Override
   public Map<String, Object> getProperties()
   {
      // NOTE: could use Collections.unmodifiableMap(m) here, but leave it writable
      // in case some code needs to tag channels
      return properties;
   }

   @Override
   public void setElementsToRead( int elementsToRead )
   {
      this.elementsToRead = elementsToRead;
   }

   @Override
   public int getElementsToRead()
   {
      return getElementsToRead(typeSupport);
   }

   @Override
   public int getNativeElementCount()
   {
      return nativeElementCount;
   }

/*- Public non-interface methods ---------------------------------------------*/

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
      return Messages.generateSearchRequestMessage( transport, buffer, name, cid );
   }

   public synchronized TcpTransport getTcpTransport()
   {
      return tcpTransport;
   }

   public int getElementsToRead(TypeSupport typeSupport)
   {
      int ret = (elementsToRead < 0) ?  nativeElementCount : elementsToRead;
      ret = Math.max(Math.min(ret,nativeElementCount),0);
      if(ret <=0) {
         ret = typeSupport.getForcedElementCount();
         if (ret == 0 && getTcpTransport().getMinorRevision() < 13) {
            ret = nativeElementCount;
         }
      }
      return ret;
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
         responseRequests.put( responseRequest.getIOID (), responseRequest );
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

   public int getCID()
   {
      return cid;
   }

   public int getSID()
   {
      return sid;
   }

   public void setAccessRights( AccessRights rights )
   {
      final AccessRights previousRights = accessRights.getAndSet( rights );
      if ( previousRights != rights )
      {
         context.enqueueStatefullEvent( accessRightsEventSource );
      }
   }

   public void setTimerId( Object timerId )
   {
      timerIdRef.set (timerId);
   }

   public Object getTimerId()
   {
      return timerIdRef.get ();
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
   public void createChannel( TcpTransport transport, int sid, short typeCode, int elementCount )
   {
      synchronized ( this )
      {
         // do not allow duplicate creation to the same transport
         if ( !allowCreation )
         {
            return;
         }
         allowCreation = false;

         // check existing transport
         if ( this.tcpTransport != null && this.tcpTransport != transport )
         {
            disconnectPendingIO(false );
            this.tcpTransport.release(this );
         }
         else if ( this.tcpTransport == transport )
         {
            // request to sent create request to same transport, ignore
            // this happens when server is slower (processing search requests) than client generating it
            return;
         }

         this.tcpTransport = transport;

         // revision < v4.4 supply this info already now
         if ( transport.getMinorRevision () < 4 )
         {
            this.sid = sid;
            this.nativeElementCount = elementCount;
            properties.put( Constants.ChannelProperties.nativeTypeCode.name (), typeCode );
            properties.put( Constants.ChannelProperties.nativeElementCount.name (), elementCount );
         }

         properties.put (Constants.ChannelProperties.remoteAddress.name (), transport.getRemoteAddress ());

         // do not submit CreateChannelRequest here, connection loss while submitting and lock
         // on this channel instance may cause deadlock
      }

      try
      {
         Messages.createChannelMessage( transport, name, cid );
         // flush immediately
         transport.flush();
      }
      catch ( Throwable th )
      {
         createChannelFailed ();
      }
   }

   public synchronized void disconnect( boolean reconnect )
   {
      if ( connectionState.get () != ConnectionState.CONNECTED && tcpTransport == null )
      {
         return;
      }

      setConnectionState( ConnectionState.DISCONNECTED );

      connectionLossId.incrementAndGet ();

      disconnectPendingIO (false);

      // release transport
      if ( tcpTransport != null )
      {
         tcpTransport.release( this );
         tcpTransport = null;
      }

      if ( reconnect )
      {
         initiateSearch();
      }
   }

/*- Package-level methods ----------------------------------------------------*/

   int getPriority()
   {
      return priority;
   }

   void createChannelFailed()
   {
      // ... and search again
      initiateSearch();
   }

   TypeSupport<T> getTypeSupport()
   {
      return typeSupport;
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
   synchronized void connectionCompleted( int sid, short typeCode, int elementCount ) throws IllegalStateException
   {
      // do this silently
      if ( connectionState.get () == ConnectionState.CLOSED )
      {
         return;
      }

      // revision < v4.1 do not have access rights, grant all
      if ( tcpTransport.getMinorRevision () < 1 )
      {
         setAccessRights( AccessRights.READ_WRITE );
      }

      // revision > v4.4 supply this info
      if ( tcpTransport.getMinorRevision () >= 4 )
      {
         this.sid = sid;
         this.nativeElementCount = elementCount;
         properties.put( Constants.ChannelProperties.nativeTypeCode.name(), typeCode );
         properties.put( Constants.ChannelProperties.nativeElementCount.name(), elementCount );
      }

      // dynamic (generic channel) support
      if ( typeSupport instanceof ChannelImpl.DynamicTypeSupport )
      {
         TypeSupport<?> nativeTypeSupport = TypeSupports.getTypeSupport( typeCode, elementCount );
         if ( nativeTypeSupport == null )
         {
            logger.log( Level.SEVERE, "Type support for typeCode=" + typeCode + ", elementCount=" + elementCount + " is not supported, switching to String/String[]");
            if ( elementCount > 1 )
            {
               nativeTypeSupport = TypeSupports.getTypeSupportForType( String[].class );
            }
            else
            {
               nativeTypeSupport = TypeSupports.getTypeSupportForType( String.class );
            }
         }

         ((DynamicTypeSupport) typeSupport).setDelegate( nativeTypeSupport );
      }

      properties.put (Constants.ChannelProperties.nativeType.name(), typeSupport.newInstance().getClass() );

      // user might create monitors in listeners, so this has to be done before this can happen
      // however, it would not be nice if events would come before connection event is fired
      // but this cannot happen since transport (TCP) is serving in this thread
      resubscribeSubscriptions( tcpTransport);
      setConnectionState( ConnectionState.CONNECTED );
   }

   /**
    * Initiate search (connect) procedure.
    */
   synchronized void initiateSearch()
   {
      allowCreation = true;
      context.getChannelSearchManager ().registerChannel (this);
   }

   void setAccessRights( int rightsCode )
   {
      // code matches enum ordinal
      setAccessRights( AccessRights.values()[ rightsCode ] );
   }

/*- Private methods ----------------------------------------------------------*/

   private int getConnectionLossId()
   {
      return connectionLossId.get ();
   }

   private void setConnectionState( ConnectionState state )
   {
      final ConnectionState previousCS = connectionState.getAndSet( state );
      if ( previousCS != state )
      {
         CompletableFuture<Channel<T>> cf = connectFuture.getAndSet (null );
         if ( cf != null )
         {
            cf.complete(this);
         }
         context.enqueueStatefullEvent( connectionStateEventSource );
      }
   }

   private void resubscribeSubscriptions( Transport transport )
   {
      ResponseRequest[] requests;
      synchronized ( responseRequests )
      {
         int count = responseRequests.size ();
         if ( count == 0 )
         {
            return;
         }
         requests = new ResponseRequest[ count ];
         requests = responseRequests.toArray (requests);
      }

      for ( ResponseRequest request : requests )
      {
         try
         {
            if ( request instanceof MonitorRequest<?> )
            {
               ((MonitorRequest<?>) request).resubscribe(transport);
            }
         }
         catch ( Throwable th )
         {
            logger.log( Level.WARNING, "Unexpected exception caught during resubscription notification.", th );
         }
      }
   }

   /**
    * Checks the current state of the TCP connection, returning the relevant
    * transport object when connected, or throwing an IllegalStateException
    * when not.
    *
    * @return the transport object.
    * @throws IllegalStateException if the TCP transport object is not connected.
    */
   private TcpTransport getTcpTransportIfConnected()
   {
      final TcpTransport transport = getTcpTransport();

      final boolean isConnected = ( connectionState.get() == ConnectionState.CONNECTED ) && ( transport != null );
      Validate.validState( isConnected, "Channel not connected" );

      return transport;
   }
   
   private TypeSupport<?> getTypeSupport( Class<?> metaTypeClass, Class<?> typeClass )
   {
      TypeSupport<?> metaTypeSupport = TypeSupports.getTypeSupportForMetatypeAndType( metaTypeClass, typeClass );
      if ( metaTypeSupport == null )
      {
         // dynamic (generic channel) support
         if ( typeSupport instanceof ChannelImpl.DynamicTypeSupport )
         {
            final Class<?> nativeType = (Class<?>) properties.get( Constants.ChannelProperties.nativeType.name() );
            metaTypeSupport = TypeSupports.getTypeSupportForMetatypeAndType( metaTypeClass, nativeType );
         }

         if ( metaTypeSupport == null )
         {
            throw new RuntimeException( "Unsupported channel metadata type " + metaTypeClass + "<" + typeClass + ">");
         }
      }

      return metaTypeSupport;
   }

   private void disconnectPendingIO( boolean destroy )
   {
      final Status status = destroy ? Status.CHANDESTROY : Status.DISCONN;

      ResponseRequest[] requests;
      synchronized ( responseRequests )
      {
         requests = new ResponseRequest[ responseRequests.size () ];
         requests = responseRequests.toArray (requests);
      }

      for ( ResponseRequest request : requests )
      {
         try
         {
            request.exception( status.getStatusCode(), null);
         }
         catch ( Throwable th )
         {
            logger.log( Level.WARNING, "Unexpected exception caught during disconnect/destroy notification.", th );
         }
      }
   }


/*- Nested classes -----------------------------------------------------------*/

   /**
    * AccessRightsStatefullEventSource
    */
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
         for ( BiConsumer<Channel<T>, AccessRights> listener : listeners )
         {
            try
            {
               listener.accept(ChannelImpl.this, acr);
            }
            catch ( Throwable th )
            {
               logger.log( Level.WARNING, "Unexpected exception caught when dispatching access rights listener event.", th );
            }
         }
      }
   }

   /**
    * AccessRightsListener
    */
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

   /**
    * ConnectionListener
    */
   class ConnectionListener implements Listener
   {
      @Override
      public void close()
      {
         synchronized ( connectionListeners )
         {
            connectionListeners.remove( this );
         }
      }
   }

   /**
    * ConnectionStateStatefullEventSource
    */
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
         for ( BiConsumer<Channel<T>, Boolean> listener : listeners )
         {
            try
            {
               listener.accept(ChannelImpl.this, connected);
            }
            catch ( Throwable th )
            {
               logger.log( Level.WARNING, "Unexpected exception caught when dispatching connection listener event.", th);
            }
         }
      }
   }

   /**
    * DynamicTypeSupport
    */
   @SuppressWarnings( "unchecked" )
   private class DynamicTypeSupport implements TypeSupports.TypeSupport<T>
   {
      @SuppressWarnings( "rawtypes" )
      private final AtomicReference<TypeSupport> delegate = new AtomicReference<>();

      public void setDelegate( @SuppressWarnings( "rawtypes" ) TypeSupport typeSupport )
      {
         delegate.set( typeSupport );
      }

      @Override
      public T newInstance()
      {
         return (T) delegate.get().newInstance ();
      }

      @Override
      public int getDataType()
      {
         return delegate.get().getDataType ();
      }

      @Override
      public T deserialize( ByteBuffer buffer, T object, int count )
      {
         return (T) delegate.get().deserialize( buffer, object, count );
      }

      @Override
      public int getForcedElementCount()
      {
         return delegate.get().getForcedElementCount();
      }

      @Override
      public void serialize( ByteBuffer buffer, T object, int count )
      {
         delegate.get().serialize( buffer, object, count );
      }

      @Override
      public int serializeSize( T object, int count )
      {
         return delegate.get().serializeSize (object, count);
      }
   }

}
