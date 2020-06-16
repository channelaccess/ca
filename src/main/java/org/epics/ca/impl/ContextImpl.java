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
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.Validate;
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

import static org.epics.ca.Constants.*;


/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class ContextImpl implements AutoCloseable
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   // The CA protocol only works on IPV4 enabled network stacks.
   // The definition below ensures that the external users of the library do not
   // have to explicitly set these definitions when using the library.
   static {
      System.setProperty( "java.net.preferIPv4Stack", "true" );
      System.setProperty( "java.net.preferIPv6Stack", "false" );
   }

   private static final Logger logger = LibraryLogManager.getLogger( ContextImpl.class );

   /**
    * Whether opening a new context should attempt to spawn a CA Repeater.
    */
   private boolean caRepeaterStartOnContextCreate = false;

   /**
    *
    * Whether closing a context should attempt to shutdown any spawned CA Repeater.
    */
   private boolean caRepeaterShutdownOnContextClose = false;

   /**
    * Whether the console output from any spawned CA Repeater should be logged.
    */
   private boolean caRepeaterOutputCapture;

   /**
    * Default value of the log level for the CA Repeater.
    */
   private Level caRepeaterLogLevel;

   /**
    * A space-separated list of broadcast address for process variable name resolution.
    * Each address must be of the form: ip.number:port or host.name:port
    */
   private String addressList = "";

   /**
    * Define whether or not the network interfaces should be discovered at runtime.
    */
   private boolean autoAddressList = true;

   /**
    * If the context doesn't see a beacon from a server that it is connected to for
    * connectionTimeout seconds then a state-of-health message is sent to the server over TCP/IP.
    * If this state-of-health message isn't promptly replied to then the context will assume that
    * the server is no longer present on the network and disconnect.
    */
   private float connectionTimeout = 30.0f;

   /**
    * Period in second between two beacon signals.
    */
   private float beaconPeriod = 15.0f;

   /**
    * Port number for the repeater to listen to.
    */
   private int repeaterPort = CA_REPEATER_PORT;

   /**
    * Port number for the server to listen to.
    */
   private int serverPort = CA_SERVER_PORT;

   /**
    * Length in bytes of the maximum array size that may pass through CA, defaults to 0 (&lt;=0 means unlimited).
    */
   private int maxArrayBytes = 0; //16384;

   /**
    * Configuration for the monitor notifier.
    */
   private String monitorNotifierConfigImpl = MonitorNotificationServiceFactoryCreator.DEFAULT_IMPL;

   /**
    * Timer.
    */
   private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

   /**
    * General executor service (e.g. event dispatcher).
    */
   private final ExecutorService executorService = Executors.newSingleThreadExecutor();

   /**
    * Factory to be used for creating MonitorNotificationService instances.
    */
   private final MonitorNotificationServiceFactory monitorNotificationServiceFactory;

   /**
    * Repeater registration future.
    */
   private final ScheduledFuture<?> repeaterRegistrationFuture;

   /**
    * Reactor.
    */
   private final Reactor reactor;

   /**
    * Leader/followers thread pool.
    */
   private final LeaderFollowersThreadPool leaderFollowersThreadPool;

   /**
    * Context instance.
    */
   private static final int LOCK_TIMEOUT = 20 * 1000;   // 20s

   /**
    * Context instance.
    */
   private final NamedLockPattern namedLocker = new NamedLockPattern();

   /**
    * TCP transport registry.
    */
   private final TransportRegistry transportRegistry = new TransportRegistry();

   /**
    * Channel search manager.
    * Manages UDP search requests.
    */
   private final ChannelSearchManager channelSearchManager;

   /**
    * Broadcast (search) transport.
    */
   private final AtomicReference<BroadcastTransport> broadcastTransport = new AtomicReference<>();

   /**
    * Last CID cache.
    */
   private int lastCID = 0;

   /**
    * Map of channels (keys are CIDs).
    */
   private final IntHashMap<ChannelImpl<?>> channelsByCID = new IntHashMap<>();

   /**
    * Last IOID cache.
    */
   private int lastIOID = 0;

   /**
    * Map of requests (keys are IOID).
    */
   private final IntHashMap<ResponseRequest> responseRequests = new IntHashMap<>();

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
   private final AtomicBoolean closed = new AtomicBoolean();

   /**
    * Beacon handler map.
    */
   private final Map<InetSocketAddress, BeaconHandler> beaconHandlers = new HashMap<>();


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Create a new context which will be initialised using the information in
    * the system properties object.
    */
   public ContextImpl()
   {
      this ( System.getProperties() );
   }

   /**
    * Create a new context based on the supplied properties object.
    *
    * @param properties the properties specifying configuration information for
    *    the new context.
    *
    * @throws NullPointerException if the supplied properties was null.
    */
   public ContextImpl( Properties properties )
   {
      Validate.notNull( properties, "null properties" );

      loadChannelAccessProtocolConfigurationProperties(properties );
      loadOtherConfigurationProperties( properties );

      hostName = InetAddressUtil.getHostName ();
      userName = System.getProperty ("user.name", "nobody");

      // async IO reactor
      try
      {
         reactor = new Reactor();
      }
      catch ( IOException e )
      {
         throw new RuntimeException( "Failed to initialize reactor.", e);
      }

      // leader/followers processing
      leaderFollowersThreadPool = new LeaderFollowersThreadPool();

      // spawn initial leader
      leaderFollowersThreadPool.promoteLeader( reactor::process );

      broadcastTransport.set( initializeUDPTransport() );

      // Attempt to spawn the CA Repeater if the relevant configuration property is set and it is not already running.
      if( caRepeaterStartOnContextCreate )
      {
         try
         {
             CARepeaterStarter.startRepeaterIfNotAlreadyRunning( repeaterPort, caRepeaterLogLevel, caRepeaterOutputCapture );
         }
         catch ( RuntimeException ex )
         {
            logger.log( Level.WARNING, "Failed to start CA Repeater on port " + repeaterPort, ex);
         }
      }

      // Always start task to register with CA Repeater (even if not started by this library).
      final InetSocketAddress repeaterLocalAddress = new InetSocketAddress (InetAddress.getLoopbackAddress (), repeaterPort );
      repeaterRegistrationFuture = timer.scheduleWithFixedDelay( new RepeaterRegistrationTask( repeaterLocalAddress ),
                                                                 CA_REPEATER_INITIAL_REGISTRATION_DELAY,
                                                                 CA_REPEATER_REGISTRATION_INTERVAL,
                                                                 TimeUnit.MILLISECONDS );

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

   public void repeaterConfirm( InetSocketAddress responseFrom )
   {
      logger.fine( "Repeater registration confirmed from: " + responseFrom );

      final ScheduledFuture<?> sf = repeaterRegistrationFuture;
      if ( sf != null )
      {
         sf.cancel(false);
      }
   }

   public boolean enqueueStatefullEvent( StatefullEventSource event )
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

   public void beaconAnomalyNotify()
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
   public BeaconHandler getBeaconHandler( InetSocketAddress responseFrom )
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

   public ScheduledExecutorService getScheduledExecutor()
   {
      return timer;
   }

   public <T> Channel<T> createChannel( String channelName, Class<T> channelType )
   {
      return createChannel( channelName, channelType, CHANNEL_PRIORITY_DEFAULT );
   }

   public <T> Channel<T> createChannel( String channelName, Class<T> channelType, int priority )
   {
      Validate.validState( ! closed.get(), "context closed" );
      Validate.notEmpty( channelName, "null or empty channel name" );
      Validate.isTrue(channelName.length() <= Math.min( MAX_UDP_SEND - CA_MESSAGE_HEADER_SIZE, UNREASONABLE_CHANNEL_NAME_LENGTH ), "name too long" );
      Validate.notNull( channelType, "null channel type" );
      Validate.isTrue( TypeSupports.isNativeType( channelType ) || channelType.equals (Object.class), "invalid channel native type" );
      Validate.inclusiveBetween( CHANNEL_PRIORITY_MIN, CHANNEL_PRIORITY_MAX, priority,"priority out of bounds" );

      return new ChannelImpl<>( this, channelName, channelType, priority );
   }

   @Override
   public void close()
   {
      logger.finest( "Closing context." );
      if ( closed.getAndSet (true) )
      {
         return;
      }

      // Shutdown repeater if the options is set.
      if ( caRepeaterShutdownOnContextClose )
      {
         CARepeaterStarter.shutdownLastStartedRepeater();
      }

      channelSearchManager.cancel();
      broadcastTransport.get().close ();

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

   public Reactor getReactor()
   {
      return reactor;
   }

/*- Package-level methods ----------------------------------------------------*/

   MonitorNotificationServiceFactory getMonitorNotificationServiceFactory()
   {
      return monitorNotificationServiceFactory;
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
    * Returns the String value of the specified configuration item which may be
    * provided either by means of a Java  property or from a variable set in
    * the operating system environment.
    *
    * If no value is explicitly defined the specified default value is returned.
    *
    * @param item the name of the property (or environment variable).
    * @param properties the properties object to search for configuration data.
    * @param defaultValue the value to return when not explicitly specified.
    * @return the result.
    * @throws NullPointerException if any of the arguments were null.
    */
   private String readStringProperty( String item, Properties properties, String defaultValue )
   {
      Validate.notNull( item );
      Validate.notNull( properties );
      Validate.notNull( defaultValue );

      final String sValue = properties.getProperty( item, System.getenv( item ) );
      return (sValue != null) ? sValue : defaultValue;
   }

   /**
    * Returns the boolean value of the specified configuration item which may be
    * provided either by means of a Java property or from a variable set in
    * the operating system environment.
    *
    * When a boolean value is specified any of the following string values may
    * be used: "yes" / "true" / "no" / "false".
    *
    * If no value is explicitly defined the specified default value is returned.
    *
    * @param item the name of the property (or environment variable).
    * @param properties the properties object to search for configuration data.
    * @param defaultValue the value to return when not explicitly specified.
    * @return the result.
    * @throws NullPointerException if any of the arguments were null.
    */
   private boolean readBooleanProperty( String item, Properties properties, boolean defaultValue )
   {
      Validate.notNull( item );
      Validate.notNull( properties );

      final String sValue = properties.getProperty( item, System.getenv( item ) );
      if ( sValue != null )
      {
         if ( ( sValue.equalsIgnoreCase( "yes" ) ) || ( sValue.equalsIgnoreCase( "true" ) ) )
         {
            return true;
         }
         else if ( ( sValue.equalsIgnoreCase( "no" ) ) || ( sValue.equalsIgnoreCase( "false" ) ) )
         {
            return false;
         }
         else
         {
            logger.config( "Failed to parse boolean value for property " + item + ": \"" + sValue + "\", \"YES\" or \"NO\" expected.");
            return defaultValue;
         }
      }
      else
      {
         return defaultValue;
      }
   }

   /**
    * Returns the float value of the specified configuration item which may be
    * provided either by means of a Java  property or from a variable set in
    * the operating system environment.
    *
    * If no value is explicitly defined the specified default value is returned.
    *
    * @param item the name of the property (or environment variable).
    * @param properties the properties object to search for configuration data.
    * @param defaultValue the value to return when not explicitly specified.
    * @return the result.
    * @throws NullPointerException if any of the arguments were null.
    */
   private float readFloatProperty( String item, Properties properties,  float defaultValue )
   {
      Validate.notNull( item );
      Validate.notNull( properties );

      final String sValue = properties.getProperty( item, System.getenv( item ));
      if ( sValue != null )
      {
         try
         {
            return Float.parseFloat( sValue );
         }
         catch ( Throwable th )
         {
            logger.config( "Failed to parse float value for property " + item + ": \"" + sValue + "\"." );
         }
      }
      return defaultValue;
   }

   /**
    * Returns the integer value of the specified configuration item which may be
    * provided either by means of a Java  property or from a variable set in
    * the operating system environment.
    *
    * If no value is explicitly defined the specified default value is returned.
    *
    * @param item the name of the property (or environment variable).
    * @param properties the properties object to search for configuration data.
    * @param defaultValue the value to return when not explicitly specified.
    * @return the result.
    * @throws NullPointerException if any of the arguments were null.
    */
   private int readIntegerProperty( String item, Properties properties, int defaultValue )
   {
      Validate.notNull( item );
      Validate.notNull( properties );

      final String sValue = properties.getProperty( item, System.getenv( item ));
      if ( sValue != null )
      {
         try
         {
            return Integer.parseInt( sValue );
         }
         catch ( Throwable th )
         {
            logger.config( "Failed to parse integer value for property " + item + ": \"" + sValue + "\"." );
         }
      }
      return defaultValue;
   }

   /**
    * Returns the debug level of the specified configuration item which may be
    * provided either by means of a Java  property or from a variable set in
    * the operating system environment.
    *
    * If no value is explicitly defined the specified default value is returned.
    *
    * @param item the name of the property (or environment variable).
    * @param properties the properties object to search for configuration data.
    * @param defaultValue the value to return when not explicitly specified.
    * @return the result.
    * @throws NullPointerException if any of the arguments were null.
    */
   @SuppressWarnings( "SameParameterValue" )
   private Level readDebugLevelProperty( String item, Properties properties, String defaultValue )
   {
      Validate.notNull( item );
      Validate.notNull( properties );
      Validate.notNull( defaultValue );

      final String sValue = readStringProperty( item, properties, defaultValue );
      try
      {
         return Level.parse( sValue );
      }
      catch ( Throwable th )
      {
         logger.config( "Failed to parse debug level value for property " + item + ": \"" + sValue + "\"." );
      }
      return Level.parse( defaultValue );
   }

   /**
    * Configures the current context according to the standard EPICS CA protocol
    * variables, which may be supplied either as Java system propertires or
    * through environment variables.
    *
    * @param properties the properties object to search for configuration data.
    */
   private void loadChannelAccessProtocolConfigurationProperties( Properties properties )
   {
      // dump version
      logger.config( "Java CA v" + LibraryVersion.getAsString() );

      addressList = readStringProperty(Context.Configuration.EPICS_CA_ADDR_LIST.toString(),  properties, addressList );
      logger.config( Context.Configuration.EPICS_CA_ADDR_LIST.toString() + ": " + addressList );

      autoAddressList = readBooleanProperty(Context.Configuration.EPICS_CA_AUTO_ADDR_LIST.toString(),  properties, autoAddressList );
      logger.config( Context.Configuration.EPICS_CA_AUTO_ADDR_LIST.toString() + ": " + autoAddressList);

      connectionTimeout = readFloatProperty(Context.Configuration.EPICS_CA_CONN_TMO.toString(),  properties, connectionTimeout );
      connectionTimeout = Math.max (0.1f, connectionTimeout);
      logger.config( Context.Configuration.EPICS_CA_CONN_TMO.toString() + ": " + connectionTimeout);

      beaconPeriod = readFloatProperty( Context.Configuration.EPICS_CA_BEACON_PERIOD.toString(),  properties, beaconPeriod );
      beaconPeriod = Math.max (0.1f, beaconPeriod);
      logger.config( Context.Configuration.EPICS_CA_BEACON_PERIOD.toString() + ": " + beaconPeriod);

      repeaterPort = readIntegerProperty( Context.Configuration.EPICS_CA_REPEATER_PORT.toString(), properties, repeaterPort );
      logger.config( Context.Configuration.EPICS_CA_REPEATER_PORT.toString() + ": " + repeaterPort);

      serverPort = readIntegerProperty( Context.Configuration.EPICS_CA_SERVER_PORT.toString(), properties, serverPort);
      logger.config( Context.Configuration.EPICS_CA_SERVER_PORT.toString() + ": " + serverPort);

      maxArrayBytes = readIntegerProperty( Context.Configuration.EPICS_CA_MAX_ARRAY_BYTES.toString(), properties,maxArrayBytes );
      if ( maxArrayBytes > 0 )
      {
         maxArrayBytes = Math.max( 1024, maxArrayBytes );
      }
      logger.config( Context.Configuration.EPICS_CA_MAX_ARRAY_BYTES.toString() + ": " + (maxArrayBytes > 0 ? maxArrayBytes : "(undefined)"));
   }

   /**
    * Configures the current context according to any other parameters which may
    * be supplied either as Java system properties or through environment variables.
    *
    * @param properties the properties object to search for configuration data.
    */
   private void loadOtherConfigurationProperties( Properties properties )
   {
      monitorNotifierConfigImpl = readStringProperty( CA_MONITOR_NOTIFIER_IMPL, properties, CA_MONITOR_NOTIFIER_DEFAULT_IMPL );
      logger.config(CA_MONITOR_NOTIFIER_IMPL + ": " + monitorNotifierConfigImpl);

      caRepeaterStartOnContextCreate = readBooleanProperty(CA_REPEATER_START_ON_CONTEXT_CREATE, properties, CA_REPEATER_START_ON_CONTEXT_CREATE_DEFAULT );
      logger.config(CA_REPEATER_START_ON_CONTEXT_CREATE + ": " + caRepeaterStartOnContextCreate );

      caRepeaterShutdownOnContextClose = readBooleanProperty( CA_REPEATER_SHUTDOWN_ON_CONTEXT_CLOSE, properties, CA_REPEATER_SHUTDOWN_ON_CONTEXT_CLOSE_DEFAULT );
      logger.config(CA_REPEATER_SHUTDOWN_ON_CONTEXT_CLOSE + ": " + caRepeaterShutdownOnContextClose );

      caRepeaterOutputCapture = this.readBooleanProperty( CA_REPEATER_OUTPUT_CAPTURE, properties, CA_REPEATER_OUTPUT_CAPTURE_DEFAULT );
      logger.config(CA_REPEATER_OUTPUT_CAPTURE + ": " + caRepeaterOutputCapture );

      caRepeaterLogLevel = this.readDebugLevelProperty( CA_REPEATER_LOG_LEVEL, properties, CA_REPEATER_LOG_LEVEL_DEFAULT );
      logger.config(CA_REPEATER_SHUTDOWN_ON_CONTEXT_CLOSE + ": " + caRepeaterLogLevel );

   }

   private BroadcastTransport initializeUDPTransport()
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
               channel.close ();
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
            return transport;
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
