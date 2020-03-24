/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.time.StopWatch;
import org.epics.ca.impl.monitor.MonitorNotificationServiceFactoryCreator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

class ChannelThroughputTests
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = Logger.getLogger( ChannelThroughputTests.class.getName() );

   private EpicsChannelAccessTestServer server;


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/

   @BeforeAll
   static void beforeAll()
   {
      System.setProperty( "java.util.logging.SimpleFormatter.format", "%1$tF %1$tT.%1$tL %4$s  %5$s%6$s%n");
      Locale.setDefault( Locale.ROOT );
   }

   @BeforeEach
   void setUp()
   {
      server = new EpicsChannelAccessTestServer();
      server.runInSeparateThread();
   }

   @AfterEach
   void tearDown()
   {
      server.destroy();
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
   void TestGet( int numberOfGets )
   {
      logger.log( Level.INFO, String.format("Starting Get throughput test for %d CA gets", numberOfGets ) );
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

         logger.info("RESULTS:");
         logger.log(Level.INFO, String.format("- Synchronous PutAndGet with %d puts/gets took %s ms. Average: %3f ms.", numberOfGets, elapseTimeInMilliseconds, (float) elapseTimeInMilliseconds / (float) numberOfGets));
         logger.info("");
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
   void TestPutAndGet( int numberOfPutsAndGets )
   {
      try ( final Context context = new Context() )
      {
         logger.log(Level.INFO, String.format("Starting PutAndGet throughput test for %d CA puts/gets", numberOfPutsAndGets));
         final Channel<Integer> channel = context.createChannel("adc01", Integer.class);
         channel.connect();

         final StopWatch stopWatch = StopWatch.createStarted();
         for ( int i = 0; i < numberOfPutsAndGets; i++ )
         {
            channel.get();
         }
         long elapseTimeInMilliseconds = stopWatch.getTime(TimeUnit.MILLISECONDS);

         logger.info("RESULTS:");
         logger.log(Level.INFO, String.format("- Synchronous PutAndGet with %d puts/gets took %s ms. Average: %3f ms.", numberOfPutsAndGets, elapseTimeInMilliseconds, (float) elapseTimeInMilliseconds / (float) numberOfPutsAndGets));
         logger.info("");
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
   void TestPutAndMonitor( String serviceImpl, int numberOfPuts )
   {
      logger.log( Level.INFO, String.format("Starting PutAndMonitor throughput test using monitor notification impl: '%s' and for %d CA puts", serviceImpl, numberOfPuts ) );
      final Properties contextProperties = new Properties();
      contextProperties.setProperty( "CA_MONITOR_NOTIFIER_IMPL", serviceImpl );

      try ( final Context context = new Context( contextProperties ) )
      {
         final Channel<Integer> channel = context.createChannel("adc01", Integer.class);
         channel.connect();

         // Add a value monitor and wait for first notification of the initial value
         final TestConsumer<Integer> testConsumer = TestConsumer.getNormalConsumer();
         testConsumer.clearCurrentNotificationCount();
         testConsumer.setExpectedNotificationCount(1);
         final Monitor monitor = channel.addValueMonitor(testConsumer);
         testConsumer.awaitExpectedNotificationCount();

         // Now send the requested number of puts
         testConsumer.clearCurrentNotificationCount();
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
         testConsumer.setExpectedNotificationValue(endOfSequence);
         final StopWatch latencyMeasurementStopWatch = StopWatch.createStarted();
         channel.put(endOfSequence);
         testConsumer.awaitExpectedNotificationValue();

         final long multipleNotificationDeliveryTimeInMilliseconds = notificationDeliveryMeasurementStopWatch.getTime(TimeUnit.MILLISECONDS);
         final long singleNotificationDeliveryLatencyInMicroseconds = latencyMeasurementStopWatch.getTime(TimeUnit.MICROSECONDS);

         logger.info("RESULTS:");
         logger.log(Level.INFO, String.format("- The test consumer received: %d notifications", testConsumer.getCurrentNotificationCount()));
         logger.log(Level.INFO, String.format("- The delivery latency was typically %d us", singleNotificationDeliveryLatencyInMicroseconds));
         logger.log(Level.INFO, String.format("- Synchronous PutAndMonitor with %d puts took %s ms. Average: %3f ms.", numberOfPuts, multipleNotificationDeliveryTimeInMilliseconds, (float) multipleNotificationDeliveryTimeInMilliseconds / (float) numberOfPuts));
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
   void TestFastCounterMonitor( String serviceImpl, int numberOfNotifications )
   {
      logger.log( Level.INFO, String.format("Starting FastCounterMonitor throughput test using impl: '%s'...", serviceImpl ) );
      final Properties contextProperties = new Properties();
      contextProperties.setProperty( "CA_MONITOR_NOTIFIER_IMPL", serviceImpl );

      try ( final Context context = new Context( contextProperties ) )
      {
         final Channel<Integer> channel = context.createChannel("fastCounter", Integer.class );
         channel.connect();
         final List<Monitor> monitorList = new ArrayList<>();

         // Can optionally set here the number of monitors that will simultaneously deliver
         // notifications to the CA library TCP/IP socket and thus explore the performance of
         // the system under increasing stress.
         final int numberOfMonitors = 1;
         for ( int i = 0; i < numberOfMonitors; i++ )
         {
            final TestConsumer<Integer> testConsumer = TestConsumer.getNormalConsumer();
            monitorList.add(channel.addValueMonitor(testConsumer));
         }

         final int totalNotificationCount = numberOfNotifications * numberOfMonitors;
         TestConsumer.setExpectedTotalNotificationCount(totalNotificationCount);
         TestConsumer.clearCurrentTotalNotificationCount();

         final StopWatch stopWatch = StopWatch.createStarted();
         TestConsumer.awaitExpectedTotalNotificationCount();
         final long elapsedTimeInMilliseconds = stopWatch.getTime(TimeUnit.MILLISECONDS);

         // Release references to monitor objects
         monitorList.forEach( Monitor::close );
         monitorList.clear();

         logger.info("RESULTS:");
         logger.log(Level.INFO, String.format("- The test consumer received: %d notifications", TestConsumer.getCurrentTotalNotificationCount()));
         logger.log(Level.INFO, String.format("- FastCounterMonitor with %d notifications took %s ms. Average: %3f ms.", totalNotificationCount, elapsedTimeInMilliseconds, (float) elapsedTimeInMilliseconds / (float) totalNotificationCount));
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

      return serviceImpls.stream().map( s -> numberOfPuts.stream().map( n -> Arguments.of(s, n) ) ).flatMap(s -> s);
   }

   /**
    * Provides the argument data for the specified test.
    * @return the data.
    */
   private static Stream<Arguments> getArgumentsForTestFastCounterMonitor()
   {
      final List<String> serviceImpls = MonitorNotificationServiceFactoryCreator.getAllServiceImplementations();
      final List<Integer> notifications = Arrays.asList( 100, 2000 );

      return serviceImpls.stream().map( s -> notifications.stream().map(n -> Arguments.of(s, n) ) ).flatMap( s -> s);
   }

/*- Nested Classes -----------------------------------------------------------*/

}
