package org.epics.ca;

public class Context implements AutoCloseable {
	
	// use Java logging API
	
	public Context()
	{
		loadConfig();
	}
	
	protected void loadConfig()
	{
		// TODO sys env. w/ explicit override
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
