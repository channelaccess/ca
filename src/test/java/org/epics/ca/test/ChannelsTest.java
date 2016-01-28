package org.epics.ca.test;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

import org.epics.ca.Channel;
import org.epics.ca.Channels;
import org.epics.ca.Context;

public class ChannelsTest extends TestCase {

	static final double DELTA = 1e-10; 

	private Context context;
	private CAJTestServer server;
	
	@Override
	protected void setUp() throws Exception {
		server = new CAJTestServer();
		server.runInSeparateThread();
		context = new Context();
	}

	@Override
	protected void tearDown() throws Exception {
		context.close();
		server.destroy();
	}
	
	public void testWait()
	{
		try (Channel<Integer> channel1 = context.createChannel("simple", Integer.class))
		{
			channel1.connect();
			channel1.put(0);
			
			try (Channel<Integer> channel = context.createChannel("simple", Integer.class))
			{
				channel.connect();
				
				Executors.newSingleThreadScheduledExecutor().schedule(()->{channel1.put(12);System.out.println("Value set to 12");}, 2, TimeUnit.SECONDS);
				long start = System.currentTimeMillis();
				Channels.waitForValue(channel, 12);
				long end = System.currentTimeMillis();
				System.out.println("value reached within "+ (end-start));
				long time = end-start;
				// Check whether the time waited was approximately 2 seconds
				assertTrue(time > 1900 && time < 2100);
			}
		}
	}
}
