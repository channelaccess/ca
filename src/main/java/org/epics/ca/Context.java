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
	
	public <T> Channel<T> createChannel(String channelName, Class<T> channelType)
	{
		return createChannel(channelName, channelType, CHANNEL_PRIORITY_DEFAULT);
	}

	public <T> Channel<T> createChannel(String channelName, Class<T> channelType, short priority)
	{
		//return new ChannelImpl(channelName, channelType, priority);
		return null;
	}

	@Override
	public void close() throws Exception {
		// TODO Auto-generated method stub
		
	}
	
}
