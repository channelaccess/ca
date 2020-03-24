/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import org.epics.ca.data.*;
import org.epics.ca.impl.monitor.MonitorNotificationServiceFactoryCreator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.jupiter.api.Assertions.*;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * @author msekoranja
 */
class ChannelTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   // Get Logger
   private static final Logger logger = Logger.getLogger( ChannelTest.class.getName () );

   private static final double DELTA = 1e-10;

   //private Context context;
   private static EpicsChannelAccessTestServer server;
   private static final int TIMEOUT_SEC = 5;

   private static Future<?> future;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/

   @BeforeAll
   static void beforeAll()
   {
      System.setProperty( "java.util.logging.SimpleFormatter.format", "%1$tF %1$tT.%1$tL %4$s  %5$s%6$s%n");
      System.setProperty( Context.Configuration.EPICS_CA_ADDR_LIST.toString(), "127.0.0.1" );
      System.setProperty( "CA_DEBUG", "5" );
      System.setProperty( "EPICS_CA_AUTO_ADDR_LIST", "NO" );
      Locale.setDefault( Locale.ROOT );
      server = new EpicsChannelAccessTestServer();
      future =server.runInSeparateThread ();
   }

   @AfterAll
   static void afterAll()
   {
      server.destroy();
      future.cancel( true );
   }

   @Test
   void logTest()
   {
      logger.log(Level.FINEST, "My msg is: %s", "abc" );
   }


   @Test
   void testConnect() throws Throwable
   {
      try ( Context context = new Context() )
      {
         try ( Channel<Double> channel = context.createChannel("no_such_channel_test", Double.class) )
         {
            assertThat( channel, notNullValue()  );
            assertThat( channel.getName(), is( "no_such_channel_test" ));
            assertThat( channel.getConnectionState(), is( ConnectionState.NEVER_CONNECTED ) );
            try
            {
               channel.connectAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
               fail( "connected on non-existent channel, timeout expected");
            }
            catch ( TimeoutException tc )
            {
               // OK
            }
            assertThat( channel.getConnectionState(), is( ConnectionState.NEVER_CONNECTED ) );
         }

         try ( Channel<Double> channel = context.createChannel("adc01", Double.class) )
         {
            assertThat( channel, notNullValue()  );
            assertThat( channel.getName(), is( "adc01" ));
            assertThat( channel.getConnectionState(), is( ConnectionState.NEVER_CONNECTED ) );
            channel.connectAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
            assertThat( channel.getConnectionState(), is( ConnectionState.CONNECTED ) );
            assertThat( channel.getName(), is( "adc01" ));
         }

         // connect to the previously closed channel
         try ( Channel<Double> channel = context.createChannel("adc01", Double.class) )
         {
            assertThat( channel, notNullValue()  );
            assertThat( channel.getConnectionState(), is( ConnectionState.NEVER_CONNECTED ) );
            channel.connectAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
            assertThat( channel.getConnectionState(), is( ConnectionState.CONNECTED ) );
         }
      }
   }

   @Test
   void testConnectionListener() throws Throwable
   {
      try ( Context context = new Context() )
      {
         try ( Channel<Double> channel = context.createChannel("adc01", Double.class) )
         {
            assertThat( channel, notNullValue()  );
            assertThat( channel.getConnectionState(), is( ConnectionState.NEVER_CONNECTED ) );

            final AtomicInteger connectedCount = new AtomicInteger();
            final AtomicInteger disconnectedCount = new AtomicInteger();
            final AtomicInteger unregisteredEventCount = new AtomicInteger();
            final Listener cl = channel.addConnectionListener(( c, connected ) -> {
               if ( c == channel )
               {
                  if ( connected )
                  {
                     connectedCount.incrementAndGet();
                  }
                  else
                  {
                     disconnectedCount.incrementAndGet();
                  }
               }
            });
            assertThat( cl, notNullValue()  );

            final Listener cl2 = channel.addConnectionListener(( c, connected ) -> unregisteredEventCount.incrementAndGet());
            assertThat( cl2, notNullValue() );
            assertThat( unregisteredEventCount.get(), is( 0 ));
            cl2.close();

            channel.connectAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
            assertThat( channel.getConnectionState(), is( ConnectionState.CONNECTED ) );

            // we need to sleep here to catch any possible multiple/invalid events
            Thread.sleep(TIMEOUT_SEC * 1000);

            assertThat( connectedCount.get(), is( 1 ));
            assertThat( disconnectedCount.get(), is( 0 ));
            assertThat( unregisteredEventCount.get(), is( 0 ));
            channel.close();

            // we need to sleep here to catch any possible multiple/invalid events
            Thread.sleep(TIMEOUT_SEC * 1000);

            // NOTE: close does not notify disconnect
            assertThat( connectedCount.get(), is( 1 ));
            assertThat( disconnectedCount.get(), is( 0 ));
            assertThat( unregisteredEventCount.get(), is( 0 ));
         }
      }
   }

   @Test
   void testAccessRightsListener() throws Throwable
   {
      try ( Context context = new Context() )
      {
         try ( Channel<Double> channel = context.createChannel("adc01", Double.class) )
         {
            assertThat( channel, notNullValue()  );

            final AtomicInteger aclCount = new AtomicInteger();
            final AtomicInteger unregisteredEventCount = new AtomicInteger();
            final Listener rl = channel.addAccessRightListener(( c, ar ) -> {
               if ( c == channel )
               {
                  if ( ar == AccessRights.READ_WRITE )
                     aclCount.incrementAndGet();
               }
            });
            assertThat( rl, notNullValue() );

            final Listener cl2 = channel.addAccessRightListener(( c, ar ) -> unregisteredEventCount.incrementAndGet());
            assertThat( cl2, notNullValue() );
            assertThat( unregisteredEventCount.get(), is( 0 ));
            cl2.close();

            channel.connectAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
            assertThat( channel.getAccessRights(), is(AccessRights.READ_WRITE ) );

            // we need to sleep here to catch any possible multiple/invalid events
            Thread.sleep(TIMEOUT_SEC * 1000);

            assertThat( aclCount.get(), is( 1 ));
            assertThat( unregisteredEventCount.get(), is( 0 ));
            channel.close();

            // we need to sleep here to catch any possible multiple/invalid events
            Thread.sleep(TIMEOUT_SEC * 1000);
            assertThat( aclCount.get(), is( 1 ));
            assertThat( unregisteredEventCount.get(), is( 0 ));
         }
      }
   }

   @Test
   void testProperties() throws Throwable
   {
      try ( Context context = new Context() )
      {
         try ( Channel<Double> channel = context.createChannel("adc01", Double.class) )
         {
            channel.connectAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);

            final Map<String, Object> props = channel.getProperties();
            final Object nativeTypeCode = props.get(Constants.ChannelProperties.nativeTypeCode.name());
            assertThat( nativeTypeCode, notNullValue()  );
            assertThat( nativeTypeCode, is( (short) 6 ) );

            final Object nativeElementCount = props.get(Constants.ChannelProperties.nativeElementCount.name());
            assertThat( nativeElementCount, notNullValue()  );
            assertThat( nativeElementCount, is( 2) );

            final Object nativeType = props.get(Constants.ChannelProperties.nativeType.name());
            assertThat( nativeType, notNullValue()  );
            assertThat( nativeType, is( Double.class ) );
         }
      }
   }

   // Note: this definition exists only to workaround the Mockito uncast warning in the test below
   interface GenericIntegerConsumer extends Consumer<Integer> {}


   @MethodSource( "getArgumentsForTestMonitorNotificationServiceImplementations" )
   @ParameterizedTest
   void testMonitorDisconnectionBehaviour( String serviceImpl ) throws InterruptedException
   {
      final Properties contextProperties = new Properties();
      contextProperties.setProperty( "CA_MONITOR_NOTIFIER_IMPL", serviceImpl );

      try ( final Context context = new Context( contextProperties ) )
      {
         try ( Channel<Integer> channel = context.createChannel("adc01", Integer.class) )
         {
            channel.addConnectionListener(( c, h ) -> logger.log(Level.INFO, String.format("Channel '%s', new connection state is: '%s' ", c.getName(), c.getConnectionState())));

            // Connect to some channel and get the default value (= value on creation) for the test PV
            channel.connect();
            final int defautAdcValue = channel.get();

            // Change the PV value to something else, allow the change to propagate
            // then verify that the expected value was received.
            final int testValue = 99;
            channel.put(testValue);
            final Consumer<Integer> consumer = Mockito.mock(GenericIntegerConsumer.class);
            channel.addValueMonitor(consumer);
            Thread.sleep(1000);
            Mockito.verify(consumer, Mockito.times(1)).accept(testValue);

            // Destroy the test server which will create a channel disconnection event.
            // Verify that the monitor did not receive a new update
            server.destroy();
            Thread.sleep(1000);
            Mockito.verifyNoMoreInteractions(consumer);

            // Now recreate the server and check that the monitor received an update with the default value
            // for this PV
            server = new EpicsChannelAccessTestServer();
            server.runInSeparateThread();
            Thread.sleep(1000);
            Mockito.verify(consumer, Mockito.times(1)).accept(defautAdcValue);
         }
      }
   }

   @Test
   void testMonitors() throws Throwable
   {
      try ( Context context = new Context() )
      {
         try ( Channel<Integer> channel = context.createChannel("counter", Integer.class) )
         {
            channel.connect();
            try
            {
               channel.addValueMonitor(null);
               fail("null handler accepted");
            }
            catch ( NullPointerException iae )
            {
               // ok
            }

            try
            {
               channel.addValueMonitor(( value ) -> {
               }, 0);
               fail("empty mask accepted");
            }
            catch ( IllegalArgumentException iae )
            {
               // ok
            }

            // note: we accept currently non-valid masks to allow future/unstandard extensions
            try ( Monitor<Integer> m = channel.addValueMonitor(( value ) -> {
            }, Monitor.VALUE_MASK)
            )
            {
               assertNotNull(m);
            }

            final AtomicInteger monitorCount = new AtomicInteger();
            final Monitor<Integer> m = channel.addValueMonitor(( value ) -> monitorCount.incrementAndGet(), Monitor.VALUE_MASK);
            assertNotNull(m);
            Thread.sleep(TIMEOUT_SEC * 1000);
            m.close();
            m.close();
            final int monitors = monitorCount.get();
            assertThat( monitors, greaterThanOrEqualTo( TIMEOUT_SEC ) ); // 1 + TIMEOUT_SEC (where one can be missed)
            Thread.sleep(TIMEOUT_SEC * 1000);
            assertThat( monitorCount.get(), is( monitors ));
         }
      }
   }

   @ParameterizedTest
   @ValueSource( strings = { "true", "false" } )
   void testGenericChannel( String asyncFlag ) throws Throwable
   {
//      try ( Context context = new Context() )
//      {
//         try ( Channel<Object> channel = context.createChannel("adc01", Object.class) )
//         {
//            assertThat( channel, notNullValue()  );
//            channel.connect();
//          }
//      }
      internalTestValuePutAndGet( asyncFlag );
//      internalTestMetaPutAndGet( asyncFlag );

   }

   @Test
   void testLargeArray() throws Throwable
   {
      final String propName = com.cosylab.epics.caj.cas.CAJServerContext.class.getName () + ".max_array_bytes";
      final String oldValue = System.getProperty (propName);
      System.setProperty (propName, String.valueOf (4 * 1024 * 1024 + 1024 + 32));
      try ( Context context = new Context() )
      {
         try
         {
            try ( Channel<int[]> channel = context.createChannel("large", int[].class) )
            {
               channel.connect();

               int[] value = channel.getAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
               assertThat( value, notNullValue()  );

               final int LARGE_PRIME = 15485863;
               for ( int i = 0; i < value.length; i++ )
               {
                  assertThat( value[ i ], is( i ) );
                  value[ i ] += LARGE_PRIME;
               }

               final Status putStatus = channel.putAsync(value).get(TIMEOUT_SEC, TimeUnit.SECONDS);
               assertThat( putStatus, is( Status.NORMAL) );

               value = channel.getAsync().get(TIMEOUT_SEC, TimeUnit.SECONDS);
               assertThat( channel, notNullValue() );

               for ( int i = 0; i < value.length; i++ )
                  assertThat( value[ i ], is( i + LARGE_PRIME ) );
            }
         }
         finally
         {
            // restore value
            if ( oldValue == null )
               System.clearProperty(propName);
            else
               System.setProperty(propName, oldValue);
         }
      }
   }

   @Test
   void testGraphicEnum() throws Throwable
   {
      final Alarm<Double> alarm = new Alarm<>();
      alarm.setAlarmStatus (AlarmStatus.UDF_ALARM);
      alarm.setAlarmSeverity (AlarmSeverity.INVALID_ALARM);

      final String[] labels =
            { "zero", "one", "two", "three", "four", "five", "six", "seven" };

      internalTestGraphicEnum ("enum", Short.class, (short) 2, alarm, labels, false);
      internalTestGraphicEnum ("enum", Short.class, (short) 3, alarm, labels, true);
      internalTestGraphicEnum ("enum", short[].class, new short[] { 1, 2 }, alarm, labels, false);
      internalTestGraphicEnum ("enum", short[].class, new short[] { 3, 4 }, alarm, labels, true);
   }

   @ParameterizedTest
   @MethodSource( "getArgumentsForTestMonitorNotificationServiceImplementations" )
   void testContextCloseAlsoClosesMonitorNotifier( String serviceImpl )
   {
      assertThat( MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 0L ) );

      final Properties contextProperties = new Properties();
      contextProperties.setProperty( "CA_MONITOR_NOTIFIER_IMPL", serviceImpl );
      final Context context = new Context( contextProperties );

      final Channel<Integer> channel = context.createChannel ("adc01", Integer.class);
      channel.connect();
      assertThat( MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 0L ) );

      final TestConsumer<Integer> testConsumer = TestConsumer.getNormalConsumer();
      TestConsumer.clearCurrentTotalNotificationCount();
      TestConsumer.setExpectedTotalNotificationCount( 2 );
      channel.addValueMonitor( testConsumer );
      channel.addValueMonitor( testConsumer );
      assertThat( MonitorNotificationServiceFactoryCreator.getServiceCount() , is( 2L ) );
      TestConsumer.awaitExpectedTotalNotificationCount();

      context.close();
      assertThat( MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 0L ) );
   }

   @ParameterizedTest
   @MethodSource( "getArgumentsForTestMonitorNotificationServiceImplementations" )
   void testChannelCloseDoesNotCloseMonitorNotifier( String serviceImpl )
   {
      assertThat(MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 0L ) );

      final Properties contextProperties = new Properties();
      contextProperties.setProperty( "CA_MONITOR_NOTIFIER_IMPL", serviceImpl );
      try( final Context context = new Context( contextProperties ) )
      {
         final Channel<Integer> channel = context.createChannel("adc01", Integer.class);
         channel.connect();

         assertThat(MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 0L ));

         final TestConsumer<Integer> testConsumer = TestConsumer.getNormalConsumer();
         TestConsumer.clearCurrentTotalNotificationCount();
         TestConsumer.setExpectedTotalNotificationCount(2);
         channel.addValueMonitor(testConsumer);
         channel.addValueMonitor(testConsumer);
         assertThat(MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 2L ));
         TestConsumer.awaitExpectedTotalNotificationCount();

         // Note: closing a channel does NOT currently close the MonitorNotificationService.
         // Therefore the count of created instances is not reset to zero.
         // TODO: we might want to look at this behaviour in the future and decide whether it needs to change !
         channel.close();
         assertThat(MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 2L ));

      }
      // After we close the context the MonitorNotificationService gets closed and the count is reset again.
      assertThat(MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 0L ) );
   }

   @ParameterizedTest
   @MethodSource( "getArgumentsForTestMonitorNotificationServiceImplementations" )
   void testMonitorCloseDoesNotAlsoClosesMonitorNotifier( String serviceImpl )
   {
      assertThat( MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 0L ) );

      final Properties contextProperties = new Properties();
      contextProperties.setProperty( "CA_MONITOR_NOTIFIER_IMPL", serviceImpl );
      try( final Context context = new Context( contextProperties ) )
      {
         assertThat( MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 0L ));

         final Channel<Integer> channel = context.createChannel("adc01", Integer.class);
         channel.connect();

         assertThat( MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 0L ));

         final TestConsumer<Integer> testConsumer = TestConsumer.getNormalConsumer();
         TestConsumer.clearCurrentTotalNotificationCount();
         TestConsumer.setExpectedTotalNotificationCount(2);
         final Monitor<Integer> monitor1 = channel.addValueMonitor(testConsumer);
         final Monitor<Integer> monitor2 = channel.addValueMonitor(testConsumer);
         assertThat( MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 2L ));
         TestConsumer.awaitExpectedTotalNotificationCount();

         // Note: closing a channel does NOT currently close the MonitorNotificationService.
         // Therefore the count of created instances is not reset to zero.
         // TODO: we might want to look at this behaviour in the future and decide whether it needs to change !
         monitor1.close();
         assertThat( MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 2L ));
         monitor2.close();
         assertThat( MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 2L ));
      }
      // After we close the context the MonitorNotificationService gets closed and the count is reset again.
      assertThat(MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 0L ) );
   }

