/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import org.epics.ca.data.*;
import org.epics.ca.impl.JavaProcessManager;
import org.epics.ca.impl.monitor.MonitorNotificationServiceFactoryCreator;
import org.epics.ca.impl.repeater.NetworkUtilities;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.mockito.Mockito;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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

   private static final Logger logger = LibraryLogManager.getLogger( ChannelTest.class );
   private ThreadWatcher threadWatcher;
   
   private static JavaProcessManager processManager;

   private static final double DELTA = 1e-10;
   private static final int TIMEOUT_MILLISEC = 10000;
   private static final int TEST_SLEEP_INTERVAL_MILLISEC = 500;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/

   @BeforeEach
   void beforeEach()
   {
      threadWatcher = ThreadWatcher.start();

      // Currently (2020-05-22) this test is not supported when the VPN connection is active on the local machine.
      if ( NetworkUtilities.isVpnActive() )
      {
         fail( "This test is not supported when a VPN connection is active on the local network interface." );
      }

      // One could consider tieing down more precisely the address to which the EPICS Test
      // Server will get bound. Currently (2020-06-01) this is not necessary.
      // System.setProperty( Context.Configuration.EPICS_CA_ADDR_LIST.toString(), "127.0.0.1" );
      // System.setProperty( "EPICS_CA_AUTO_ADDR_LIST", "NO" );
      processManager = EpicsChannelAccessTestServer.start();
   }

   @AfterEach
   void afterEach()
   {
      processManager.shutdown();
      threadWatcher.verify();
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
               channel.connectAsync().get( TIMEOUT_MILLISEC, TimeUnit.MILLISECONDS);
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
            channel.connectAsync().get( TIMEOUT_MILLISEC, TimeUnit.MILLISECONDS );
            assertThat( channel.getConnectionState(), is( ConnectionState.CONNECTED ) );
            assertThat( channel.getName(), is( "adc01" ));
         }

         // connect to the previously closed channel
         try ( Channel<Double> channel = context.createChannel("adc01", Double.class) )
         {
            assertThat( channel, notNullValue()  );
            assertThat( channel.getConnectionState(), is( ConnectionState.NEVER_CONNECTED ) );
            channel.connectAsync().get(TIMEOUT_MILLISEC, TimeUnit.MILLISECONDS);
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

            channel.connectAsync().get( TIMEOUT_MILLISEC, TimeUnit.MILLISECONDS);
            assertThat( channel.getConnectionState(), is( ConnectionState.CONNECTED ) );

            // we need to sleep here to catch any possible multiple/invalid events
            Thread.sleep( TEST_SLEEP_INTERVAL_MILLISEC );

            assertThat( connectedCount.get(), is( 1 ));
            assertThat( disconnectedCount.get(), is( 0 ));
            assertThat( unregisteredEventCount.get(), is( 0 ));
            channel.close();

            // we need to sleep here to catch any possible multiple/invalid events
            Thread.sleep( TEST_SLEEP_INTERVAL_MILLISEC );

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
                  {
                     aclCount.incrementAndGet();
                  }   
               }
            });
            assertThat( rl, notNullValue() );

            final Listener cl2 = channel.addAccessRightListener(( c, ar ) -> unregisteredEventCount.incrementAndGet());
            assertThat( cl2, notNullValue() );
            assertThat( unregisteredEventCount.get(), is( 0 ));
            cl2.close();

            channel.connectAsync().get( TIMEOUT_MILLISEC, TimeUnit.MILLISECONDS);
            assertThat( channel.getAccessRights(), is(AccessRights.READ_WRITE ) );

            // we need to sleep here to catch any possible multiple/invalid events
            Thread.sleep( TEST_SLEEP_INTERVAL_MILLISEC );

            assertThat( aclCount.get(), is( 1 ));
            assertThat( unregisteredEventCount.get(), is( 0 ));
            channel.close();

            // we need to sleep here to catch any possible multiple/invalid events
            Thread.sleep( TEST_SLEEP_INTERVAL_MILLISEC );
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
            channel.connectAsync().get( TIMEOUT_MILLISEC, TimeUnit.MILLISECONDS );

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

   @MethodSource( "getArgumentsForMonitorNotificationServiceImplementations" )
   @ParameterizedTest
   void testMonitorDisconnectionBehaviour( String serviceImpl ) throws InterruptedException
   {
      final Properties contextProperties = new Properties();
      contextProperties.setProperty( "CA_MONITOR_NOTIFIER_IMPL", serviceImpl );

      try ( final Context context = new Context( contextProperties ) )
      {
         try ( Channel<Integer> channel = context.createChannel("adc01", Integer.class) )
         {
            channel.addConnectionListener(( c, h ) -> logger.info( String.format( "Channel '%s', new connection state is: '%s' ", c.getName(), c.getConnectionState() ) ) );

            // Connect to some channel and get the default value (= value on creation) for the test PV
            assertDoesNotThrow( () -> {
               logger.info( "connecting async..." );
               channel.connectAsync().get( TIMEOUT_MILLISEC, TimeUnit.MILLISECONDS );
               logger.info( "connected." );
            } );

            final int defautAdcValue = channel.get();

            // Change the PV value to something else, allow the change to propagate
            // then verify that the expected value was received.
            final int testValue = 99;
            channel.put(testValue);
            final Consumer<Integer> consumer = Mockito.mock( GenericIntegerConsumer.class );
            channel.addValueMonitor( consumer );
            Thread.sleep( TEST_SLEEP_INTERVAL_MILLISEC );
            Mockito.verify( consumer, Mockito.times(1)).accept( testValue );

            // Destroy the test server which will create a channel disconnection event.
            // Verify that the monitor did not receive a new update
            processManager.shutdown();
            Thread.sleep( TEST_SLEEP_INTERVAL_MILLISEC );
            Mockito.verifyNoMoreInteractions( consumer );
         }
      }
   }

   @Test
   void testMonitors() throws Throwable
   {
      try ( Context context = new Context() )
      {
         try ( Channel<Integer> channel = context.createChannel("100msCounter", Integer.class) )
         {
            channel.connect();
            try
            {
               channel.addValueMonitor(null );
               fail( "null handler accepted" );
            }
            catch ( NullPointerException iae )
            {
               // ok
            }

            try
            {
               channel.addValueMonitor( (value) -> {}, 0 );
               fail( "empty mask accepted" );
            }
            catch ( IllegalArgumentException iae )
            {
               // ok
            }

            // note: we accept currently non-valid masks to allow future/unstandard extensions
            try ( Monitor<Integer> monitor = channel.addValueMonitor( (value) -> {}, Monitor.VALUE_MASK) )
            {
               assertNotNull(monitor);
            }

            final AtomicInteger monitorNotificationCount = new AtomicInteger();
            final Monitor<Integer> monitor = channel.addValueMonitor( (value) -> monitorNotificationCount.incrementAndGet(), Monitor.VALUE_MASK);
            assertNotNull( monitor );
            Thread.sleep( 5000 );
            monitor.close();
            final int monitorNotifications = monitorNotificationCount.get();
            assertThat( monitorNotificationCount.get(), greaterThanOrEqualTo( 48 ) );
            Thread.sleep( 1000 );
            assertThat( monitorNotificationCount.get(), is( monitorNotifications ));
         }
      }
   }

   @Test
   void testLargeArray() throws Throwable
   {
      try ( Context context = new Context() )
      {
         try ( Channel<int[]> channel = context.createChannel("large", int[].class) )
         {
            assertDoesNotThrow( () -> channel.connectAsync().get( TIMEOUT_MILLISEC, TimeUnit.MILLISECONDS ) );

            int[] value = channel.getAsync().get( TIMEOUT_MILLISEC, TimeUnit.MILLISECONDS );
            assertThat( value, notNullValue()  );

            final int LARGE_PRIME = 15485863;
            for ( int i = 0; i < value.length; i++ )
            {
               assertThat( value[ i ], is( i ) );
               value[ i ] += LARGE_PRIME;
            }

            final Status putStatus = channel.putAsync(value).get( TIMEOUT_MILLISEC, TimeUnit.MILLISECONDS);
            assertThat( putStatus, is( Status.NORMAL) );

            value = channel.getAsync().get( TIMEOUT_MILLISEC, TimeUnit.MILLISECONDS);
            assertThat( channel, notNullValue() );

            for ( int i = 0; i < value.length; i++ )
            {
               assertThat(value[ i ], is(i + LARGE_PRIME));
            }
         }
      }
   }

   @ParameterizedTest
   @MethodSource( "getArgumentsForMonitorNotificationServiceImplementations" )
   void testContextCloseAlsoClosesMonitorNotifier( String serviceImpl )
   {
      assertThat( MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 0L ) );

      final Properties contextProperties = new Properties();
      contextProperties.setProperty( "CA_MONITOR_NOTIFIER_IMPL", serviceImpl );
      final Context context = new Context( contextProperties );

      final Channel<Integer> channel = context.createChannel ("adc01", Integer.class);
      assertDoesNotThrow( () -> channel.connectAsync().get( TIMEOUT_MILLISEC, TimeUnit.MILLISECONDS ) );
      assertThat( MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 0L ) );

      final NotificationConsumer<Integer> notificationConsumer = NotificationConsumer.getNormalConsumer();
      NotificationConsumer.clearCurrentTotalNotificationCount();
      NotificationConsumer.setExpectedTotalNotificationCount(2 );
      channel.addValueMonitor(notificationConsumer);
      channel.addValueMonitor(notificationConsumer);
      assertThat( MonitorNotificationServiceFactoryCreator.getServiceCount() , is( 2L ) );
      NotificationConsumer.awaitExpectedTotalNotificationCount();

      context.close();
      assertThat( MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 0L ) );
   }

   @ParameterizedTest
   @MethodSource( "getArgumentsForMonitorNotificationServiceImplementations" )
   void testChannelCloseDoesNotCloseMonitorNotifier( String serviceImpl )
   {
      assertThat( MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 0L ) );

      final Properties contextProperties = new Properties();
      contextProperties.setProperty( "CA_MONITOR_NOTIFIER_IMPL", serviceImpl );
      try( final Context context = new Context( contextProperties ) )
      {
         final Channel<Integer> channel = context.createChannel("adc01", Integer.class);
         assertDoesNotThrow( () -> channel.connectAsync().get( TIMEOUT_MILLISEC, TimeUnit.MILLISECONDS ) );

         assertThat( MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 0L ));

         final NotificationConsumer<Integer> notificationConsumer = NotificationConsumer.getNormalConsumer();
         NotificationConsumer.clearCurrentTotalNotificationCount();
         NotificationConsumer.setExpectedTotalNotificationCount( 2 );
         channel.addValueMonitor(notificationConsumer);
         channel.addValueMonitor(notificationConsumer);
         assertThat( MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 2L ));
         NotificationConsumer.awaitExpectedTotalNotificationCount();

         // Note: closing a channel does NOT currently close the MonitorNotificationService.
         // Therefore the count of created instances is not reset to zero.
         // TODO: we might want to look at this behaviour in the future and decide whether it needs to change !
         channel.close();
         assertThat( MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 2L ));

      }
      // After we close the context the MonitorNotificationService gets closed and the count is reset again.
      assertThat( MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 0L ) );
   }

   @ParameterizedTest
   @MethodSource( "getArgumentsForMonitorNotificationServiceImplementations" )
   void testMonitorCloseDoesNotAlsoCloseMonitorNotifier( String serviceImpl )
   {
      assertThat( MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 0L ) );

      final Properties contextProperties = new Properties();
      contextProperties.setProperty( "CA_MONITOR_NOTIFIER_IMPL", serviceImpl );
      try( final Context context = new Context( contextProperties ) )
      {
         assertThat( MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 0L ));

         final Channel<Integer> channel = context.createChannel("adc01", Integer.class);
         assertDoesNotThrow( () -> channel.connectAsync().get( TIMEOUT_MILLISEC, TimeUnit.MILLISECONDS ) );

         assertThat( MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 0L ));

         final NotificationConsumer<Integer> notificationConsumer = NotificationConsumer.getNormalConsumer();
         NotificationConsumer.clearCurrentTotalNotificationCount();
         NotificationConsumer.setExpectedTotalNotificationCount(2);
         final Monitor<Integer> monitor1 = channel.addValueMonitor(notificationConsumer);
         final Monitor<Integer> monitor2 = channel.addValueMonitor(notificationConsumer);
         assertThat( MonitorNotificationServiceFactoryCreator.getServiceCount(), is( 2L ));
         NotificationConsumer.awaitExpectedTotalNotificationCount();

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

   @MethodSource( "getArgumentsForTestPutAndGetValue" )
   @ParameterizedTest
   <T> void testPutAndGetValue( String channelName, Class<T> type, T expectedValue, boolean async ) throws Throwable
   {
      logger.info( "testPutAndGetValue invoked with args: " + String.format( "'%s', '%s', '%s'.", channelName, type.getSimpleName(), async ) );

      try ( Context context = new Context() )
      {
         try ( Channel<T> channel = context.createChannel( channelName, type) )
         {
            assertDoesNotThrow( () -> channel.connectAsync().get( TIMEOUT_MILLISEC, TimeUnit.MILLISECONDS ) );

            final T t = channel.get();

            if ( async )
            {
               Status status = channel.putAsync( expectedValue ).get( TIMEOUT_MILLISEC, TimeUnit.MILLISECONDS );
               assertTrue( status.isSuccessful() );
            }
            else
            {
               channel.putNoWait( expectedValue );
            }

            final T value;
            if ( async )
            {
               value = channel.getAsync().get( TIMEOUT_MILLISEC, TimeUnit.MILLISECONDS );
               assertNotNull( value );
            }
            else
            {
               value = channel.get();
            }

            assertThat( value, is( expectedValue ) );

         }
      }
   }

   @SuppressWarnings( "unchecked" )
   @MethodSource( "getArgumentsForTestPutAndGetMetadata" )
   @ParameterizedTest
   <T, ST, MT extends Metadata<T>> void testPutAndGetMetadata( String channelName, Class<T> type, Class<ST> scalarType, T expectedValue, Class<? extends Metadata<T>> metaType, Alarm<?> expectedAlarm, Control<?, Double> expectedMeta, boolean async ) throws Throwable
   {
      logger.info( "testPutAndGetMetadata invoked with args: " + String.format( "'%s', '%s', '%s', '%s', '%s'.",channelName, type.getSimpleName(), scalarType.getSimpleName(), metaType.getSimpleName(), async ) );
      if ( ( metaType.equals( Control.class ) || metaType.equals( Graphic.class ) ) && ( type.equals( String.class ) || type.equals( String[].class ) ) )
      {
         logger.info( "Skipping test because EPICS does not support this combination !");
         return;
      }

      logger.info( "Creating context...");
      try ( Context context = new Context() )
      {
         logger.info( "Done. Creating channel...");
         try ( Channel<T> channel = context.createChannel(channelName, type) )
         {
            logger.info( "Done. connecting channel...");
            assertDoesNotThrow( () -> channel.connectAsync().get( TIMEOUT_MILLISEC, TimeUnit.MILLISECONDS ) );

            if ( async )
            {
               Status status = channel.putAsync( expectedValue ).get( TIMEOUT_MILLISEC, TimeUnit.MILLISECONDS );
               assertTrue( status.isSuccessful() );
            }
            else
            {
               channel.putNoWait( expectedValue );
            }

            final MT value;
            if ( async )
            {
               value = (MT) channel.getAsync( metaType ).get( TIMEOUT_MILLISEC, TimeUnit.MILLISECONDS );
               assertNotNull(value);
            }
            else
            {
               value = channel.get( metaType );
            }

            if ( Alarm.class.isAssignableFrom( metaType ) )
            {
               final Alarm<T> v = (Alarm<T>) value;
               assertThat( v.getAlarmStatus(), is( expectedAlarm.getAlarmStatus()));
               assertThat( v.getAlarmSeverity(), is( expectedAlarm.getAlarmSeverity()));
            }

            if ( Timestamped.class.isAssignableFrom( metaType ) )
            {
               final Timestamped<T> v = (Timestamped<T>) value;
               long dt = System.currentTimeMillis() - v.getMillis();
               assertTrue(dt < (TIMEOUT_MILLISEC ) );
            }

            if ( Graphic.class.isAssignableFrom( metaType ) )
            {
               final Graphic<T, ST> v = (Graphic<T, ST>) value;

               assertThat( v.getUnits(),is( expectedMeta.getUnits()) );
               if ( scalarType.equals(Double.class) || scalarType.equals(Float.class) )
               {
                  assertEquals( expectedMeta.getPrecision(), v.getPrecision());
               }

               // no NaN or other special values allowed
               assertThat( ((Number) v.getLowerAlarm()).doubleValue(), closeTo(expectedMeta.getLowerAlarm(), DELTA ));
               assertThat( ((Number) v.getLowerDisplay()).doubleValue(), closeTo(expectedMeta.getLowerDisplay(), DELTA ));
               assertThat( ((Number) v.getLowerWarning()).doubleValue(), closeTo(expectedMeta.getLowerWarning(), DELTA ));
               assertThat( ((Number) v.getUpperAlarm()).doubleValue(), closeTo(expectedMeta.getUpperAlarm(), DELTA ));
               assertThat( ((Number) v.getUpperDisplay()).doubleValue(), closeTo(expectedMeta.getUpperDisplay(), DELTA ));
               assertThat( ((Number) v.getUpperWarning()).doubleValue(), closeTo(expectedMeta.getUpperWarning(), DELTA ));
            }

            if ( Control.class.isAssignableFrom( metaType ) )
            {
               final Control<T, ST> v = (Control<T, ST>) value;
               assertThat( ((Number) v.getLowerControl()).doubleValue(), closeTo(expectedMeta.getLowerControl(), DELTA ) );
               assertThat( ((Number) v.getUpperControl()).doubleValue(), closeTo(expectedMeta.getUpperControl(), DELTA ) );
            }
            assertThat( value.getValue(), is( expectedValue ) );
         }
      }
   }

   @MethodSource( "getArgumentsForTestGraphicEnum" )
   @ParameterizedTest
   <T> void testGraphicEnum( String channelName, Class<T> clazz, T expectedValue, Alarm<?> expectedAlarm, String[] expectedLabels, boolean async ) throws Throwable
   {
      try ( Context context = new Context() )
      {
         // put
         try ( Channel<T> channel = context.createChannel( channelName, clazz ) )
         {
            assertDoesNotThrow( () -> channel.connectAsync().get( TIMEOUT_MILLISEC, TimeUnit.MILLISECONDS ) );

            if ( async )
            {
               final Status status = channel.putAsync( expectedValue ).get( TIMEOUT_MILLISEC, TimeUnit.MILLISECONDS );
               assertTrue( status.isSuccessful() );
            }
            else
            {
               channel.putNoWait( expectedValue );
            }

            final Alarm<T> value;
            @SuppressWarnings( "rawtypes" )
            final Class<? extends Metadata> gec = clazz.isArray() ? GraphicEnumArray.class : GraphicEnum.class;
            if ( async )
            {
               value =  (Alarm<T>) channel.getAsync( gec ).get( TIMEOUT_MILLISEC, TimeUnit.MILLISECONDS );
               assertNotNull( value );
            }
            else
            {
               value = channel.get( gec );
            }

            assertThat( value.getValue(), is( expectedValue ) );
            assertThat( value.getAlarmStatus(), is( expectedAlarm.getAlarmStatus() ) );
            assertThat( value.getAlarmSeverity(), is( expectedAlarm.getAlarmSeverity() ) );

            final String[] labels = clazz.isArray() ? ((GraphicEnumArray) value).getLabels() : ((GraphicEnum) value).getLabels();
            assertThat( labels, arrayContaining( expectedLabels ));
         }
      }
   }

