/**
 * 
 */
package org.epics.ca.test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.TestCase;

import org.epics.ca.Channel;
import org.epics.ca.ConnectionState;
import org.epics.ca.Context;

/**
 * @author msekoranja
 *
 */
public class ChannelTest extends TestCase {

	private Context context;
	private CAJTestServer server;
	private static final int TIMEOUT_SEC = 5;
	
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

	public void testConnect() throws Throwable {
		
		try (Channel<Double> channel = context.createChannel("no_such_channel_test", Double.class))
		{
			assertNotNull(channel);
			
			assertEquals(ConnectionState.NEVER_CONNECTED, channel.getConnectionState());
			try {
				channel.connect().get(TIMEOUT_SEC, TimeUnit.SECONDS);
				fail("connected on non-existent channel, timeout expected");
			} catch (TimeoutException tc) {
				// OK
			}
			
			assertEquals(ConnectionState.NEVER_CONNECTED, channel.getConnectionState());
		};

		try (Channel<Double> channel = context.createChannel("adc01", Double.class))
		{
			assertNotNull(channel);
			
			assertEquals(ConnectionState.NEVER_CONNECTED, channel.getConnectionState());
			channel.connect().get(TIMEOUT_SEC, TimeUnit.SECONDS);
			assertEquals(ConnectionState.CONNECTED, channel.getConnectionState());
		};
	}

	
	public void testConnectionListener() throws Throwable {
		
		try (Channel<Double> channel = context.createChannel("adc01", Double.class))
		{
			assertNotNull(channel);
			
			assertEquals(ConnectionState.NEVER_CONNECTED, channel.getConnectionState());
			
			final AtomicInteger connectedCount = new AtomicInteger();
			final AtomicInteger disconnectedCount = new AtomicInteger();
			
			channel.addConnectionListener((c, connected) -> {
				if (c == channel)
				{
					if (connected.booleanValue())
						connectedCount.incrementAndGet();
					else
						disconnectedCount.incrementAndGet();
				}
			});

			channel.connect().get(TIMEOUT_SEC, TimeUnit.SECONDS);
			assertEquals(ConnectionState.CONNECTED, channel.getConnectionState());
			
			// we need to sleep here to catch any possible multiple/invalid events
			Thread.sleep(TIMEOUT_SEC * 1000);
			
			assertEquals(1, connectedCount.get());
			assertEquals(0, disconnectedCount.get());
			
			channel.close();

			// we need to sleep here to catch any possible multiple/invalid events
			Thread.sleep(TIMEOUT_SEC * 1000);
			
			// TODO close does not notify disconnect, or should it?
			assertEquals(1, connectedCount.get());
			assertEquals(0, disconnectedCount.get());
			
			
		};
	}
	
}
