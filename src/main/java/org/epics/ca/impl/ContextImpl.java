package org.epics.ca.impl;

import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.epics.ca.Channel;
import org.epics.ca.Constants;
import org.epics.ca.Version;
import org.epics.ca.util.logging.ConsoleLogHandler;

public class ContextImpl implements AutoCloseable, Constants {

	static
	{
		// force only IPv4 sockets, since EPICS does not work with IPv6 sockets
		// see http://java.sun.com/j2se/1.5.0/docs/guide/net/properties.html
		System.setProperty("java.net.preferIPv4Stack", "true");
	}
	
	/**
	 * Context logger.
	 */
	protected Logger logger;
	
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
	 * Port number for the server to listen to.
	 */
	protected int serverPort = CA_SERVER_PORT;
	
	/**
	 * Length in bytes of the maximum array size that may pass through CA.
	 */
	protected int maxArrayBytes = 16384;

	public ContextImpl()
	{
		this(System.getProperties());
	}
	
	public ContextImpl(Properties properties)
	{
		initializeLogger(properties);
		loadConfig(properties);
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
				logger.config("Failed to parse boolean value for property " + key + ": \"" + sValue + "\", \"YES\" or \"NO\" expected.");
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
				logger.config("Failed to parse float value for property " + key + ": \"" + sValue + "\".");
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
				logger.config("Failed to parse integer value for property " + key + ": \"" + sValue + "\".");
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
		
		logger = Logger.getLogger(this.getClass().getName());
		
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

	public <T> Channel<T> createChannel(String channelName, Class<T> channelType)
	{
		return createChannel(channelName, channelType, CHANNEL_PRIORITY_DEFAULT);
	}

	public <T> Channel<T> createChannel(String channelName, Class<T> channelType, int priority)
	{
		// TODO priority
		return new ChannelImpl<T>(channelName, channelType);
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		
	}
	
}
