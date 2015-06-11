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
import org.epics.ca.Status;
import org.epics.ca.data.Alarm;
import org.epics.ca.data.AlarmSeverity;
import org.epics.ca.data.AlarmStatus;
import org.epics.ca.data.Control;
import org.epics.ca.data.Graphic;
import org.epics.ca.data.Metadata;
import org.epics.ca.data.Timestamped;

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
	
	private <T> void internalTestPutAndGet(String channelName, Class<T> clazz, T expectedValue, boolean async) throws Throwable
	{
		try (Channel<T> channel = context.createChannel(channelName, clazz))
		{
			channel.connect().get();

			if (async)
			{
				Status status = channel.putAsync(expectedValue).get(TIMEOUT_SEC, TimeUnit.SECONDS);
				assertTrue(status.isSuccessful());
			}
			else
				channel.put(expectedValue);
			
			T value;
			if (async)
			{
				value = channel.getAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
				assertNotNull(value);
			}
			else
				value = channel.get();
			
			if (clazz.isArray())
				arrayEquals(expectedValue, value);
			else
				assertEquals(expectedValue, value);
		}
	}
	
	private void internalTestValuePutAndGet(boolean async) throws Throwable
	{
		internalTestPutAndGet("adc01", String.class, "12.346", async);	// precision == 3
		internalTestPutAndGet("adc01", Short.class, Short.valueOf((short)123), async);
		internalTestPutAndGet("adc01", Float.class, Float.valueOf(-123.4f), async);
		internalTestPutAndGet("adc01", Byte.class, Byte.valueOf((byte)100), async);
		internalTestPutAndGet("adc01", Integer.class, Integer.valueOf(123456), async);
		internalTestPutAndGet("adc01", Double.class, Double.valueOf(12.3456), async);

		internalTestPutAndGet("adc01", String[].class, new String[] { "12.356", "3.112" }, async);	// precision == 3
		internalTestPutAndGet("adc01", short[].class, new short[] { (short)123, (short)-321 }, async);
		internalTestPutAndGet("adc01", float[].class, new float[] { -123.4f, 321.98f }, async);
		internalTestPutAndGet("adc01", byte[].class, new byte[] { (byte)120, (byte)-120 }, async);
		internalTestPutAndGet("adc01", int[].class, new int[] { 123456, 654321 }, async);
		internalTestPutAndGet("adc01", double[].class, new double[] { 12.82, 3.112 }, async);
	}
	
	public void testValuePutAndGetSync() throws Throwable
	{
		internalTestValuePutAndGet(false);
	}
	
	public void testValuePutAndGetAsync() throws Throwable
	{
		internalTestValuePutAndGet(true);
	}
	
	@SuppressWarnings({"unchecked", "rawtypes"})
	private <T, ST, MT extends Metadata<T>> void internalTestMetaPutAndGet(String channelName, Class<T> clazz, Class<ST> scalarClazz, T expectedValue, Class<? extends Metadata> meta, Alarm expectedAlarm, boolean async) throws Throwable
	{
		try (Channel<T> channel = context.createChannel(channelName, clazz))
		{
			channel.connect().get();

			if (async)
			{
				Status status = channel.putAsync(expectedValue).get(TIMEOUT_SEC, TimeUnit.SECONDS);
				assertTrue(status.isSuccessful());
			}
			else
				channel.put(expectedValue);
			
			MT value;
			if (async)
			{
				value = (MT)channel.getAsync(meta).get(TIMEOUT_SEC, TimeUnit.SECONDS);
				assertNotNull(value);
			}
			else
				value = channel.get(meta);
			
			if (Alarm.class.isAssignableFrom(meta))
			{
				Alarm<T> v = (Alarm<T>)value;
				assertEquals(expectedAlarm.getAlarmStatus(), v.getAlarmStatus());
				assertEquals(expectedAlarm.getAlarmSeverity(), v.getAlarmSeverity());
			}
			
			if (Timestamped.class.isAssignableFrom(meta))
			{
				Timestamped<T> v = (Timestamped<T>)value;
				long dt = System.currentTimeMillis() - v.getMillis();
				assertTrue(dt < (TIMEOUT_SEC * 1000));
			}

			System.out.println(meta);
			if (Graphic.class.isAssignableFrom(meta))
			{
				System.out.println("graphics");
				Graphic<T, ST> v = (Graphic<T, ST>)value;
				// TODO 
				// System.out.println(Number.class.cast(v.getLowerAlarm()).doubleValue());
			}

			if (Control.class.isAssignableFrom(meta))
			{
				Control<T, ST> v = (Control<T, ST>)value;
				
				// TODO
			}
		
			if (clazz.isArray())
				arrayEquals(expectedValue, value.getValue());
			else
				assertEquals(expectedValue, value.getValue());
		}
	}
	
	private <T, ST> void internalTestMetaPutAndGet(String channelName, Class<T> clazz, Class<ST> scalarClazz, T expectedValue, Alarm expectedAlarm, boolean async) throws Throwable
	{
		internalTestMetaPutAndGet(channelName, clazz, scalarClazz, expectedValue, Alarm.class, expectedAlarm, async);	// precision == 3
		internalTestMetaPutAndGet(channelName, clazz, scalarClazz, expectedValue, Timestamped.class, expectedAlarm, async);
		if (!clazz.equals(String.class) && !clazz.equals(String[].class))
		{
			internalTestMetaPutAndGet(channelName, clazz, scalarClazz, expectedValue, Graphic.class, expectedAlarm, async);
			internalTestMetaPutAndGet(channelName, clazz, scalarClazz, expectedValue, Control.class, expectedAlarm, async);
		}
	}
	
	private void internalTestMetaPutAndGet(boolean async) throws Throwable
	{
		Alarm<Double> alarm = new Alarm<Double>();
		alarm.setAlarmStatus(AlarmStatus.UDF_ALARM);
		alarm.setAlarmSeverity(AlarmSeverity.INVALID_ALARM);
		
		internalTestMetaPutAndGet("adc01", String.class, String.class, "12.346", alarm, async);	// precision == 3
		internalTestMetaPutAndGet("adc01", Short.class, Short.class, Short.valueOf((short)123), alarm, async);
		internalTestMetaPutAndGet("adc01", Float.class, Float.class, Float.valueOf(-123.4f), alarm, async);
		internalTestMetaPutAndGet("adc01", Byte.class, Byte.class, Byte.valueOf((byte)100), alarm, async);
		internalTestMetaPutAndGet("adc01", Integer.class, Integer.class, Integer.valueOf(123456), alarm, async);
		internalTestMetaPutAndGet("adc01", Double.class, Double.class, Double.valueOf(12.3456), alarm, async);

		internalTestMetaPutAndGet("adc01", String[].class, String.class, new String[] { "12.356", "3.112" }, alarm, async);	// precision == 3
		internalTestMetaPutAndGet("adc01", short[].class, Short.class, new short[] { (short)123, (short)-321 }, alarm, async);
		internalTestMetaPutAndGet("adc01", float[].class, Float.class, new float[] { -123.4f, 321.98f }, alarm, async);
		internalTestMetaPutAndGet("adc01", byte[].class, Byte.class, new byte[] { (byte)120, (byte)-120 }, alarm, async);
		internalTestMetaPutAndGet("adc01", int[].class, Integer.class, new int[] { 123456, 654321 }, alarm, async);
		internalTestMetaPutAndGet("adc01", double[].class, Double.class, new double[] { 12.82, 3.112 }, alarm, async);
	}
	
	public void testMetaPutAndGetSync() throws Throwable
	{
		internalTestMetaPutAndGet(false);
	}
	
	public void testMetaPutAndGetAsync() throws Throwable
	{
		internalTestMetaPutAndGet(true);
	}
	
	
}
