/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.time.StopWatch;
import org.epics.ca.impl.JavaProcessManager;
import org.epics.ca.impl.monitor.MonitorNotificationServiceFactoryCreator;
import org.epics.ca.impl.repeater.NetworkUtilities;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.fail;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

class ChannelThroughputTests
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( ChannelThroughputTests.class );
   private ThreadWatcher threadWatcher;
   
   private JavaProcessManager processManager;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/

   @BeforeEach
   void beforeEach()
   {
      threadWatcher = ThreadWatcher.start();
      
      // Currently (2020-05-22) this test is not supported when the VPN connection is active on the local machine.
      if ( NetworkUtilities.isVpnActive() )
      {
         fail( "This test is not supported when a VPN connection is active on the local network interface." );
      }

      EpicsChannelAccessTestServer.start();
   }

   @AfterEach
   void afterEach()
   {
      EpicsChannelAccessTestServer.shutdown();
      threadWatcher.verify();
   }

   /**
    * Tests the CA Get performance.
    *
    * Performs multiple CA puts on a monitored test channel and then times how long it takes
    * to read the value back again using CA get.
    *
    * @param numberOfGets the number of put/get cycles to perform.
    */
   @ParameterizedTest
   @ValueSource( ints = { 1, 10, 100, 1000, 2000, 5000 } )
   void testGet( int numberOfGets )
   {
      logger.info( String.format( "Starting Get throughput test for %d CA gets", numberOfGets ) );
      try ( final Context context = new Context() )
      {
         final Channel<Integer> channel = context.createChannel("adc01", Integer.class );
         channel.connect();

         final StopWatch stopWatch = StopWatch.createStarted();
         for ( int i = 0; i < numberOfGets; i++ )
         {
            channel.get();
         }
         long elapseTimeInMilliseconds = stopWatch.getTime(TimeUnit.MILLISECONDS);

         logger.info( "RESULTS:");
         logger.info( String.format("- Synchronous Get with %d gets took %s ms. Average: %3f ms.", numberOfGets, elapseTimeInMilliseconds, (float) elapseTimeInMilliseconds / (float) numberOfGets));
         logger.info( "" );
      }
   }

   /**
    * Tests the CA Put-and-Get performance.
    *
    * Performs multiple CA puts on a monitored test channel and then times how long it takes
    * to read the value back again using CA get.
    *
    * @param numberOfPutsAndGets the number of put/get cycles to perform.
    */
   @ParameterizedTest
   @ValueSource( ints = { 1, 10, 100, 1000, 2000, 5000 } )
   void testPutAndGet( int numberOfPutsAndGets )
   {
      try ( final Context context = new Context() )
      {
         logger.info( String.format("Starting PutAndGet throughput test for %d CA puts/gets", numberOfPutsAndGets));
         final Channel<Integer> channel = context.createChannel("adc01", Integer.class);
         channel.connect();

         final StopWatch stopWatch = StopWatch.createStarted();
         for ( int i = 0; i < numberOfPutsAndGets; i++ )
         {
            channel.get();
         }
         long elapseTimeInMilliseconds = stopWatch.getTime(TimeUnit.MILLISECONDS);

         logger.info( "RESULTS:");
         logger.info( String.format("- Synchronous PutAndGet with %d puts/gets took %s ms. Average: %3f ms.", numberOfPutsAndGets, elapseTimeInMilliseconds, (float) elapseTimeInMilliseconds / (float) numberOfPutsAndGets));
         logger.info( "" );
      }
   }

   /**
    * Tests the CA Put-and-Monitor performance for different monitor notification service implementations.
    *
    * Performs multiple CA puts on a monitored test channel and measures how long it takes for the
    * notifications to be received by the monitor's consumer.
    *
    * @param serviceImpl the service implementation.
    * @param numberOfPuts the number of puts to perform. Each put will result in a new monitor
    *                     notification.
    */
   @ParameterizedTest
   @MethodSource( "getArgumentsForTestPutAndMonitor" )
   void testPutAndMonitor( String serviceImpl, int numberOfPuts )
   {
      logger.info( String.format("Starting PutAndMonitor throughput test using monitor notification impl: '%s' and for %d CA puts", serviceImpl, numberOfPuts ) );
      final Properties contextProperties = new Properties();
      contextProperties.setProperty( "CA_MONITOR_NOTIFIER_IMPL", serviceImpl );

      try ( final Context context = new Context( contextProperties ) )
      {
         final Channel<Integer> channel = context.createChannel("adc01", Integer.class);
         channel.connect();

         // Add a value monitor and wait for first notification of the initial value
         final NotificationConsumer<Integer> notificationConsumer = NotificationConsumer.getNormalConsumer();
         notificationConsumer.clearCurrentNotificationCount();
         notificationConsumer.setExpectedNotificationCount(1);
         final Monitor<Integer> monitor = channel.addValueMonitor(notificationConsumer);
         notificationConsumer.awaitExpectedNotificationCount();

         // Now send the requested number of puts
         notificationConsumer.clearCurrentNotificationCount();
         final StopWatch notificationDeliveryMeasurementStopWatch = StopWatch.createStarted();
         for ( int i = 0; i < numberOfPuts; i++ )
         {
            channel.put(i);
         }

         // The end of sequence token is necessary become some monitor notification service
         // implementations may be lossy but they are always guaranteed to deliver the last
         // value in a sequence so we use this to detect the end of the notification sequence.
         // It's also convenient to measure the latency here too.
         final Integer endOfSequence = -1;
         notificationConsumer.setExpectedNotificationValue(endOfSequence);
         final StopWatch latencyMeasurementStopWatch = StopWatch.createStarted();
         channel.put(endOfSequence);
         notificationConsumer.awaitExpectedNotificationValue();

         final long multipleNotificationDeliveryTimeInMilliseconds = notificationDeliveryMeasurementStopWatch.getTime(TimeUnit.MILLISECONDS);
         final long singleNotificationDeliveryLatencyInMicroseconds = latencyMeasurementStopWatch.getTime(TimeUnit.MICROSECONDS);

         logger.info( "RESULTS:" );
         logger.info( String.format("- The test consumer received: %d notifications", notificationConsumer.getCurrentNotificationCount()));
         logger.info( String.format("- The delivery latency was typically %d us", singleNotificationDeliveryLatencyInMicroseconds));
         logger.info( String.format("- Synchronous PutAndMonitor with %d puts took %s ms. Average: %3f ms.", numberOfPuts, multipleNotificationDeliveryTimeInMilliseconds, (float) multipleNotificationDeliveryTimeInMilliseconds / (float) numberOfPuts));
         logger.info("");
      }
   }

   /**
    * Tests the CA throughput performance of the library when using a CA monitor
    * to receive notifications from a fast running counter.
    *
    * The test can be configured for different monitor notification service
    * implementations and to listen for a configurable number of notifications.
    *
    * @param serviceImpl the service implementation.
    * @param numberOfNotifications the number of notifications to listen for.
    */
   @ParameterizedTest
   @MethodSource( "getArgumentsForTestFastCounterMonitor" )
   void testFastCounterMonitor( String serviceImpl, int numberOfNotifications )
   {
      logger.info( String.format("Starting FastCounterMonitor throughput test using impl: '%s'and for '%d' notifications...", serviceImpl, numberOfNotifications ) );
      final Properties contextProperties = new Properties();
      contextProperties.setProperty( "CA_MONITOR_NOTIFIER_IMPL", serviceImpl );

      try ( final Context context = new Context( contextProperties ) )
      {
         final Channel<Integer> channel = context.createChannel("1msCounter", Integer.class );
         channel.connect();
         final List<Monitor<Integer>> monitorList = new ArrayList<>();

         // Can optionally set here the number of monitors that will simultaneously deliver
         // notifications to the CA library TCP/IP socket and thus explore the performance of
         // the system under increasing stress.
         final int numberOfMonitors = 1;
         for ( int i = 0; i < numberOfMonitors; i++ )
         {
            final NotificationConsumer<Integer> notificationConsumer = NotificationConsumer.getNormalConsumer();
            monitorList.add(channel.addValueMonitor(notificationConsumer));
         }

         final int totalNotificationCount = numberOfNotifications * numberOfMonitors;
         NotificationConsumer.setExpectedTotalNotificationCount(totalNotificationCount);
         NotificationConsumer.clearCurrentTotalNotificationCount();

         final StopWatch stopWatch = StopWatch.createStarted();
         NotificationConsumer.awaitExpectedTotalNotificationCount();
         final long elapsedTimeInMilliseconds = stopWatch.getTime(TimeUnit.MILLISECONDS);

         // Release references to monitor objects
         monitorList.forEach( Monitor::close );
         monitorList.clear();

         logger.info("RESULTS:");
         logger.info( String.format("- The test consumer received: %d notifications", NotificationConsumer.getCurrentTotalNotificationCount()));
         logger.info( String.format("- FastCounterMonitor with %d notifications took %s ms. Average: %3f ms.", totalNotificationCount, elapsedTimeInMilliseconds, (float) elapsedTimeInMilliseconds / (float) totalNotificationCount));
         logger.info("");
      }
   }

/*- Private methods ----------------------------------------------------------*/

   /**
    * Provides the argument data for the specified test.
    * @return the data.
    */
   private static Stream<Arguments> getArgumentsForTestPutAndMonitor()
   {
      final List<String> serviceImpls = MonitorNotificationServiceFactoryCreator.getAllServiceImplementations();
      final List<Integer> numberOfPuts = Arrays.asList( 100, 2000 );

      return serviceImpls.stream().flatMap(s -> numberOfPuts.stream().map(n -> Arguments.of(s, n) ) );
   }

   /**
    * Provides the argument data for the specified test.
    * @return the data.
    */
   private static Stream<Arguments> getArgumentsForTestFastCounterMonitor()
   {
      final List<String> serviceImpls = MonitorNotificationServiceFactoryCreator.getAllServiceImplementations();
      final List<Integer> notifications = Arrays.asList( 100, 2000 );

      return serviceImpls.stream().flatMap(s -> notifications.stream().map(n -> Arguments.of(s, n) ) );
   }

/*- Nested Classes -----------------------------------------------------------*/

}