/*- Private methods ----------------------------------------------------------*/

   private static Stream<Arguments> getArgumentsForMonitorNotificationServiceImplementations()
   {
      final List<String> serviceImpls = MonitorNotificationServiceFactoryCreator.getAllServiceImplementations();
      return serviceImpls.stream().map(Arguments::of);
   }

   private static Stream<Arguments> getArgumentsForTestPutAndGetValue()
   {
      // Note: precision == 3
      final List<Boolean> asyncOptions = Arrays.asList( false, true );
      return asyncOptions.stream()
            .flatMap( (async) ->
               Stream.of( Arguments.of( "adc01", String.class, "12.346", async ),
                          Arguments.of( "adc01", Short.class, (short) 123, async),
                          Arguments.of( "adc01", Float.class, -123.4f, async ),
                          Arguments.of( "adc01", Byte.class, (byte) 100, async),
                          Arguments.of( "adc01", Integer.class, 123456, async ),
                          Arguments.of( "adc01", Double.class, 12.3456, async),

                          Arguments.of( "adc01", String[].class, new String[] { "12.356", "3.112" }, async ),
                          Arguments.of( "adc01", short[].class, new short[] { (short) 123, (short) -321 }, async ),
                          Arguments.of( "adc01", float[].class, new float[] { -123.4f, 321.98f }, async ),
                          Arguments.of( "adc01", byte[].class, new byte[] { (byte) 120, (byte) -120 }, async ),
                          Arguments.of( "adc01", int[].class, new int[] { 123456, 654321 }, async ),
                          Arguments.of( "adc01", double[].class, new double[] { 12.82, 3.112 }, async ) ) );
   }

   private static Stream<Arguments> getArgumentsForTestPutAndGetMetadata()
   {
      final Alarm<Double> alarm = new Alarm<>();
      alarm.setAlarmStatus (AlarmStatus.UDF_ALARM);
      alarm.setAlarmSeverity (AlarmSeverity.INVALID_ALARM);

      final Control<Double, Double> meta = new Control<> ();
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

      // precision == 3
      @SuppressWarnings( "rawtypes" )
      final List<Class<? extends Metadata>> metaTypeOptions = Arrays.asList( Alarm.class, Timestamped.class, Control.class, Graphic.class );

      final List<Boolean> asyncOptions = Arrays.asList( false, true );
      return metaTypeOptions.stream()
            .flatMap( (metaType) ->
                Stream.of( Arguments.of( "adc01", String.class, String.class, "12.346", metaType, alarm, meta, false ),
                           Arguments.of( "adc01", Short.class, Short.class, (short) 123, metaType, alarm, meta, false ),
                           Arguments.of( "adc01", Float.class, Float.class, -123.4f, metaType, alarm, meta, false ),
                           Arguments.of( "adc01", Byte.class, Byte.class, (byte) 100, metaType, alarm, meta, false ),
                           Arguments.of( "adc01", Integer.class, Integer.class, 123456, metaType, alarm, meta, false ),
                           Arguments.of( "adc01", Double.class, Double.class, 12.3456, metaType, alarm, meta, false ),

                           Arguments.of( "adc01", String.class, String.class, "12.346", metaType, alarm, meta, true ),
                           Arguments.of( "adc01", Short.class, Short.class, (short) 123, metaType, alarm, meta, true ),
                           Arguments.of( "adc01", Float.class, Float.class, -123.4f, metaType, alarm, meta, true ),
                           Arguments.of( "adc01", Byte.class, Byte.class, (byte) 100, metaType, alarm, meta, true ),
                           Arguments.of( "adc01", Integer.class, Integer.class, 123456, metaType, alarm, meta, true ),
                           Arguments.of( "adc01", Double.class, Double.class, 12.3456, metaType, alarm, meta, true ),

                           Arguments.of( "adc01", String[].class, String.class, new String[] { "12.356", "3.112" }, metaType, alarm, meta, false ),
                           Arguments.of( "adc01", short[].class, Short.class, new short[] { (short) 123, (short) -321 }, metaType, alarm, meta, false ),
                           Arguments.of( "adc01", float[].class, Float.class, new float[] { -123.4f, 321.98f }, metaType, alarm, meta, false ),
                           Arguments.of( "adc01", byte[].class, Byte.class, new byte[] { (byte) 120, (byte) -120 }, metaType, alarm, meta, false ),
                           Arguments.of( "adc01", int[].class, Integer.class, new int[] { 123456, 654321 }, metaType, alarm, meta, false ),
                           Arguments.of( "adc01", double[].class, Double.class, new double[] { 12.82, 3.112 }, metaType, alarm, meta, false ),

                           Arguments.of( "adc01", String[].class, String.class, new String[] { "12.356", "3.112" }, metaType, alarm, meta, true ),
                           Arguments.of( "adc01", short[].class, Short.class, new short[] { (short) 123, (short) -321 }, metaType, alarm, meta, true ),
                           Arguments.of( "adc01", float[].class, Float.class, new float[] { -123.4f, 321.98f }, metaType, alarm, meta, true ),
                           Arguments.of( "adc01", byte[].class, Byte.class, new byte[] { (byte) 120, (byte) -120 }, metaType, alarm, meta, true ),
                           Arguments.of( "adc01", int[].class, Integer.class, new int[] { 123456, 654321 }, metaType, alarm, meta, true ),
                           Arguments.of( "adc01", double[].class, Double.class, new double[] { 12.82, 3.112 }, metaType, alarm, meta, true ) )
      );
   }

   private static Stream<Arguments> getArgumentsForTestGraphicEnum()
   {
      final Alarm<Double> alarm = new Alarm<>();
      alarm.setAlarmStatus( AlarmStatus.UDF_ALARM );
      alarm.setAlarmSeverity( AlarmSeverity.INVALID_ALARM );

      final String[] labels = { "zero", "one", "two", "three", "four", "five", "six", "seven" };

      final List<Boolean> asyncOptions = Arrays.asList( false, true );
      return asyncOptions.stream()
         .flatMap( (async) ->
            Stream.of( Arguments.of( "enum", Short.class, (short) 2, alarm, labels, async ),
                       Arguments.of( "enum", Short.class, (short) 3, alarm, labels, async ),
                       Arguments.of( "enum", short[].class, new short[] { 4, 2 }, alarm, labels, async ),
                       Arguments.of( "enum", short[].class, new short[] { 1, 3 }, alarm, labels, async ) ) );
   }

/*- Nested Classes -----------------------------------------------------------*/

}