/*- Private methods ----------------------------------------------------------*/

   private static Stream<Arguments> getArgumentsForTestMonitorNotificationServiceImplementations()
   {
      final List<String> serviceImpls = MonitorNotificationServiceFactoryCreator.getAllServiceImplementations();
      return serviceImpls.stream().map(Arguments::of);
   }


   private <T> void internalTestPutAndGet( String channelName, Class<T> clazz, T expectedValue, boolean async ) throws Throwable
   {
      try ( Context context = new Context() )
      {
         try ( Channel<T> channel = context.createChannel( channelName, clazz) )
         {
            channel.connect();
            final T t = channel.get();

            if ( async )
            {
               Status status = channel.putAsync( expectedValue ).get( TIMEOUT_SEC, TimeUnit.SECONDS );
               assertTrue( status.isSuccessful() );
            }
            else
            {
               channel.putNoWait( expectedValue );
            }

            final T value;
            if ( async )
            {
               value = channel.getAsync().get( TIMEOUT_SEC, TimeUnit.SECONDS );
               assertNotNull( value );
            }
            else
            {
               value = channel.get();
            }

            if ( clazz.isArray() )
            {
               assertThat( arrayEquals( value, expectedValue ), is( true ) );
            }
            else
            {
               assertThat( value, is( expectedValue ) );
            }

         }
      }
   }

   private void internalTestValuePutAndGet( String asyncFlag ) throws Throwable
   {
      final boolean async = Boolean.parseBoolean( asyncFlag  );


      internalTestPutAndGet ("adc01", String[].class, new String[] { "12.356", "3.112" }, async);   // precision == 3
      internalTestPutAndGet ("adc01", short[].class, new short[] { (short) 123, (short) -321 }, async);
      internalTestPutAndGet ("adc01", float[].class, new float[] { -123.4f, 321.98f }, async);
      internalTestPutAndGet ("adc01", byte[].class, new byte[] { (byte) 120, (byte) -120 }, async);
      internalTestPutAndGet ("adc01", int[].class, new int[] { 123456, 654321 }, async);
      internalTestPutAndGet ("adc01", double[].class, new double[] { 12.82, 3.112 }, async);

//      internalTestPutAndGet ("adc01", String.class, "12.346", async);   // precision == 3
//      internalTestPutAndGet ("adc01", Short.class, (short) 123, async);
//      internalTestPutAndGet ("adc01", Float.class, -123.4f, async);
//      internalTestPutAndGet ("adc01", Byte.class, (byte) 100, async);
//      internalTestPutAndGet ("adc01", Integer.class, 123456, async);
//      internalTestPutAndGet ("adc01", Double.class, 12.3456, async);

   }

   @SuppressWarnings( { "unchecked", "rawtypes" } )
   private <T, ST, MT extends Metadata<T>> void internalTestMetaPutAndGet( String channelName, Class<T> clazz, Class<ST> scalarClazz, T expectedValue, Class<? extends Metadata> meta, Alarm<?> expectedAlarm, Control<?, Double> expectedMeta, boolean async ) throws Throwable
   {
      try ( Context context = new Context() )
      {
         try ( Channel<T> channel = context.createChannel(channelName, clazz) )
         {
            channel.connect();

            if ( async )
            {
               Status status = channel.putAsync( expectedValue ).get( TIMEOUT_SEC, TimeUnit.SECONDS );
               assertTrue(status.isSuccessful());
            }
            else
               channel.putNoWait(expectedValue);

            final MT value;
            if ( async )
            {
               value = (MT) channel.getAsync(meta).get( TIMEOUT_SEC, TimeUnit.SECONDS );
               assertNotNull(value);
            }
            else
            {
               value = channel.get( meta );
            }

            if ( Alarm.class.isAssignableFrom( meta ) )
            {
               final Alarm<T> v = (Alarm<T>) value;
               assertThat( v.getAlarmStatus(), is( expectedAlarm.getAlarmStatus()));
               assertThat( v.getAlarmSeverity(), is( expectedAlarm.getAlarmSeverity()));
            }

            if ( Timestamped.class.isAssignableFrom(meta) )
            {
               final Timestamped<T> v = (Timestamped<T>) value;
               long dt = System.currentTimeMillis() - v.getMillis();
               assertTrue(dt < (TIMEOUT_SEC * 1000));
            }

            if ( Graphic.class.isAssignableFrom(meta) )
            {
               final Graphic<T, ST> v = (Graphic<T, ST>) value;

               assertThat( v.getUnits(),is( expectedMeta.getUnits()) );
               if ( scalarClazz.equals(Double.class) || scalarClazz.equals(Float.class) )
                  assertEquals( expectedMeta.getPrecision(), v.getPrecision());
               // no NaN or other special values allowed
               assertThat( ((Number) v.getLowerAlarm()).doubleValue(), closeTo(expectedMeta.getLowerAlarm(), DELTA ));
               assertThat( ((Number) v.getLowerDisplay()).doubleValue(), closeTo(expectedMeta.getLowerDisplay(), DELTA ));
               assertThat( ((Number) v.getLowerWarning()).doubleValue(), closeTo(expectedMeta.getLowerWarning(), DELTA ));
               assertThat( ((Number) v.getUpperAlarm()).doubleValue(), closeTo(expectedMeta.getUpperAlarm(), DELTA ));
               assertThat( ((Number) v.getUpperDisplay()).doubleValue(), closeTo(expectedMeta.getUpperDisplay(), DELTA ));
               assertThat( ((Number) v.getUpperWarning()).doubleValue(), closeTo(expectedMeta.getUpperWarning(), DELTA ));
            }

            if ( Control.class.isAssignableFrom(meta) )
            {
               final Control<T, ST> v = (Control<T, ST>) value;
               assertThat( ((Number) v.getLowerControl()).doubleValue(), closeTo(expectedMeta.getLowerControl(), DELTA ) );
               assertThat( ((Number) v.getUpperControl()).doubleValue(), closeTo(expectedMeta.getUpperControl(), DELTA ) );
            }

            if ( clazz.isArray() )
            {
               assertThat( arrayEquals( value.getValue(), expectedValue ), is( true ) );
            }
            else
            {
               assertThat( value.getValue(), is( expectedValue ) );
            }
         }
      }
   }

   @SuppressWarnings( "SameParameterValue" )
   private <T, ST> void internalTestMetaPutAndGet( String channelName, Class<T> clazz, Class<ST> scalarClazz, T expectedValue, Alarm<?> expectedAlarm, Control<?, Double> expectedMeta, boolean async ) throws Throwable
   {
      internalTestMetaPutAndGet (channelName, clazz, scalarClazz, expectedValue, Alarm.class, expectedAlarm, expectedMeta, async);   // precision == 3
      internalTestMetaPutAndGet (channelName, clazz, scalarClazz, expectedValue, Timestamped.class, expectedAlarm, expectedMeta, async);
      if ( !clazz.equals (String.class) && !clazz.equals (String[].class) )
      {
         internalTestMetaPutAndGet (channelName, clazz, scalarClazz, expectedValue, Graphic.class, expectedAlarm, expectedMeta, async);
         internalTestMetaPutAndGet (channelName, clazz, scalarClazz, expectedValue, Control.class, expectedAlarm, expectedMeta, async);
      }
   }

   private void internalTestMetaPutAndGet( String asyncFlag ) throws Throwable
   {
      boolean async = Boolean.parseBoolean( asyncFlag  );

      Alarm<Double> alarm = new Alarm<> ();
      alarm.setAlarmStatus (AlarmStatus.UDF_ALARM);
      alarm.setAlarmSeverity (AlarmSeverity.INVALID_ALARM);

      Control<Double, Double> meta = new Control<> ();
      meta.setUpperDisplay( 10d );
      meta.setLowerDisplay( -10.0 );
      meta.setUpperAlarm( 9.0 );
      meta.setLowerAlarm( -9.0 );
      meta.setUpperControl( 8d );
      meta.setLowerControl( -8.0 );
      meta.setUpperWarning( 7d );
      meta.setLowerWarning( -7.0 );
      meta.setUnits ("units");
      meta.setPrecision( (short) 3);

      internalTestMetaPutAndGet ("adc01", String.class, String.class, "12.346", alarm, meta, async);   // precision == 3
      internalTestMetaPutAndGet ("adc01", Short.class, Short.class, (short) 123, alarm, meta, async);
      internalTestMetaPutAndGet ("adc01", Float.class, Float.class, -123.4f, alarm, meta, async);
      internalTestMetaPutAndGet ("adc01", Byte.class, Byte.class, (byte) 100, alarm, meta, async);
      internalTestMetaPutAndGet ("adc01", Integer.class, Integer.class, 123456, alarm, meta, async);
      internalTestMetaPutAndGet ("adc01", Double.class, Double.class, 12.3456, alarm, meta, async);

//      internalTestMetaPutAndGet ("adc01", String[].class, String.class, new String[] { "12.356", "3.112" }, alarm, meta, async);   // precision == 3
//      internalTestMetaPutAndGet ("adc01", short[].class, Short.class, new short[] { (short) 123, (short) -321 }, alarm, meta, async);
//      internalTestMetaPutAndGet ("adc01", float[].class, Float.class, new float[] { -123.4f, 321.98f }, alarm, meta, async);
//      internalTestMetaPutAndGet ("adc01", byte[].class, Byte.class, new byte[] { (byte) 120, (byte) -120 }, alarm, meta, async);
//      internalTestMetaPutAndGet ("adc01", int[].class, Integer.class, new int[] { 123456, 654321 }, alarm, meta, async);
//      internalTestMetaPutAndGet ("adc01", double[].class, Double.class, new double[] { 12.82, 3.112 }, alarm, meta, async);
   }

   @SuppressWarnings( "SameParameterValue" )
   private <T> void internalTestGraphicEnum( String channelName, Class<T> clazz, T expectedValue, Alarm<?> expectedAlarm, String[] expectedLabels, boolean async ) throws Throwable
   {
      try ( Context context = new Context() )
      {
         // put
         try ( Channel<T> channel = context.createChannel(channelName, clazz) )
         {
            channel.connect();

            if ( async )
            {
               Status status = channel.putAsync(expectedValue).get(TIMEOUT_SEC, TimeUnit.SECONDS);
               assertTrue(status.isSuccessful());
            }
            else
            {
               channel.putNoWait(expectedValue);
            }

            final Alarm<T> value;
            @SuppressWarnings( "rawtypes" )
            final Class<? extends Metadata> gec = clazz.isArray() ? GraphicEnumArray.class : GraphicEnum.class;
            if ( async )
            {
               value = (Alarm<T>) channel.getAsync(gec).get(TIMEOUT_SEC, TimeUnit.SECONDS);
               assertNotNull(value);
            }
            else
            {
               value = channel.get(gec);
            }

            if ( clazz.isArray() )
            {
               assertThat( arrayEquals( value.getValue(), expectedValue ), is( true ) );
            }
            else
            {
               assertEquals( value.getValue(), expectedValue);
            }

            assertThat( value.getAlarmStatus(), is( expectedAlarm.getAlarmStatus() ) );
            assertThat(  value.getAlarmSeverity(), is( expectedAlarm.getAlarmSeverity() ) );

            final String[] labels = clazz.isArray() ? ((GraphicEnumArray) value).getLabels() : ((GraphicEnum) value).getLabels();
            assertThat( labels, arrayContaining( expectedLabels ));
         }
      }
   }

   private static <T> boolean arrayEquals( T arr1, T arr2 ) throws Exception
   {
      Class<?> c = arr1.getClass ();
      if ( !c.getComponentType ().isPrimitive () )
         c = Object[].class;

      return (Boolean) Arrays.class.getMethod ("equals", c, c).invoke (null, arr1, arr2);
   }

/*- Nested Classes -----------------------------------------------------------*/

}
