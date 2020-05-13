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
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.epics.ca.Channel;
import org.epics.ca.Constants;
import org.epics.ca.Context;
import org.epics.ca.LibraryVersion;
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


/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class ContextImpl implements AutoCloseable, Constants
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   static
   {
      // force only IPv4 sockets, since EPICS does not work with IPv6 sockets
      System.setProperty ("java.net.preferIPv4Stack", "true");
   }

   /**
    * Context logger.
    */
   private static final Logger logger = LibraryLogManager.getLogger( ContextImpl.class );

   /**
    * Debug level, turns on low-level debugging.
    */
   protected int debugLevel = 0;

   /**
    * A space-separated list of broadcast address for process variable name resolution.
    * Each address must be of the form: ip.number:port or host.name:port
    */
   protected String addressList = "";

   /**
    * Define whether or not the network interfaces should be discovered at runtime.
    */
   protected boolean autoAddressList = true;

   /**
    * If the context doesn't see a beacon from a server that it is connected to for
    * connectionTimeout seconds then a state-of-health message is sent to the server over TCP/IP.
    * If this state-of-health message isn't promptly replied to then the context will assume that
    * the server is no longer present on the network and disconnect.
    */
   protected float connectionTimeout = 30.0f;

   /**
    * Period in second between two beacon signals.
    */
   protected float beaconPeriod = 15.0f;

   /**
    * Port number for the repeater to listen to.
    */
   protected int repeaterPort = CA_REPEATER_PORT;

   /**
    * Port number for the server to listen to.
    */
   protected int serverPort = CA_SERVER_PORT;

   /**
    * Length in bytes of the maximum array size that may pass through CA, defaults to 0 (&lt;=0 means unlimited).
    */
   protected int maxArrayBytes = 0; //16384;

   /**
    * Configuration for the monitor notifier.
    */
   protected String monitorNotifierConfigImpl = MonitorNotificationServiceFactoryCreator.DEFAULT_IMPL;

   /**
    * Timer.
    */
   protected final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor ();

   /**
    * General executor service (e.g. event dispatcher).
    */
   protected final ExecutorService executorService = Executors.newSingleThreadExecutor ();

   /**
    * Factory to be used for creating MonitorNotificationService instances.
    */
   private final MonitorNotificationServiceFactory monitorNotificationServiceFactory;

   /**
    * Repeater registration future.
    */
   protected volatile ScheduledFuture<?> repeaterRegistrationFuture;

   /**
    * Reactor.
    */
   protected final Reactor reactor;

   /**
    * Leader/followers thread pool.
    */
   protected final LeaderFollowersThreadPool leaderFollowersThreadPool;

   /**
    * Context instance.
    */
   private static final int LOCK_TIMEOUT = 20 * 1000;   // 20s

   /**
    * Context instance.
    */
   private final NamedLockPattern namedLocker = new NamedLockPattern ();

   /**
    * TCP transport registry.
    */
   private final TransportRegistry transportRegistry = new TransportRegistry ();

   /**
    * Channel search manager.
    * Manages UDP search requests.
    */
   private final ChannelSearchManager channelSearchManager;

   /**
    * Broadcast (search) transport.
    */
   private final AtomicReference<BroadcastTransport> broadcastTransport = new AtomicReference<> ();

   /**
    * Last CID cache.
    */
   private int lastCID = 0;

   /**
    * Map of channels (keys are CIDs).
    */
   protected final IntHashMap<ChannelImpl<?>> channelsByCID = new IntHashMap<>();

   /**
    * Last IOID cache.
    */
   private int lastIOID = 0;

   /**
    * Map of requests (keys are IOID).
    */
   protected final IntHashMap<ResponseRequest> responseRequests = new IntHashMap<>();

   /**
    * Cached hostname.
    */
   private final String hostName;

   /**
    * Cached username.
    */
   private final String userName;

   /**
    * Closed flag.
    */
   private final AtomicBoolean closed = new AtomicBoolean ();

   /**
    * Beacon handler map.
    */
   protected final Map<InetSocketAddress, BeaconHandler> beaconHandlers = new HashMap<> ();


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   public ContextImpl()
   {
      this (System.getProperties ());
   }

   public ContextImpl( Properties properties )
   {
      if ( properties == null )
      {
         throw new IllegalArgumentException( "null properties" );
      }

      initializeLogger( properties );
      loadConfig( properties );

      hostName = InetAddressUtil.getHostName ();
      userName = System.getProperty ("user.name", "nobody");

      // async IO reactor
      try
      {
         reactor = new Reactor ();
      }
      catch ( IOException e )
      {
         throw new RuntimeException ("Failed to initialize reactor.", e);
      }

      // leader/followers processing
      leaderFollowersThreadPool = new LeaderFollowersThreadPool ();

      // spawn initial leader
      leaderFollowersThreadPool.promoteLeader (reactor::process);

      broadcastTransport.set (initializeUDPTransport ());

      // Start task to register with CA Repeater
      final InetSocketAddress repeaterLocalAddress = new InetSocketAddress (InetAddress.getLoopbackAddress (), repeaterPort );
      repeaterRegistrationFuture = timer.scheduleWithFixedDelay ( new RepeaterRegistrationTask( repeaterLocalAddress ),
                                                                  0,
                                                                  CA_REPEATER_REGISTRATION_INTERVAL,
                                                                  TimeUnit.SECONDS );

      // Attempt to spawn the CA Repeater if not already running.
      try
      {
         CARepeaterStarter.startRepeaterIfNotAlreadyRunning( repeaterPort );
      }
      catch ( RuntimeException ex )
      {
         logger.log( Level.WARNING, "Failed to start CA Repeater on port " + repeaterPort, ex ) ;
      }

      channelSearchManager = new ChannelSearchManager( broadcastTransport.get() );
      monitorNotificationServiceFactory = MonitorNotificationServiceFactoryCreator.create( monitorNotifierConfigImpl );
   }


