package org.epics.ca.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.epics.ca.Channel;
import org.epics.ca.Constants;
import org.epics.ca.Version;
import org.epics.ca.impl.reactor.Reactor;
import org.epics.ca.impl.reactor.ReactorHandler;
import org.epics.ca.impl.reactor.lf.LeaderFollowersHandler;
import org.epics.ca.impl.reactor.lf.LeaderFollowersThreadPool;
import org.epics.ca.impl.search.ChannelSearchManager;
import org.epics.ca.util.IntHashMap;
import org.epics.ca.util.logging.ConsoleLogHandler;
import org.epics.ca.util.net.InetAddressUtil;
import org.epics.ca.util.sync.NamedLockPattern;

public class ContextImpl implements AutoCloseable, Constants {

	static
	{
		// force only IPv4 sockets, since EPICS does not work with IPv6 sockets
		System.setProperty("java.net.preferIPv4Stack", "true");
	}
	
	/**
	 * Context logger.
	 */
	protected final Logger logger = Logger.getLogger(getClass().getPackage().getName());
	
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
	 * Length in bytes of the maximum array size that may pass through CA.
	 */
	protected int maxArrayBytes = 16384;

	
	
	
	
	
	
	/**
	 * Timer.
	 */
	//protected final Timer timer;

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
	@SuppressWarnings("unused")
	private static final int LOCK_TIMEOUT = 20 * 1000;	// 20s

	/**
	 * Context instance.
	 */
	@SuppressWarnings("unused")
	private final NamedLockPattern namedLocker;

	/**
	 * Channel search manager.
	 * Manages UDP search requests.
	 */
	private final ChannelSearchManager channelSearchManager;
	
	/**
	 * Broadcast (search) transport.
	 */
	private final BroadcastTransport broadcastTransport;
	
	/**
	 * Last CID cache. 
	 */
	private int lastCID = 0;

	/**
	 * Map of channels (keys are CIDs).
	 */
	// TODO consider using IntHashMap
	protected IntHashMap<ChannelImpl<?>> channelsByCID = new IntHashMap<ChannelImpl<?>>();
	
	
	public ContextImpl()
	{
		this(System.getProperties());
	}
	
	public ContextImpl(Properties properties)
	{
		initializeLogger(properties);
		loadConfig(properties);

		// async IO reactor
		try {
			reactor = new Reactor();
		} catch (IOException e) {
			throw new RuntimeException("Failed to initialize reactor.", e);
		}
		
	    // leader/followers processing
	    leaderFollowersThreadPool = new LeaderFollowersThreadPool();
	    
		// spawn initial leader
		leaderFollowersThreadPool.promoteLeader(() -> reactor.process());

		namedLocker = new NamedLockPattern();

		broadcastTransport = initializeUDPTransport();
		channelSearchManager = new ChannelSearchManager(broadcastTransport);
	}
	
	protected String readStringProperty(Properties properties, String key, String defaultValue)
	{
		String sValue = properties.getProperty(key, System.getenv(key));
		return (sValue != null) ? sValue : defaultValue;
	}

	protected boolean readBooleanProperty(Properties properties, String key, boolean defaultValue)
	{
		String sValue = properties.getProperty(key, System.getenv(key));
		if (sValue != null) {
			if (sValue.equalsIgnoreCase("YES"))
				return true;
			else if (sValue.equalsIgnoreCase("NO"))
				return false;
			else
			{
				logger.config(() -> "Failed to parse boolean value for property " + key + ": \"" + sValue + "\", \"YES\" or \"NO\" expected.");
				return defaultValue;
			}
		}
		else
			return defaultValue;
	}

	protected float readFloatProperty(Properties properties, String key, float defaultValue)
	{
		String sValue = properties.getProperty(key, System.getenv(key));
		if (sValue != null) {
			try {
				return Float.parseFloat(sValue);
			} catch (Throwable th) {
				logger.config(() -> "Failed to parse float value for property " + key + ": \"" + sValue + "\".");
			}
		}
		return defaultValue;
	}

	protected int readIntegerProperty(Properties properties, String key, int defaultValue)
	{
		String sValue = properties.getProperty(key, System.getenv(key));
		if (sValue != null) {
			try {
				return Integer.parseInt(sValue);
			} catch (Throwable th) {
				logger.config(() -> "Failed to parse integer value for property " + key + ": \"" + sValue + "\".");
			}
		}
		return defaultValue;
	}
	
	protected void loadConfig(Properties properties)
	{
		// dump version
		logger.config(() -> "Java CA v" + Version.getVersionString());
		
		addressList = readStringProperty(properties, ADDR_LIST_KEY, addressList);
		logger.config(() -> ADDR_LIST_KEY + ": " + addressList);
		
		autoAddressList = readBooleanProperty(properties, AUTO_ADDR_LIST_KEY, autoAddressList);
		logger.config(() -> AUTO_ADDR_LIST_KEY + ": " + autoAddressList);

		connectionTimeout = readFloatProperty(properties, CONN_TMO_KEY, connectionTimeout);
		logger.config(() -> CONN_TMO_KEY + ": " + connectionTimeout);

		beaconPeriod = readFloatProperty(properties, BEACON_PERIOD_KEY, beaconPeriod);
		logger.config(() -> BEACON_PERIOD_KEY + ": " + beaconPeriod);

		repeaterPort = readIntegerProperty(properties, REPEATER_PORT_KEY, repeaterPort);
		logger.config(() -> REPEATER_PORT_KEY + ": " + repeaterPort);

		serverPort = readIntegerProperty(properties, SERVER_PORT_KEY, serverPort);
		logger.config(() -> SERVER_PORT_KEY + ": " + serverPort);

		maxArrayBytes = readIntegerProperty(properties, MAX_ARRAY_BYTES_KEY, maxArrayBytes);
		logger.config(() -> MAX_ARRAY_BYTES_KEY + ": " + maxArrayBytes);
	}
	
