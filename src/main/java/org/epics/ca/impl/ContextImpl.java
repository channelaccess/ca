/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl;

/*- Imported packages --------------------------------------------------------*/

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.Validate;
import org.epics.ca.Channel;
import org.epics.ca.Constants;
import org.epics.ca.impl.monitor.MonitorNotificationServiceFactoryCreator;
import org.epics.ca.impl.monitor.MonitorNotificationServiceFactory;
import org.epics.ca.impl.reactor.Reactor;
import org.epics.ca.impl.reactor.ReactorHandler;
import org.epics.ca.impl.reactor.lf.LeaderFollowersHandler;
import org.epics.ca.impl.reactor.lf.LeaderFollowersThreadPool;
import org.epics.ca.impl.repeater.CARepeaterStarter;
import org.epics.ca.impl.search.ChannelSearchManager;
import org.epics.ca.util.IntHashMap;
import org.epics.ca.util.logging.LibraryLogManager;
import org.epics.ca.util.net.InetAddressUtil;
import org.epics.ca.util.sync.NamedLockPattern;

import static org.epics.ca.Constants.*;


/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class ContextImpl implements AutoCloseable
{

/*- Public attributes --------------------------------------------------------*/
/*- Private static attributes ------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( ContextImpl.class );

   /**
    * Context instance.
    */
   private static final int LOCK_TIMEOUT = 20 * 1000;   // 20s


/*- Private final attributes (initialised immediately) -----------------------*/

   /**
    * Context instance.
    */
   private final NamedLockPattern namedLocker = new NamedLockPattern();

   /**
    * Timer.
    */
   private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

   /**
    * General executor service (e.g. event dispatcher).
    */
   private final ExecutorService executorService = Executors.newSingleThreadExecutor();

   /**
    * TCP transport registry.
    */
   private final TransportRegistry transportRegistry = new TransportRegistry();

   /**
    * UDP broadcast transport - used for channel search requests.
    */
   private final AtomicReference<BroadcastTransport> broadcastTransport = new AtomicReference<>();

   /**
    * Beacon handler map.
    */
   private final Map<InetSocketAddress, BeaconHandler> beaconHandlers = new HashMap<>();

   /**
    * Map of channels (keys are CIDs).
    */
   private final IntHashMap<ChannelImpl<?>> channelsByCID = new IntHashMap<>();

   /**
    * Map of requests (keys are IOID).
    */
   private final IntHashMap<ResponseRequest> responseRequests = new IntHashMap<>();

   /**
    * Closed flag.
    */
   private final AtomicBoolean closed = new AtomicBoolean();


/*- Private final attributes (initialised by constructor) --------------------*/

   /**
    * The EPICS Channel-Access configuration variables to be used for this context.
    */
   private final ProtocolConfiguration protocolConfiguration;

   /**
    * Factory to be used for creating MonitorNotificationService instances.
    */
   private final MonitorNotificationServiceFactory monitorNotificationServiceFactory;

   /**
    * Repeater registration future.
    */
   private final ScheduledFuture<?> repeaterRegistrationFuture;

   /**
    * Leader/followers thread pool.
    */
   private final LeaderFollowersThreadPool leaderFollowersThreadPool;

   /**
    * Reactor.
    */
   private final Reactor reactor;

   /**
    * Channel search manager. Manages UDP search requests.
    */
   private final ChannelSearchManager channelSearchManager;

   /**
    * Cached hostname.
    */
   private final String hostName;

   /**
    * Cached username.
    */
   private final String userName;


/*- Private attributes --------------------------------------------------------*/

   /**
    * Last CID cache.
    */
   private int lastCID = 0;


   /**
    * Last IOID cache.
    */
   private int lastIOID = 0;


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Create an instance whose channel-access protocol configuration is based on
    * the values of operating system environmental variables, the values of the
    * the Java system properties, or twhen not otherwise specified, the library
    * defaults.
    */
   public ContextImpl()
   {
      this ( System.getProperties() );
   }

   /**
    * Create an instance whose channel-access protocol configuration is based on
    * the values of operating system environmental variables, the values of
    * the supplied properties object, or when not otherwise specified, the
    * library defaults.
    *
    * @param properties an object whose definitions may override the
    *   values set in the operating system environment.
    * @throws NullPointerException if the properties argument was null.
    */
   public ContextImpl( Properties properties )
   {
      Validate.notNull( properties, "null properties" );

      // Instantiate the protocol configuration object.
      this.protocolConfiguration = new ProtocolConfiguration( properties );

      // Capture the hostname and username.
      hostName = InetAddressUtil.getHostName();
      userName = System.getProperty("user.name", "nobody" );

      // Create the asynchronous IO reactor.
      try
      {
         reactor = new Reactor();
      }
      catch ( IOException e )
      {
         throw new RuntimeException( "Failed to initialize reactor.", e);
      }

      // Initiate leader/followers processing.
      leaderFollowersThreadPool = new LeaderFollowersThreadPool();

      // Spawn the initial leader.
      leaderFollowersThreadPool.promoteLeader( reactor::process );

      // Initialise the UDP transport.
      broadcastTransport.set( initializeUDPTransport() );

      // Start the CA Repeater for this context (when enabled and when not already running).
      try
      {
          CARepeaterStarter.startRepeaterOnPort( getRepeaterPort() );
      }
      catch ( RuntimeException ex )
      {
         logger.log(Level.WARNING, "Failed to start CA Repeater on port " + protocolConfiguration.getRepeaterPort(), ex);
      }

      // Start the task to register with CA Repeater (even if the lifecycle is not managed by this library).
      final InetSocketAddress repeaterLocalAddress = new InetSocketAddress( InetAddress.getLoopbackAddress(),
                                                                            protocolConfiguration.getRepeaterPort() );

      final Runnable repeaterRegistrationTask = new RepeaterRegistrationTask( repeaterLocalAddress );
      repeaterRegistrationFuture = timer.scheduleWithFixedDelay( repeaterRegistrationTask,
                                                                 CA_REPEATER_INITIAL_REGISTRATION_DELAY,
                                                                 CA_REPEATER_REGISTRATION_INTERVAL,
                                                                 TimeUnit.MILLISECONDS );

      // Create the channel search manager.
      channelSearchManager = new ChannelSearchManager( broadcastTransport.get() );

      // Create the monitor notification engine.
      final String monitorNotifierImpl = LibraryConfiguration.getInstance().getMonitorNotifierImplementation();
      monitorNotificationServiceFactory = MonitorNotificationServiceFactoryCreator.create( monitorNotifierImpl );
   }

/*- Public methods -----------------------------------------------------------*/

   /**
    * Creates a new channel of the specified name and type and with the specified priority.
    *
    * @param channelName the name of the Channel (which should follow standard
    *   EPICS naming conventions).
    *
    * @param channelType the type of the channel which will determine the
    *    type used when communicating with the remote channel access server.
    *    Note: &lt;Object&gt; can be used to force the channel to use the
    *    native type on the server.
    *
    * @param <T> the type parameter.
    * @param priority the priority to be registered with the channel access server.
    * @return the channel.
    *
    * @throws NullPointerException if the channel name was null.
    * @throws NullPointerException if the channel type was null.
    * @throws IllegalArgumentException if the channel name was an empty string.
    * @throws IllegalArgumentException if the channel name was of an unreasonable length.
    * @throws IllegalArgumentException if the channel type was invalid.
    * @throws IllegalArgumentException if the priority was outside the allowed range.
    * @throws IllegalStateException if the context was already closed.
    */
   public <T> Channel<T> createChannel( String channelName, Class<T> channelType, int priority )
   {
      Validate.validState( ! closed.get(), "context closed" );
      Validate.notEmpty( channelName, "null or empty channel name" );
      Validate.isTrue(channelName.length() <= Math.min( MAX_UDP_SEND - CA_MESSAGE_HEADER_SIZE, UNREASONABLE_CHANNEL_NAME_LENGTH ), "name too long" );
      Validate.notNull( channelType, "null channel type" );
      Validate.isTrue( TypeSupports.isNativeType( channelType ) || channelType.equals( Object.class ), "invalid channel native type" );
      Validate.inclusiveBetween( CHANNEL_PRIORITY_MIN, CHANNEL_PRIORITY_MAX, priority,"priority out of bounds" );

      return new ChannelImpl<>( this, channelName, channelType, priority );
   }

   /**
    * Closes the context, disposing of all underlying resources.
    */
   @Override
   public void close()
   {
      logger.finest( "Closing context." );
      if ( closed.getAndSet (true) )
      {
         return;
      }

      // Stop the CA Repeater for this context (when enabled and when no other context needs it)
      CARepeaterStarter.stopRepeaterOnPort( getRepeaterPort() );

      channelSearchManager.cancel();
      broadcastTransport.get().close();

      // this will also close all CA transports
      destroyAllChannels();

      reactor.shutdown();
      leaderFollowersThreadPool.shutdown ();
      timer.shutdown();

      // Dispose of the monitor service factory and all services which it has created
      monitorNotificationServiceFactory.close();

      executorService.shutdown();
      try
      {
         executorService.awaitTermination (3, TimeUnit.SECONDS);
      }
      catch ( InterruptedException e )
      {
         // noop
      }
      executorService.shutdownNow();
   }

   /**
    * Register response request.
    *
    * @param request request to register.
    * @return request ID (IOID).
    */
   public int registerResponseRequest( ResponseRequest request )
   {
      synchronized ( responseRequests )
      {
         int ioid = generateIOID ();
         responseRequests.put (ioid, request);
         return ioid;
      }
   }

   /**
    * Unregister response request.
    *
    * @param request the request.
    * @return removed object, can be <code>null</code>
    */
   public ResponseRequest unregisterResponseRequest( ResponseRequest request )
   {
      synchronized ( responseRequests )
      {
         return responseRequests.remove (request.getIOID ());
      }
   }

/*- Package-level methods ----------------------------------------------------*/

   /**
    * Searches for a response request with given channel IOID.
    *
    * @param ioid I/O ID.
    * @return request response with given I/O ID.
    */
   ResponseRequest getResponseRequest( int ioid )
   {
      synchronized ( responseRequests )
      {
         return responseRequests.get (ioid);
      }
   }

   /**
    * Searches for a channel with given channel ID.
    *
    * @param channelID CID.
    * @return channel with given CID, <code>null</code> if non-existent.
    */
   ChannelImpl<?> getChannel( int channelID )
   {
      synchronized ( channelsByCID )
      {
         return channelsByCID.get (channelID);
      }
   }

   /**
    * Search response from server (channel found).
    *
    * @param cid           client channel ID.
    * @param sid           server channel ID.
    * @param type          channel native type code.
    * @param count         channel element count.
    * @param minorRevision server minor CA revision.
    * @param serverAddress server address.
    */
   void searchResponse( int cid, int sid, short type, int count, short minorRevision, InetSocketAddress serverAddress )
   {
      final ChannelImpl<?> channel = getChannel( cid );
      if ( channel == null )
      {
         return;
      }

      logger.log ( Level.FINER, "Search response for channel " + channel.getName () + " received.");

      // check for multiple responses
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized( channel )
      {
         TCPTransport transport = channel.getTransport ();
         if ( transport != null )
         {
            // multiple defined PV or reconnect request (same server address)
            if ( !transport.getRemoteAddress ().equals( serverAddress) )
            {
               logger.log( Level.INFO,"More than one PVs with name '" + channel.getName () +
                     "' detected, additional response from: " + serverAddress);
               return;
            }
         }

         // do not search anymore (also unregisters)
         channelSearchManager.searchResponse (channel);

         transport = getTransport (channel, serverAddress, minorRevision, channel.getPriority ());
         if ( transport == null )
         {
            channel.createChannelFailed ();
            return;
         }

         // create channel
         channel.createChannel (transport, sid, type, count);
      }
   }

   void repeaterConfirm( InetSocketAddress responseFrom )
   {
      logger.fine( "Repeater registration confirmed from: " + responseFrom );

      final ScheduledFuture<?> sf = repeaterRegistrationFuture;
      if ( sf != null )
      {
         sf.cancel(false);
      }
   }

   boolean enqueueStatefullEvent( StatefullEventSource event )
   {
      if ( event.allowEnqueue () )
      {
         executorService.execute( event );
         return true;
      }
      else
      {
         return false;
      }
   }

   void beaconAnomalyNotify()
   {
      logger.fine( "A beacon anomaly has been detected." );
      if ( channelSearchManager != null )
      {
         channelSearchManager.beaconAnomalyNotify();
      }
   }

   /**
    * Get (and if necessary create) beacon handler.
    *
    * @param responseFrom remote source address of received beacon.
    * @return beacon handler for particular server.
    */
   BeaconHandler getBeaconHandler( InetSocketAddress responseFrom )
   {
      synchronized ( beaconHandlers )
      {
         BeaconHandler handler = beaconHandlers.get( responseFrom );
         if ( handler == null )
         {
            handler = new BeaconHandler( this, responseFrom );
            beaconHandlers.put( responseFrom, handler );
         }
         return handler;
      }
   }

   /**
    * Generate Client channel ID (CID).
    *
    * @return Client channel ID (CID).
    */
   int generateCID()
   {
      synchronized ( channelsByCID )
      {
         // search first free (theoretically possible loop of death)
         //noinspection StatementWithEmptyBody
         while ( channelsByCID.containsKey( ++lastCID ) )
         {
            // Intentionally left blank
         }

         // reserve CID
         channelsByCID.put( lastCID, null );
         return lastCID;
      }
   }

   /**
    * Register channel.
    *
    * @param channel the channel.
    */
   void registerChannel( ChannelImpl<?> channel )
   {
      synchronized ( channelsByCID )
      {
         channelsByCID.put (channel.getCID (), channel);
      }
   }

   /**
    * Unregister channel.
    *
    * @param channel the channel.
    */
   void unregisterChannel( ChannelImpl<?> channel )
   {
      synchronized ( channelsByCID )
      {
         channelsByCID.remove (channel.getCID ());
      }
   }

/*- Package-level getter methods ---------------------------------------------*/

   ChannelSearchManager getChannelSearchManager()
   {
      return channelSearchManager;
   }

   BroadcastTransport getBroadcastTransport()
   {
      return broadcastTransport.get ();
   }

   TransportRegistry getTransportRegistry()
   {
      return transportRegistry;
   }

   LeaderFollowersThreadPool getLeaderFollowersThreadPool()
   {
      return leaderFollowersThreadPool;
   }

   int getRepeaterPort()
   {
      return protocolConfiguration.getRepeaterPort();
   }

   int getServerPort()
   {
      return protocolConfiguration.getServerPort();
   }

   float getConnectionTimeout()
   {
      return protocolConfiguration.getConnectionTimeout();
   }

   int getMaxArrayBytes()
   {
      return protocolConfiguration.getMaxArrayBytes();
   }

   ScheduledExecutorService getScheduledExecutor()
   {
      return timer;
   }

   Reactor getReactor()
   {
      return reactor;
   }

   MonitorNotificationServiceFactory getMonitorNotificationServiceFactory()
   {
      return monitorNotificationServiceFactory;
   }

/*- Private methods ----------------------------------------------------------*/

   private BroadcastTransport initializeUDPTransport()
   {
      final String addressList = this.protocolConfiguration.getAddressList();
      final boolean autoAddressList = this.protocolConfiguration.getAutoAddressList();
      final int serverPort = this.protocolConfiguration.getServerPort();

      // set broadcast address list
      InetSocketAddress[] broadcastAddressList = null;
      if ( addressList != null && addressList.length() > 0 )
      {
         // if auto is true, add it to specified list
         InetSocketAddress[] appendList = null;
         if ( autoAddressList )
         {
            appendList = InetAddressUtil.getBroadcastAddresses(serverPort);
         }

         final InetSocketAddress[] list = InetAddressUtil.getSocketAddressList(addressList, serverPort, appendList);
         if ( list.length > 0 )
         {
            broadcastAddressList = list;
         }
      }
      else if ( !autoAddressList )
      {
         logger.log(Level.WARNING, "Empty broadcast search address list, all connects will fail.");
      }
      else
      {
         broadcastAddressList = InetAddressUtil.getBroadcastAddresses( serverPort );
      }

      if ( logger.isLoggable(Level.CONFIG) && broadcastAddressList != null )
      {
         for ( int i = 0; i < broadcastAddressList.length; i++ )
         {
            logger.log(Level.CONFIG, "Broadcast address #" + i + ": " + broadcastAddressList[ i ] + '.');
         }
      }

      // any address
      InetSocketAddress connectAddress = new InetSocketAddress (0);
      logger.finer( "Creating datagram socket to: " + connectAddress);

      DatagramChannel channel = null;
      try
      {
         channel = DatagramChannel.open ();

         // use non-blocking channel (no need for soTimeout)
         channel.configureBlocking (false);

         // set SO_BROADCAST
         channel.socket ().setBroadcast (true);

         // explicitly bind first
         channel.socket ().setReuseAddress (true);
         channel.socket ().bind (new InetSocketAddress (0));

         // create transport
         BroadcastTransport transport = new BroadcastTransport (this, ResponseHandlers::handleResponse, channel,
                                                                connectAddress, broadcastAddressList);

         // and register to the selector
         ReactorHandler handler = new LeaderFollowersHandler (reactor, transport, leaderFollowersThreadPool);
         reactor.register (channel, SelectionKey.OP_READ, handler);

         return transport;
      }
      catch ( Throwable th )
      {
         // close socket, if open
         try
         {
            if ( channel != null )
            {
               channel.close();
            }
         }
         catch ( Throwable t )
         { /* noop */ }

         throw new RuntimeException ("Failed to connect to '" + connectAddress + "'.", th);
      }
   }

   /**
    * Destroy all channels.
    */
   private void destroyAllChannels()
   {
      ChannelImpl<?>[] channels;
      synchronized ( channelsByCID )
      {
         channels = (ChannelImpl<?>[]) new ChannelImpl[ channelsByCID.size () ];
         channelsByCID.toArray (channels);
         channelsByCID.clear ();
      }

      for ( ChannelImpl<?> channel : channels )
      {
         try
         {
            if ( channel != null )
            {
               channel.close();
            }
         }
         catch ( Throwable th )
         {
            logger.log(Level.SEVERE, "Unexpected exception caught while closing a channel", th);
         }
      }
   }

   /**
    * Generate IOID.
    *
    * @return IOID.
    */
   private int generateIOID()
   {
      synchronized ( responseRequests )
      {
         // search first free (theoretically possible loop of death)
         //noinspection StatementWithEmptyBody
         while ( responseRequests.containsKey (++lastIOID) )
         {
            // Intentionally left blank
         }
         // reserve IOID
         responseRequests.put (lastIOID, null);
         return lastIOID;
      }
   }

   /**
    * Get, or create if necessary, transport of given server address.
    *
    * @param priority process priority.
    * @return transport for given address
    */
   private TCPTransport getTransport( TransportClient client, InetSocketAddress address, short minorRevision, int priority )
   {
      SocketChannel socket = null;

      // first try to check cache w/o named lock...
      TCPTransport transport = (TCPTransport) transportRegistry.get( address, priority );
      if ( transport != null )
      {
         logger.log ( Level.FINER,"Reusing existing connection to CA server: " + address);
         if ( transport.acquire (client) )
         {
            return transport;
         }
      }

      final boolean lockAcquired = namedLocker.acquireSynchronizationObject( address, LOCK_TIMEOUT );
      if ( lockAcquired )
      {
         try
         {
            // ... transport created during waiting in lock
            transport = (TCPTransport) transportRegistry.get (address, priority);
            if ( transport != null )
            {
               logger.log ( Level.FINER,"Reusing existing connection to CA server: " + address);
               if ( transport.acquire (client) )
               {
                  return transport;
               }
            }

            logger.log ( Level.FINER, "Connecting to CA server: " + address);

            socket = tryConnect( address, 3 );

            // use non-blocking channel (no need for soTimeout)
            socket.configureBlocking( false );

            // enable TCP_NODELAY (disable Nagle's algorithm)
            socket.socket().setTcpNoDelay( true );

            // enable TCP_KEEPALIVE
            socket.socket().setKeepAlive( true );

            // create transport
            transport = new TCPTransport( this, client, ResponseHandlers::handleResponse, socket, minorRevision, priority );

            ReactorHandler handler = transport;
            if ( leaderFollowersThreadPool != null )
            {
               handler = new LeaderFollowersHandler (reactor, handler, leaderFollowersThreadPool);
            }

            // register to reactor
            reactor.register( socket, SelectionKey.OP_READ, handler );

            // issue version including priority, username and local hostname
            Messages.versionMessage( transport, (short) priority, 0, false );
            Messages.userNameMessage( transport, userName );
            Messages.hostNameMessage( transport, hostName );
            transport.flush ();

            logger.log ( Level.FINER, "Connected to CA server: " + address);

            return transport;

         }
         catch ( Throwable th )
         {
            // close socket, if open
            try
            {
               if ( socket != null )
               {
                  socket.close();
               }
            }
            catch ( Throwable t )
            { /* noop */
            }

            logger.log( Level.WARNING, th, () -> "Failed to connect to '" + address + "'.");
            return null;
         }
         finally
         {
            namedLocker.releaseSynchronizationObject( address );
         }
      }
      else
      {
         logger.severe( () -> "Failed to obtain synchronization lock for '" + address + "', possible deadlock." );
         return null;
      }
   }

   /**
    * Tries to connect to the given address.
    *
    * @param address the address of the socket.
    * @param tries the attempt number.
    * @return the channel.
    * @throws IOException thrown on failure.
    */
   @SuppressWarnings( "SameParameterValue" )
   private SocketChannel tryConnect( InetSocketAddress address, int tries ) throws IOException
   {
      IOException lastException = null;

      for ( int tryCount = 0; tryCount < tries; tryCount++ )
      {
         // sleep for a while
         if ( tryCount > 0 )
         {
            try
            {
               Thread.sleep(100 );
            }
            catch ( InterruptedException ie )
            {
               logger.finest( "Interrupted Exception" );
            }
         }
         logger.finest( "Opening socket to CA server " + address + ", attempt " + (tryCount + 1) + "." );

         try
         {
            return SocketChannel.open( address );
         }
         catch ( IOException ioe )
         {
            lastException = ioe;
         }
      }

      throw Objects.requireNonNull( lastException );
   }

/*- Nested classes -----------------------------------------------------------*/

   /**
    * RepeaterRegistrationTask
    */
   private class RepeaterRegistrationTask implements Runnable
   {
      private final InetSocketAddress repeaterLocalAddress;
      private final ByteBuffer buffer = ByteBuffer.allocate (Constants.CA_MESSAGE_HEADER_SIZE);

      RepeaterRegistrationTask( InetSocketAddress repeaterLocalAddress )
      {
         this.repeaterLocalAddress = repeaterLocalAddress;

         Messages.generateRepeaterRegistration( buffer );
      }

      public void run()
      {
         try
         {
            logger.fine( "Attempting to register with repeater at address: '" + repeaterLocalAddress + "'." );
            getBroadcastTransport ().send( buffer, repeaterLocalAddress );
            logger.fine( "Repeater registration message sent ok." );
         }
         catch ( Throwable th )
         {
            logger.log( Level.FINE, th, () -> "Failed to send repeater registration message to: " + repeaterLocalAddress);
         }
      }
   }

}
