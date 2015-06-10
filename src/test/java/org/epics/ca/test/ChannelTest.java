/**
 * 
 */
package org.epics.ca.test;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.TestCase;

import org.epics.ca.AccessRights;
import org.epics.ca.Channel;
import org.epics.ca.ConnectionState;
import org.epics.ca.Context;
import org.epics.ca.Listener;

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
			assertEquals("no_such_channel_test", channel.getName());
			
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
			assertEquals("adc01", channel.getName());
			
			assertEquals(ConnectionState.NEVER_CONNECTED, channel.getConnectionState());
			channel.connect().get(TIMEOUT_SEC, TimeUnit.SECONDS);
			assertEquals(ConnectionState.CONNECTED, channel.getConnectionState());
			assertEquals("adc01", channel.getName());
		};
	}

	
	public void testConnectionListener() throws Throwable {
		
		try (Channel<Double> channel = context.createChannel("adc01", Double.class))
		{
			assertNotNull(channel);
			
			assertEquals(ConnectionState.NEVER_CONNECTED, channel.getConnectionState());
			
			final AtomicInteger connectedCount = new AtomicInteger();
			final AtomicInteger disconnectedCount = new AtomicInteger();
			final AtomicInteger unregsiteredEventCount = new AtomicInteger();
			
			Listener cl = channel.addConnectionListener((c, connected) -> {
				if (c == channel)
				{
					if (connected.booleanValue())
						connectedCount.incrementAndGet();
					else
						disconnectedCount.incrementAndGet();
				}
			});
			assertNotNull(cl);
			
			Listener cl2 = channel.addConnectionListener((c, connected) -> unregsiteredEventCount.incrementAndGet());
			assertNotNull(cl2);
			assertEquals(0, unregsiteredEventCount.get());
			cl2.close();

			channel.connect().get(TIMEOUT_SEC, TimeUnit.SECONDS);
			assertEquals(ConnectionState.CONNECTED, channel.getConnectionState());
			
			// we need to sleep here to catch any possible multiple/invalid events
			Thread.sleep(TIMEOUT_SEC * 1000);
			
			assertEquals(1, connectedCount.get());
			assertEquals(0, disconnectedCount.get());

			assertEquals(0, unregsiteredEventCount.get());

			channel.close();

			// we need to sleep here to catch any possible multiple/invalid events
			Thread.sleep(TIMEOUT_SEC * 1000);
			
			// TODO close does not notify disconnect, or should it?
			assertEquals(1, connectedCount.get());
			assertEquals(0, disconnectedCount.get());
			
			assertEquals(0, unregsiteredEventCount.get());
		};
	}
	
	public void testAccessRightsListener() throws Throwable {
		
		try (Channel<Double> channel = context.createChannel("adc01", Double.class))
		{
			assertNotNull(channel);
			
			final AtomicInteger aclCount = new AtomicInteger();
			final AtomicInteger unregsiteredEventCount = new AtomicInteger();

			Listener rl = channel.addAccessRightListener((c, ar) -> {
				if (c == channel)
				{
					if (ar == AccessRights.READ_WRITE)
						aclCount.incrementAndGet();
				}
			});
			assertNotNull(rl);
			
			Listener cl2 = channel.addAccessRightListener((c, ar) -> unregsiteredEventCount.incrementAndGet());
			assertNotNull(cl2);
			assertEquals(0, unregsiteredEventCount.get());
			cl2.close();

			channel.connect().get(TIMEOUT_SEC, TimeUnit.SECONDS);
			assertEquals(AccessRights.READ_WRITE, channel.getAccessRights());
			
			// we need to sleep here to catch any possible multiple/invalid events
			Thread.sleep(TIMEOUT_SEC * 1000);
			
			assertEquals(1, aclCount.get());

			assertEquals(0, unregsiteredEventCount.get());

			channel.close();

			// we need to sleep here to catch any possible multiple/invalid events
			Thread.sleep(TIMEOUT_SEC * 1000);
			
			assertEquals(1, aclCount.get());
			
			assertEquals(0, unregsiteredEventCount.get());
		};
	}

	public void testProperties() throws Throwable {
		
		try (Channel<Double> channel = context.createChannel("adc01", Double.class))
		{
			channel.connect().get(TIMEOUT_SEC, TimeUnit.SECONDS);
			
			Map<String, Object> props = channel.getProperties();
			// TODO constants?
			Object nativeType = props.get("nativeType");
			assertNotNull(nativeType);
			assertEquals(Short.valueOf((short)6), (Short)nativeType);
			
			Object nativeElementCount = props.get("nativeElementCount");
			assertNotNull(nativeElementCount);
			assertEquals(Integer.valueOf(2), (Integer)nativeElementCount);
		};
	}
	
	public static <T> boolean arrayEquals(T arr1, T arr2) throws Exception {
	    Class<?> c = arr1.getClass();
	    if (!c.getComponentType().isPrimitive()) 
	    	c = Object[].class;
	    
	    return (Boolean) Arrays.class.getMethod("equals", c, c).invoke(null, arr1, arr2);
	}
	
	private <T> void internalTestGet(String channelName, Class<T> clazz, T expectedValue) throws Throwable
	{
		try (Channel<T> channel = context.createChannel(channelName, clazz))
		{
			channel.connect().get();

			T value = channel.get();
			
			if (clazz.isArray())
				arrayEquals(expectedValue, value);
			else
				assertEquals(expectedValue, value);
		}
	}
	
	public void testValueGet() throws Throwable
	{
		internalTestGet("adc01", String.class, "12.080");	// precision == 3
		internalTestGet("adc01", Short.class, Short.valueOf((short)12));
		internalTestGet("adc01", Float.class, Float.valueOf(12.08f));
		internalTestGet("adc01", Byte.class, Byte.valueOf((byte)12));
		internalTestGet("adc01", Integer.class, Integer.valueOf(12));
		internalTestGet("adc01", Double.class, Double.valueOf(12.08));

		internalTestGet("adc01", String[].class, new String[] { "12.080", "3.110" });	// precision == 3
		internalTestGet("adc01", short[].class, new short[] { (short)12, (short)3 } );
		internalTestGet("adc01", float[].class, new float[] { 12.08f, 3.11f });
		internalTestGet("adc01", byte[].class, new byte[] { (byte)12, (byte)3 });
		internalTestGet("adc01", int[].class, new int[] { 12, 3 });
		internalTestGet("adc01", double[].class, new double[] { 12.8, 3.11 });
	}
	
}