	/**
	 * Initialize context logger.
	 */
	protected void initializeLogger(Properties properties)
	{
		debugLevel = readIntegerProperty(properties, CA_DEBUG, debugLevel);
		
		if (debugLevel > 0)
		{
			logger.setLevel(Level.ALL);
			
			// install console logger only if there is no already installed
			Logger inspectedLogger = logger;
			boolean found = false;
			while (inspectedLogger != null)
			{
				for (Handler handler : inspectedLogger.getHandlers())
					if (handler instanceof ConsoleLogHandler)
					{
						found = true;
						break;
					}
				inspectedLogger = inspectedLogger.getParent();
			}
			
			if (!found)
				logger.addHandler(new ConsoleLogHandler());
		}
	}

	protected BroadcastTransport initializeUDPTransport()
	{
		// where to bind (listen) address
		InetSocketAddress connectAddress = new InetSocketAddress(repeaterPort);

		
		// set broadcast address list
		InetSocketAddress[] broadcastAddressList = null;
		if (addressList != null && addressList.length() > 0)
		{
			// if auto is true, add it to specified list
			InetSocketAddress[] appendList = null;
			if (autoAddressList == true)
				appendList = InetAddressUtil.getBroadcastAddresses(serverPort);
			
			InetSocketAddress[] list = InetAddressUtil.getSocketAddressList(addressList, serverPort, appendList);
			if (list != null && list.length > 0)
				broadcastAddressList = list;
		}
		else if (autoAddressList == false)
			logger.warning("Empty broadcast search address list, all connects will fail.");
		else
			broadcastAddressList = InetAddressUtil.getBroadcastAddresses(serverPort);

		if (logger.isLoggable(Level.CONFIG) && broadcastAddressList != null)
			for (int i = 0; i < broadcastAddressList.length; i++)
        		logger.config("Broadcast address #" + i + ": " + broadcastAddressList[i] + '.');

		logger.finer(() -> "Creating datagram socket to: " + connectAddress);
		
		DatagramChannel channel = null;
		try
		{        
			channel = DatagramChannel.open();

			// use non-blocking channel (no need for soTimeout)			
			channel.configureBlocking(false);
		
			// set SO_BROADCAST
			channel.socket().setBroadcast(true);
			
			// explicitly bind first
			channel.socket().setReuseAddress(true);
			channel.socket().bind(connectAddress /*new InetSocketAddress(0)*/);

			// create transport
			// TODO !!!
			ResponseHandler responseHandler = null;
			BroadcastTransport transport = new BroadcastTransport(this, responseHandler, channel,
					connectAddress, broadcastAddressList);
	
			// and register to the selector
			ReactorHandler handler = new LeaderFollowersHandler(reactor, transport, leaderFollowersThreadPool);
			reactor.register(channel, SelectionKey.OP_READ, handler);
			
			return transport;
		}
		catch (Throwable th)
		{
			// close socket, if open
			try
			{
				if (channel != null)
					channel.close();
			}
			catch (Throwable t) { /* noop */ }

			throw new RuntimeException("Failed to connect to '" + connectAddress + "'.", th);
		}
		
	}
	
	public <T> Channel<T> createChannel(String channelName, Class<T> channelType)
	{
		return createChannel(channelName, channelType, CHANNEL_PRIORITY_DEFAULT);
	}

	public <T> Channel<T> createChannel(String channelName, Class<T> channelType, int priority)
	{
		if (channelName == null || channelName.length() == 0)
			throw new IllegalArgumentException("null or empty channel name");
		else if (channelName.length() > Math.min(MAX_UDP_SEND - CA_MESSAGE_HEADER_SIZE, UNREASONABLE_CHANNEL_NAME_LENGTH))
			throw new IllegalArgumentException("name too long");
		
		if (!Util.isNativeType(channelType))
			throw new IllegalArgumentException("Invalid channel native type");
		
		if (priority < CHANNEL_PRIORITY_MIN || priority > CHANNEL_PRIORITY_MAX)
			throw new IllegalArgumentException("priority out of bounds");
		
		return new ChannelImpl<T>(this, channelName, channelType, priority);
	}

	@Override
	public void close() {
		channelSearchManager.cancel();
		broadcastTransport.close();
		
		// this will also close all CA transports
		//destroyAllChannels();
		
		reactor.shutdown();
	    leaderFollowersThreadPool.shutdown();
	}

	public Reactor getReactor() {
		return reactor;
	}
	

	/**
	 * Generate Client channel ID (CID).
	 * @return Client channel ID (CID). 
	 */
	int generateCID()
	{
		synchronized (channelsByCID)
		{
			// search first free (theoretically possible loop of death)
			while (channelsByCID.containsKey(++lastCID));
			// reserve CID
			channelsByCID.put(lastCID, null);
			return lastCID;
		}
	}
	
	
}