/*- Public methods -----------------------------------------------------------*/

   /**
    * Searches for a response request with given channel IOID.
    *
    * @param ioid I/O ID.
    * @return request response with given I/O ID.
    */
   public ResponseRequest getResponseRequest( int ioid )
   {
      synchronized ( responseRequests )
      {
         return responseRequests.get (ioid);
      }
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

   /**
    * Searches for a channel with given channel ID.
    *
    * @param channelID CID.
    * @return channel with given CID, <code>null</code> if non-existent.
    */
   public ChannelImpl<?> getChannel( int channelID )
   {
      synchronized ( channelsByCID )
      {
         return channelsByCID.get (channelID);
      }
   }

   public ChannelSearchManager getChannelSearchManager()
   {
      return channelSearchManager;
   }

   public BroadcastTransport getBroadcastTransport()
   {
      return broadcastTransport.get ();
   }

   public int getServerPort()
   {
      return serverPort;
   }

   public float getConnectionTimeout()
   {
      return connectionTimeout;
   }

   public int getMaxArrayBytes()
   {
      return maxArrayBytes;
   }

   public TransportRegistry getTransportRegistry()
   {
      return transportRegistry;
   }

   public LeaderFollowersThreadPool getLeaderFollowersThreadPool()
   {
      return leaderFollowersThreadPool;
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
   public void searchResponse( int cid, int sid, short type, int count, short minorRevision, InetSocketAddress serverAddress )
   {
      final ChannelImpl<?> channel = getChannel( cid );
      if ( channel == null )
      {
         return;
      }

      logger.log ( Level.FINER, "Search response for channel " + channel.getName () + " received.");

      // check for multiple responses
      synchronized( channel )
      {
         TCPTransport transport = channel.getTransport ();
         if ( transport != null )
         {
            // multiple defined PV or reconnect request (same server address)
            if ( !transport.getRemoteAddress ().equals (serverAddress) )
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

   public void repeaterConfirm( InetSocketAddress responseFrom )
   {
      logger.log( Level.FINE, "Repeater registration confirmed from: " + responseFrom );

      ScheduledFuture<?> sf = repeaterRegistrationFuture;
      if ( sf != null )
         sf.cancel (false);
   }

   public boolean enqueueStatefullEvent( StatefullEventSource event )
   {
      if ( event.allowEnqueue () )
      {
         executorService.execute (event);
         return true;
      }
      else
         return false;
   }

   public void beaconAnomalyNotify()
   {
      if ( channelSearchManager != null )
      {
         channelSearchManager.beaconAnomalyNotify ();
      }
   }

   /**
    * Get (and if necessary create) beacon handler.
    *
    * @param responseFrom remote source address of received beacon.
    * @return beacon handler for particular server.
    */
   public BeaconHandler getBeaconHandler( InetSocketAddress responseFrom )
   {
      synchronized ( beaconHandlers )
      {
         BeaconHandler handler = beaconHandlers.get (responseFrom);
         if ( handler == null )
         {
            handler = new BeaconHandler (this, responseFrom);
            beaconHandlers.put (responseFrom, handler);
         }
         return handler;
      }
   }

   public ScheduledExecutorService getScheduledExecutor()
   {
      return timer;
   }


   public <T> Channel<T> createChannel( String channelName, Class<T> channelType )
   {
      return createChannel (channelName, channelType, CHANNEL_PRIORITY_DEFAULT);
   }

   public <T> Channel<T> createChannel( String channelName, Class<T> channelType, int priority )
   {
      if ( closed.get () )
      {
         throw new RuntimeException("context closed");
      }

      if ( channelName == null || channelName.length () == 0 )
      {
         throw new IllegalArgumentException("null or empty channel name");
      }
      else if ( channelName.length () > Math.min (MAX_UDP_SEND - CA_MESSAGE_HEADER_SIZE, UNREASONABLE_CHANNEL_NAME_LENGTH) )
      {
         throw new IllegalArgumentException("name too long");
      }

      if ( channelType == null )
      {
         throw new IllegalArgumentException("null channel type");
      }

      if ( !TypeSupports.isNativeType (channelType) && !channelType.equals (Object.class) )
      {
         throw new IllegalArgumentException("invalid channel native type");
      }

      if ( priority < CHANNEL_PRIORITY_MIN || priority > CHANNEL_PRIORITY_MAX )
      {
         throw new IllegalArgumentException("priority out of bounds");
      }

      return new ChannelImpl<>(this, channelName, channelType, priority);
   }

   @Override
   public void close()
   {

      if ( closed.getAndSet (true) )
      {
         return;
      }

      channelSearchManager.cancel ();
      broadcastTransport.get ().close ();

      // this will also close all CA transports
      destroyAllChannels ();

      reactor.shutdown ();
      leaderFollowersThreadPool.shutdown ();
      timer.shutdown ();

      // Dispose of the monitor service factory and all services which it has created
      monitorNotificationServiceFactory.close();

      executorService.shutdown ();
      try
      {
         executorService.awaitTermination (3, TimeUnit.SECONDS);
      }
      catch ( InterruptedException e )
      {
         // noop
      }
      executorService.shutdownNow ();
   }

   public Reactor getReactor()
   {
      return reactor;
   }

/*- Protected methods --------------------------------------------------------*/

   protected MonitorNotificationServiceFactory getMonitorNotificationServiceFactory()
   {
      return monitorNotificationServiceFactory;
   }

   protected String readStringProperty( Properties properties, String key, String defaultValue )
   {
      String sValue = properties.getProperty (key, System.getenv (key));
      return (sValue != null) ? sValue : defaultValue;
   }

   protected boolean readBooleanProperty( Properties properties, String key, boolean defaultValue )
   {
      String sValue = properties.getProperty (key, System.getenv (key));
      if ( sValue != null )
      {
         if ( sValue.equalsIgnoreCase ("YES") )
         {
            return true;
         }
         else if ( sValue.equalsIgnoreCase ("NO") )
         {
            return false;
         }
         else
         {
            logger.log( Level.CONFIG, "Failed to parse boolean value for property " + key + ": \"" + sValue + "\", \"YES\" or \"NO\" expected.");
            return defaultValue;
         }
      }
      else
      {
         return defaultValue;
      }
   }

   protected float readFloatProperty( Properties properties, String key, float defaultValue )
   {
      String sValue = properties.getProperty (key, System.getenv (key));
      if ( sValue != null )
      {
         try
         {
            return Float.parseFloat (sValue);
         }
         catch ( Throwable th )
         {
            logger.log( Level.CONFIG, "Failed to parse float value for property " + key + ": \"" + sValue + "\".");
         }
      }
      return defaultValue;
   }

   protected int readIntegerProperty( Properties properties, String key, int defaultValue )
   {
      String sValue = properties.getProperty (key, System.getenv (key));
      if ( sValue != null )
      {
         try
         {
            return Integer.parseInt (sValue);
         }
         catch ( Throwable th )
         {
            logger.log( Level.CONFIG, "Failed to parse integer value for property " + key + ": \"" + sValue + "\".");
         }
      }
      return defaultValue;
   }

   protected void loadConfig( Properties properties )
   {
      // dump version
      logger.log( Level.CONFIG, "Java CA v" + LibraryVersion.getAsString() );

      addressList = readStringProperty (properties, Context.Configuration.EPICS_CA_ADDR_LIST.toString (), addressList);
      logger.log( Level.CONFIG, Context.Configuration.EPICS_CA_ADDR_LIST.toString () + ": " + addressList);

      autoAddressList = readBooleanProperty (properties, Context.Configuration.EPICS_CA_AUTO_ADDR_LIST.toString (), autoAddressList);
      logger.log( Level.CONFIG, Context.Configuration.EPICS_CA_AUTO_ADDR_LIST.toString () + ": " + autoAddressList);

      connectionTimeout = readFloatProperty (properties, Context.Configuration.EPICS_CA_CONN_TMO.toString (), connectionTimeout);
      connectionTimeout = Math.max (0.1f, connectionTimeout);
      logger.log( Level.CONFIG, Context.Configuration.EPICS_CA_CONN_TMO.toString () + ": " + connectionTimeout);

      beaconPeriod = readFloatProperty (properties, Context.Configuration.EPICS_CA_BEACON_PERIOD.toString (), beaconPeriod);
      beaconPeriod = Math.max (0.1f, beaconPeriod);
      logger.log( Level.CONFIG, Context.Configuration.EPICS_CA_BEACON_PERIOD.toString () + ": " + beaconPeriod);

      repeaterPort = readIntegerProperty (properties, Context.Configuration.EPICS_CA_REPEATER_PORT.toString (), repeaterPort);
      logger.log( Level.CONFIG, Context.Configuration.EPICS_CA_REPEATER_PORT.toString () + ": " + repeaterPort);

      serverPort = readIntegerProperty (properties, Context.Configuration.EPICS_CA_SERVER_PORT.toString (), serverPort);
      logger.log( Level.CONFIG, Context.Configuration.EPICS_CA_SERVER_PORT.toString () + ": " + serverPort);

      maxArrayBytes = readIntegerProperty (properties, Context.Configuration.EPICS_CA_MAX_ARRAY_BYTES.toString (), maxArrayBytes);
      if ( maxArrayBytes > 0 )
         maxArrayBytes = Math.max (1024, maxArrayBytes);
      logger.log( Level.CONFIG, Context.Configuration.EPICS_CA_MAX_ARRAY_BYTES.toString () + ": " + (maxArrayBytes > 0 ? maxArrayBytes : "(undefined)"));

      monitorNotifierConfigImpl = readStringProperty(properties, CA_MONITOR_NOTIFIER_IMPL, CA_MONITOR_NOTIFIER_DEFAULT_IMPL);
      logger.log( Level.CONFIG, "CA_MONITOR_NOTIFIER_IMPL: " + monitorNotifierConfigImpl);
   }

   /**
    * Initialize context logger.
    *
    * @param properties the properties to be used for the logger.
    */
   protected void initializeLogger( Properties properties )
   {
      debugLevel = readIntegerProperty( properties, CA_DEBUG, debugLevel );

      if ( debugLevel > 0 )
      {
         logger.setLevel( Level.ALL );
      }
   }

   protected BroadcastTransport initializeUDPTransport()
   {
      // set broadcast address list
      InetSocketAddress[] broadcastAddressList = null;
      if ( addressList != null && addressList.length () > 0 )
      {
         // if auto is true, add it to specified list
         InetSocketAddress[] appendList = null;
         if ( autoAddressList )
         {
            appendList = InetAddressUtil.getBroadcastAddresses( serverPort );
         }

         final InetSocketAddress[] list = InetAddressUtil.getSocketAddressList( addressList, serverPort, appendList );
         if ( list.length > 0 )
         {
            broadcastAddressList = list;
         }
      }
      else if ( !autoAddressList )
      {
         logger.log ( Level.WARNING, "Empty broadcast search address list, all connects will fail.");
      }
      else
      {
         broadcastAddressList = InetAddressUtil.getBroadcastAddresses (serverPort);
      }

      if ( logger.isLoggable (Level.CONFIG) && broadcastAddressList != null )
         for ( int i = 0; i < broadcastAddressList.length; i++ )
            logger.log( Level.CONFIG, "Broadcast address #" + i + ": " + broadcastAddressList[ i ] + '.');

      // any address
      InetSocketAddress connectAddress = new InetSocketAddress (0);
      logger.log( Level.FINER, "Creating datagram socket to: " + connectAddress);

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
               channel.close ();
         }
         catch ( Throwable t )
         { /* noop */ }

         throw new RuntimeException ("Failed to connect to '" + connectAddress + "'.", th);
      }
   }


/*- Package-level methods ----------------------------------------------------*/

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
         while ( channelsByCID.containsKey (++lastCID) )
         {
            // Intentionally left blank
         }

         // reserve CID
         channelsByCID.put (lastCID, null);
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

/*- Private methods ----------------------------------------------------------*/

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
               channel.close();
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
      TCPTransport transport = (TCPTransport) transportRegistry.get (address, priority);
      if ( transport != null )
      {
         logger.log ( Level.FINER,"Reusing existing connection to CA server: " + address);
         if ( transport.acquire (client) )
            return transport;
      }

      boolean lockAcquired = namedLocker.acquireSynchronizationObject (address, LOCK_TIMEOUT);
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

            socket = tryConnect (address, 3);

            // use non-blocking channel (no need for soTimeout)
            socket.configureBlocking (false);

            // enable TCP_NODELAY (disable Nagle's algorithm)
            socket.socket ().setTcpNoDelay (true);

            // enable TCP_KEEPALIVE
            socket.socket ().setKeepAlive (true);

            // create transport
            transport = new TCPTransport (this, client, ResponseHandlers::handleResponse, socket, minorRevision, priority );

            ReactorHandler handler = transport;
            if ( leaderFollowersThreadPool != null )
            {
               handler = new LeaderFollowersHandler (reactor, handler, leaderFollowersThreadPool);
            }

            // register to reactor
            reactor.register (socket, SelectionKey.OP_READ, handler);

            // issue version including priority, username and local hostname
            Messages.versionMessage (transport, (short) priority, 0, false);
            Messages.userNameMessage (transport, userName);
            Messages.hostNameMessage (transport, hostName);
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
                  socket.close ();
            }
            catch ( Throwable t )
            { /* noop */
            }

            logger.log (Level.WARNING, th, () -> "Failed to connect to '" + address + "'.");
            return null;
         }
         finally
         {
            namedLocker.releaseSynchronizationObject (address);
         }
      }
      else
      {
         logger.severe (() -> "Failed to obtain synchronization lock for '" + address + "', possible deadlock.");
         return null;
      }
   }

   /**
    * Tries to connect to the given adresss.
    *
    * @param address
    * @param tries
    * @return
    * @throws IOException
    */
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
               Thread.sleep (100);
            }
            catch ( InterruptedException ie )
            {
            }
         }
         logger.log( Level.FINEST,"Opening socket to CA server " + address + ", attempt " + (tryCount + 1) + "." );

         try
         {
            return SocketChannel.open (address);
         }
         catch ( IOException ioe )
         {
            lastException = ioe;
         }

      }

      throw lastException;
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
