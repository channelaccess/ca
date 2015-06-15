package org.epics.ca.impl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
import org.epics.ca.impl.repeater.CARepeater;
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
	protected final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

	/**
	 * General executor service (e.g. event dispatcher).
	 */
	protected final ExecutorService executorService = Executors.newSingleThreadExecutor();

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
	private static final int LOCK_TIMEOUT = 20 * 1000;	// 20s

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
	protected final IntHashMap<ChannelImpl<?>> channelsByCID = new IntHashMap<ChannelImpl<?>>();
	
	/**
	 * Last IOID cache. 
	 */
	private int lastIOID = 0;

	/**
	 * Map of requests (keys are IOID).
	 */
	protected final IntHashMap<ResponseRequest> responseRequests = new IntHashMap<ResponseRequest>();

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

	public ContextImpl()
	{
		this(System.getProperties());
	}
	
	public ContextImpl(Properties properties)
	{
		if (properties == null)
			throw new IllegalArgumentException("null properties");
		
		initializeLogger(properties);
		loadConfig(properties);
		
		hostName = InetAddressUtil.getHostName();
		userName = System.getProperty("user.name", "nobody");

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

		broadcastTransport.set(initializeUDPTransport());

		// spawn repeater registration task
		InetSocketAddress repeaterLocalAddress =
				new InetSocketAddress(InetAddress.getLoopbackAddress(), repeaterPort);
		repeaterRegistrationFuture = timer.scheduleWithFixedDelay(
				new RepeaterRegistrationTask(repeaterLocalAddress), 
				0,
				60,
				TimeUnit.SECONDS);
		
		try {
			CARepeater.startRepeater(repeaterPort);
		} catch (Throwable th) { /* noop */ }

		channelSearchManager = new ChannelSearchManager(broadcastTransport.get());
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

	private class RepeaterRegistrationTask implements Runnable {
		
		private final InetSocketAddress repeaterLocalAddress;
		private final ByteBuffer buffer = ByteBuffer.allocate(Constants.CA_MESSAGE_HEADER_SIZE);
		
		RepeaterRegistrationTask(InetSocketAddress repeaterLocalAddress)
		{
			this.repeaterLocalAddress = repeaterLocalAddress;

			Messages.generateRepeaterRegistration(buffer);
		}
		
		public void run() {
			try {
				getBroadcastTransport().send(buffer, repeaterLocalAddress);
			}
			catch (Throwable th) {
				logger.log(Level.FINE, th, () -> "Failed to send repeater registration message to: " + repeaterLocalAddress);
			}
		}
	}
	
	protected BroadcastTransport initializeUDPTransport()
	{
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

		// any address
		InetSocketAddress connectAddress = new InetSocketAddress(0);
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
			channel.socket().bind(new InetSocketAddress(0));

			// create transport
			BroadcastTransport transport = new BroadcastTransport(this, ResponseHandlers::handleResponse, channel,
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
		if (closed.get())
			throw new RuntimeException("context closed");
			
		if (channelName == null || channelName.length() == 0)
			throw new IllegalArgumentException("null or empty channel name");
		else if (channelName.length() > Math.min(MAX_UDP_SEND - CA_MESSAGE_HEADER_SIZE, UNREASONABLE_CHANNEL_NAME_LENGTH))
			throw new IllegalArgumentException("name too long");
		
		if (!TypeSupports.isNativeType(channelType) && !channelType.equals(Object.class))
			throw new IllegalArgumentException("Invalid channel native type");
		
		if (priority < CHANNEL_PRIORITY_MIN || priority > CHANNEL_PRIORITY_MAX)
			throw new IllegalArgumentException("priority out of bounds");
		
		return new ChannelImpl<T>(this, channelName, channelType, priority);
	}

	@Override
	public void close() {
		
		if (closed.getAndSet(true))
			throw new RuntimeException("context already closed");
		
		channelSearchManager.cancel();
		broadcastTransport.get().close();
		
		// this will also close all CA transports
		destroyAllChannels();
	
		reactor.shutdown();
	    leaderFollowersThreadPool.shutdown();
		timer.shutdown();
		
		executorService.shutdown();
		try {
			executorService.awaitTermination(3, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			// noop
		}
		executorService.shutdownNow();
		
	}
	
	/**
	 * Destroy all channels.
	 */
	private void destroyAllChannels() {
		
		ChannelImpl<?>[] channels;
		synchronized (channelsByCID)
		{
			channels = (ChannelImpl<?>[])new ChannelImpl[channelsByCID.size()];
			channelsByCID.toArray(channels);
			channelsByCID.clear();
		}
		
		for (int i = 0; i < channels.length; i++)
		{
			try
			{
				if (channels[i] != null)
					channels[i].close();
			}
			catch (Throwable th)
			{
				logger.log(Level.SEVERE, "Unexpected exception caught while closing a channel", th);
			}
		}
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
	
	/**
	 * Register channel.
	 * @param channel
	 */
	void registerChannel(ChannelImpl<?> channel)
	{
		synchronized (channelsByCID)
		{
			channelsByCID.put(channel.getCID(), channel);
		}
	}

	/**
	 * Unregister channel.
	 * @param channel
	 */
	void unregisterChannel(ChannelImpl<?> channel)
	{
		synchronized (channelsByCID)
		{
			channelsByCID.remove(channel.getCID());
		}
	}
	
	/**
	 * Searches for a response request with given channel IOID.
	 * @param ioid	I/O ID.
	 * @return request response with given I/O ID.
	 */
	public ResponseRequest getResponseRequest(int ioid)
	{
		synchronized (responseRequests)
		{
			return responseRequests.get(ioid);
		}
	}

	/**
	 * Register response request.
	 * @param request request to register.
	 * @return request ID (IOID).
	 */
	public int registerResponseRequest(ResponseRequest request)
	{
		synchronized (responseRequests)
		{
			int ioid = generateIOID();
			responseRequests.put(ioid, request);
			return ioid;
		}
	}

	/**
	 * Unregister response request.
	 * @param request
	 * @return removed object, can be <code>null</code>
	 */
	public ResponseRequest unregisterResponseRequest(ResponseRequest request)
	{
		synchronized (responseRequests)
		{
			return responseRequests.remove(request.getIOID());
		}
	}

	/**
	 * Generate IOID.
	 * @return IOID. 
	 */
	private int generateIOID()
	{
		synchronized (responseRequests)
		{
			// search first free (theoretically possible loop of death)
			while (responseRequests.containsKey(++lastIOID));
			// reserve IOID
			responseRequests.put(lastIOID, null);
			return lastIOID;
		}
	}
	
	/**
	 * Searches for a channel with given channel ID.
	 * @param channelID CID.
	 * @return channel with given CID, <code>null</code> if non-existent.
	 */
	public ChannelImpl<?> getChannel(int channelID)
	{
		synchronized (channelsByCID)
		{
			return channelsByCID.get(channelID);
		}
	}

	public ChannelSearchManager getChannelSearchManager() {
		return channelSearchManager;
	}

	public BroadcastTransport getBroadcastTransport() {
		return broadcastTransport.get();
	}

	public int getServerPort() {
		return serverPort;
	}
	
	public float getConnectionTimeout() {
		return connectionTimeout;
	}

	public int getMaxArrayBytes() {
		return maxArrayBytes;
	}

	public TransportRegistry getTransportRegistry() {
		return transportRegistry;
	}

	public LeaderFollowersThreadPool getLeaderFollowersThreadPool() {
		return leaderFollowersThreadPool;
	}

	/**
	 * Search response from server (channel found).
	 * @param cid	client channel ID.
	 * @param sid	server channel ID.
	 * @param type	channel native type code.
	 * @param count	channel element count.
	 * @param minorRevision	server minor CA revision.
	 * @param serverAddress	server address.
	 */
	public void searchResponse(int cid, int sid, short type, int count,
							   short minorRevision, InetSocketAddress serverAddress)
	{
		ChannelImpl<?> channel = getChannel(cid);
		if (channel == null)
			return;

		logger.finer(() -> "Search response for channel " + channel.getName() + " received.");

		// check for multiple responses
		synchronized (channel)
		{
			TCPTransport transport = channel.getTransport();
			if (transport != null)
			{
				// multiple defined PV or reconnect request (same server address)
				if (!transport.getRemoteAddress().equals(serverAddress))
				{
					logger.info(() -> "More than one PVs with name '" + channel.getName() +
								 "' detected, additional response from: " + serverAddress);
					return;
				}
			}
			
			// do not search anymore (also unregisters)
			channelSearchManager.searchResponse(channel);
			
			transport = getTransport(channel, serverAddress, minorRevision, channel.getPriority());
			if (transport == null)
			{
				channel.createChannelFailed();
				return;
			}

			// create channel
			channel.createChannel(transport, sid, type, count);
		}
		
	}
	
	/**
	 * Get, or create if necessary, transport of given server address.
	 * @param serverAddress	required transport address
	 * @param priority process priority.
	 * @return transport for given address
	 */
	private TCPTransport getTransport(TransportClient client, InetSocketAddress address, short minorRevision, int priority)
	{		
		SocketChannel socket = null;

		// first try to check cache w/o named lock...
		TCPTransport transport = (TCPTransport)transportRegistry.get(address, priority);
		if (transport != null) {
			logger.finer(() -> "Reusing existant connection to CA server: " + address);
			if (transport.acquire(client))
				return transport;
		}

		boolean lockAcquired = namedLocker.acquireSynchronizationObject(address, LOCK_TIMEOUT);
		if (lockAcquired) {
			try {
				// ... transport created during waiting in lock
				transport = (TCPTransport)transportRegistry.get(address, priority);
				if (transport != null) {
					logger.finer(() -> "Reusing existant connection to CA server: " + address);
					if (transport.acquire(client))
						return transport;
				}

				logger.finer(() -> "Connecting to CA server: " + address);

				socket = tryConnect(address, 3);

				// use non-blocking channel (no need for soTimeout)
				socket.configureBlocking(false);

				// enable TCP_NODELAY (disable Nagle's algorithm)
				socket.socket().setTcpNoDelay(true);

				// enable TCP_KEEPALIVE
				socket.socket().setKeepAlive(true);

				// create transport
				transport = new TCPTransport(this, client, ResponseHandlers::handleResponse,
						socket, minorRevision, priority);
				
				ReactorHandler handler = transport;
				if (leaderFollowersThreadPool != null)
					handler = new LeaderFollowersHandler(reactor, handler, leaderFollowersThreadPool);

				// register to reactor
				reactor.register(socket, SelectionKey.OP_READ, handler);

				// issue version including priority, username and local hostname
				Messages.versionMessage(transport, (short)priority, 0, false);
				Messages.userNameMessage(transport, userName);
				Messages.hostNameMessage(transport, hostName);
				transport.flush();

				logger.finer(() -> "Connected to CA server: " + address);

				return transport;

			} catch (Throwable th) {
				// close socket, if open
				try {
					if (socket != null)
						socket.close();
				} catch (Throwable t) { /* noop */
				}

				logger.log(Level.WARNING, th, () -> "Failed to connect to '" + address + "'.");
				return null;
			} finally {
				namedLocker.releaseSynchronizationObject(address);
			}
		} else {
			logger.severe(() -> "Failed to obtain synchronization lock for '" + address + "', possible deadlock.");
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
	private SocketChannel tryConnect(InetSocketAddress address, int tries)
			throws IOException {

		IOException lastException = null;

		for (int tryCount = 0; tryCount < tries; tryCount++) {

			// sleep for a while
			if (tryCount > 0) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException ie) {
				}
			}

			if (logger.isLoggable(Level.FINEST))
				logger.finest("Openning socket to CA server " + address
						+ ", attempt " + (tryCount + 1) + ".");

			try {
				return SocketChannel.open(address);
			} catch (IOException ioe) {
				lastException = ioe;
			}

		}

		throw lastException;
	}
	
	public void repeaterConfirm(InetSocketAddress responseFrom)
	{
		logger.fine("Repeater registration confirmed from: " + responseFrom);

		ScheduledFuture<?> sf = repeaterRegistrationFuture;
		if (sf != null)
			sf.cancel(false);
	}
	
	public boolean enqueueStatefullEvent(StatefullEventSource event)
	{
		if (event.allowEnqueue())
		{
			executorService.execute(event);
			return true;
		}
		else
			return false;
	}
	
}
