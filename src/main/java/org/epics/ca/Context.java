package org.epics.ca;

import java.util.Properties;

public class Context implements AutoCloseable, Constants {
	
	// use Java logging API
	
	public Context()
	{
		this(System.getProperties());
	}
	
	public Context(Properties properties)
	{
		loadConfig(properties);
	}

	protected void loadConfig(Properties properties)
	{
		// TODO properties override system env. variables
		// e.g. String addressList = properties.getProperty(ADDR_LIST_KEY, System.getenv(ADDR_LIST_KEY));
	}
	
	<T> Channel<T> createChannel(String channelName)
	{
		return createChannel(channelName, CHANNEL_PRIORITY_DEFAULT);
	}

	<T> Channel<T> createChannel(String channelName, short priority)
	{
		//return new ChannelImpl<T>(channelName, priority);
		return null;
	}

	@Override
	public void close() throws Exception {
		// TODO Auto-generated method stub
		
	}
	
}
