/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.time.StopWatch;
import org.epics.ca.TestConsumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;


/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Tests all underlying implementations of the MonitorNotificationService.
 */
public class MonitorNotificationServiceTest
{
/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = Logger.getLogger( MonitorNotificationServiceTest.class.getName() );


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/

   @BeforeAll
   static void beforeAll()
   {
      System.setProperty( "java.util.logging.SimpleFormatter.format", "%1$tF %1$tT.%1$tL %4$s  %5$s%6$s%n");
      Locale.setDefault(Locale.ROOT );
   }

   @ParameterizedTest
   @MethodSource( "getMonitorNotificationServiceImplementations" )
   void testGetNotifierForConsumer_ThrowsNullPointerExceptionWhenConsumerNull( String serviceImpl )
   {
      assertThrows( NullPointerException.class, () -> new MonitorNotificationServiceFactory( serviceImpl ).getServiceForConsumer( null ) );
   }

   @ParameterizedTest
   @MethodSource( "getArgumentsForTestNotifyConsumerNullPublicationBehaviour" )
   void testNotifyConsumerNullPublicationBehaviour( String serviceImpl, boolean acceptsNullExpectation )
   {
      Validate.notNull( serviceImpl );

      logger.log( Level.INFO, String.format("Assessing null publication behaviour of service implementation: '%s'", serviceImpl ) );
      boolean acceptedNull = true;
      try
      {
         final TestConsumer<Long> consumer = TestConsumer.getNormalConsumer();
         final MonitorNotificationServiceFactory factory = new MonitorNotificationServiceFactory( serviceImpl );
         final MonitorNotificationService<Long> notifier = factory.getServiceForConsumer( consumer );
         notifier.publish( null );
      }
      catch( NullPointerException ex )
      {
         acceptedNull = false;
      }
      assertEquals( acceptsNullExpectation, acceptedNull );
      logger.log( Level.INFO, String.format( "The service implementation '%s' had the following null publication property: [accepts null=%b].\n", serviceImpl, acceptedNull ) );
   }

   @ParameterizedTest
   @MethodSource( "getArgumentsForTestThroughputWithSameConsumer" )
   public <T> void testThroughputWithSameConsumer( String serviceImpl, int notifications, T notifyValue1, T notifyValue2, TestConsumer.ConsumerType consumerType, int consumerProcessingTimeInMicroseconds )
   {
      Validate.notNull( serviceImpl );
      Validate.notNull( notifyValue1 );
      Validate.notNull( notifyValue2 );
      Validate.notNull( consumerType );

      final String notificationValueType = notifyValue1.getClass().getName();
      logger.log( Level.INFO, String.format( "Starting test with service implementation '%s', '%s' notifications, and notification value type: '%s'", serviceImpl, notifications, notificationValueType ) );
      assertTimeoutPreemptively( Duration.ofSeconds( 10 ), () ->
      {
         // Startup the service
         final TestConsumer<T> testConsumer = new TestConsumer<>( consumerType, consumerProcessingTimeInMicroseconds, TimeUnit.MICROSECONDS );
         final MonitorNotificationService<? super T> notifier = new MonitorNotificationServiceFactory( serviceImpl ).getServiceForConsumer( testConsumer );

         // Start the timer and send all the notifications but one
         // logger.info( "Sending {} notifications...", notifications );
         final StopWatch stopWatch = StopWatch.createStarted();
         for ( long notification = 0; notification < notifications - 1; notification++ )
         {
            if ( notifier.publish( notifyValue1 ) )
            {
               logger.log( Level.FINEST, String.format( " Value %s accepted - buffer OK", notifyValue1 ) );
            }
            else
            {
               logger.log( Level.FINEST, String.format( " Value %s accepted - buffer OVERRUN", notifyValue1 ) );
            }
         }

         // For the last notification send a different value, so that we can check it gets
         // notified correctly.
         testConsumer.setExpectedNotificationValue( notifyValue2 );
         if ( notifier.publish( notifyValue2 ) )
         {
            logger.log( Level.FINEST, String.format( " Value %s accepted - buffer OK", notifyValue2 ) );
         }
         else
         {
            logger.log( Level.FINEST, String.format( " Value %s accepted - buffer OVERRUN", notifyValue2 ) );
         }

         // Wait for the last notification. Poll relatively often so that it doesn't
         // perturb the timing measurement too much.
         testConsumer.awaitExpectedNotificationValue();

         final long elapsedTimeInMicroseconds = stopWatch.getTime(TimeUnit.MICROSECONDS);

         // Now shutdown the service for good housekeeping
         notifier.dispose();
         notifier.disposeAllResources();

         double averageNotificationTimeInMicroseconds = (double) elapsedTimeInMicroseconds / (double) notifications;
         double elapsedTimeInMilliseconds = (double) elapsedTimeInMicroseconds / 1000;
         double throughput = 1_000_000 / averageNotificationTimeInMicroseconds;
         logger.log( Level.INFO, String.format( "Time to send '%s' notification to a SINGLE Consumer was: %,.3f ms ", notifications, elapsedTimeInMilliseconds ) );
         logger.log( Level.INFO, String.format( "Average notification time was: %,.3f us ", averageNotificationTimeInMicroseconds) );
         logger.log( Level.INFO, String.format( "Throughput was: %,.0f notifications per second.\n", throughput ) );
      } );
   }

   /**
    * Measures the throughput performance when sending a variable number of notifications of different value types
    * to distinct consumers of with different processing times.
    *
    * @param serviceImpl the service implementation.
    * @param notifications the number of notifications to be sent.
    * @param notifyValue the notification value.
    * @param consumerProcessingTimeInMicroseconds the simulated consumer processing time.
    * @param <T> the notification type.
    */
   @ParameterizedTest
   @MethodSource( "getArgumentsForTestThroughputWithDifferentConsumers" )
   public <T> void testThroughputWithDifferentConsumers( String serviceImpl, int notifications, T notifyValue, TestConsumer.ConsumerType consumerType, int consumerProcessingTimeInMicroseconds )
   {
      Validate.notNull( serviceImpl );
      Validate.notNull( notifyValue );
      Validate.notNull( consumerType );

      final String notificationValueType = notifyValue.getClass().getName();
      logger.log( Level.INFO, String.format( "Starting test with service implementation '%s', '%s' notifications, and notification value type: '%s'", serviceImpl, notifications, notificationValueType ) );

      assertTimeoutPreemptively( Duration.ofSeconds( 120 ), () ->
      {
        final MonitorNotificationServiceFactory factory = new MonitorNotificationServiceFactory( serviceImpl );

        // Set the expectation on how many notifications should be received. Since even the lossy
        // monitor notification serviceImpls must notify each DISTINCT consumer we can simply wait
        // for each consumer to notify.
        TestConsumer.clearCurrentTotalNotificationCount();
        TestConsumer.setExpectedTotalNotificationCount( notifications );

        // Start the stopwatch and send all the notifications
        logger.log( Level.FINEST, String.format( "Sending '%s' notifications with value: '%s' ", notifications, notifyValue ) );
        MonitorNotificationService<T> notifier = null;
        final StopWatch stopWatch = StopWatch.createStarted();
        for ( long notification = 0; notification < notifications; notification++ )
        {
           final TestConsumer<T> testConsumer = new TestConsumer<>( consumerType, consumerProcessingTimeInMicroseconds,TimeUnit.MICROSECONDS );
           notifier = factory.getServiceForConsumer( testConsumer );
           if ( ! notifier.publish( notifyValue) )
           {
              logger.log( Level.INFO, String.format( "Value was dropped: %s", notifyValue ) );
           }
        }

        TestConsumer.awaitExpectedTotalNotificationCount();
        final long elapsedTimeInMicroseconds = stopWatch.getTime( TimeUnit.MICROSECONDS );

         // Check that the expected number of notification events were received from the consumers
         assertEquals( notifications, TestConsumer.getCurrentTotalNotificationCount(), "unexpected number of notifications received" );

         // Now shutdown the service for good housekeeping
         notifier.dispose();
         notifier.disposeAllResources();

        final double averageNotificationTimeInMicroseconds = (double) elapsedTimeInMicroseconds / (double) notifications;
        final double elapsedTimeInMilliseconds = (double) elapsedTimeInMicroseconds / 1000;
        final double throughput = 1_000_000 / averageNotificationTimeInMicroseconds;

        // The theoretical limit is based on there being an sufficient CPU cores to devote themeslved exclusively to the number of processing threads.
        final int processingThreads = factory.getServiceForConsumer( TestConsumer.getNormalConsumer() ).getQualityOfServiceNumberOfNotificationThreadsPerConsumer();
        final double theoreticalThroughputLimit =  (double) 1_000_000 / ( (double) consumerProcessingTimeInMicroseconds / (double) processingThreads );

        // The CPU bound theoretical limit takes into account the fact that there may be less CPU cores than processing threads so in the situation
        // that the consumer requires heavy processing there may not be enough CPU time available.
        final int cpuCores = Runtime.getRuntime().availableProcessors();
        logger.log( Level.FINEST, String.format( "CPU Cores are %d", cpuCores ) );

        final int availableThreads = Math.min( cpuCores, processingThreads );
        logger.log( Level.FINEST, String.format( "Available threads are %d", availableThreads ) );
        final double cpuBoundedThroughputLimitOnThisMachine =  (double) 1_000_000 / ( (double) consumerProcessingTimeInMicroseconds / (double) availableThreads );

        final TestConsumer<Long> exampleConsumer = TestConsumer.getBusyWaitingSlowConsumer( consumerProcessingTimeInMicroseconds, TimeUnit.MICROSECONDS );
        logger.log( Level.INFO, String.format( "Service Implementation '%s' took '%,.3f' ms to send %,d notifications of value type '%s' to DISTINCT consumers of type '%s'", serviceImpl, elapsedTimeInMilliseconds, notifications, notificationValueType, exampleConsumer ) );
        logger.log( Level.INFO, String.format( "Average notification time was: '%,.3f' us ", averageNotificationTimeInMicroseconds ) );
        logger.log( Level.INFO, String.format( "Throughput was: '%,.0f' notifications per second.", throughput ) );
        logger.log( Level.INFO, String.format( "Theoretical limits were: theoretical / cpuBoundOnThisMachine '%,.0f' / '%,.0f' notifications per second.\n", theoreticalThroughputLimit, cpuBoundedThroughputLimitOnThisMachine ) );
      } );
   }

   /**
    * This test verifies the blocking behaviour for Consumers that take a long time to process notification
    * requests. The goal is that a single slow consumer cannot block others.
    *
    * @param serviceImpl the service implementation.
    * @param slowConsumerDelayTimeInMillis the required processing time for the slow consumer.
    * @param otherConsumerMinNotifyTimeInMillis the minimum time in which the other consumer should be notified.
    * @param otherConsumerMaxNotifyTimeInMillis the maximum time in which the other consumer should be notified.
    */
   @ParameterizedTest
   @MethodSource( "getArgumentsForTestSlowConsumerBlockingBehaviour" )
   void testSlowConsumerBlockingBehaviour( String serviceImpl, long slowConsumerDelayTimeInMillis, long otherConsumerMinNotifyTimeInMillis, long otherConsumerMaxNotifyTimeInMillis )
   {
      Validate.notNull( serviceImpl );

      logger.log( Level.INFO, String.format( "Starting test with MonitorNotifier configuration '%s'", serviceImpl ) );
      assertTimeoutPreemptively( Duration.ofSeconds( 10 ), () ->
      {
         // Create in advance the parameters for the test
         final Long slowConsumerValue = 123L;
         final Long otherConsumerValue = 456L;

         final TestConsumer<Long> slowConsumer = TestConsumer.getThreadSleepingSlowConsumer( slowConsumerDelayTimeInMillis, TimeUnit.MILLISECONDS );
         final TestConsumer<Long> otherConsumer = TestConsumer.getNormalConsumer();
         final MonitorNotificationServiceFactory factory = new MonitorNotificationServiceFactory( serviceImpl );
         final MonitorNotificationService<Long> slowConsumerNotifier = factory.getServiceForConsumer( slowConsumer );
         final MonitorNotificationService<Long> otherConsumerNotifier = factory.getServiceForConsumer( otherConsumer );

         slowConsumer.setExpectedNotificationCount( 1 );
         slowConsumer.setExpectedNotificationValue( slowConsumerValue );
         otherConsumer.setExpectedNotificationCount( 1 );
         otherConsumer.setExpectedNotificationValue( otherConsumerValue );

         // Start the stopwatch
         final StopWatch stopWatch = StopWatch.createStarted();

         // Publish a value to both the slow consumer and the other one
         if ( ! slowConsumerNotifier.publish( slowConsumerValue ))
         {
            logger.log( Level.INFO, String.format( "Value was dropped: %s", slowConsumerValue ) );
         }

         if ( ! otherConsumerNotifier.publish( otherConsumerValue ))
         {
            logger.log( Level.INFO, String.format( "Value was dropped: %s", otherConsumerValue ) );
         }

         // Record the elapsed time when the normal consumer sees the expected value
         otherConsumer.awaitExpectedNotificationCount();
         otherConsumer.awaitExpectedNotificationValue();
         final long otherConsumerNotifyTimeInMicroseconds = stopWatch.getTime( TimeUnit.MICROSECONDS );

         // Record the elapsed time when the slow consumer sees the expected value
         slowConsumer.awaitExpectedNotificationCount();
         slowConsumer.awaitExpectedNotificationValue();
         final long slowConsumerNotifyTimeInMicroseconds = stopWatch.getTime( TimeUnit.MICROSECONDS );

         // Check that the notification arrived in the expected time frame.
         assertTrue(otherConsumerNotifyTimeInMicroseconds >= otherConsumerMinNotifyTimeInMillis * 1000,
                     String.format( "%s greater than %s ", otherConsumerNotifyTimeInMicroseconds, otherConsumerMinNotifyTimeInMillis * 1000) );

         assertTrue(otherConsumerNotifyTimeInMicroseconds < otherConsumerMaxNotifyTimeInMillis * 1000,
                     String.format( "%s less than %s ", otherConsumerNotifyTimeInMicroseconds, otherConsumerMaxNotifyTimeInMillis * 1000) );

         assertTrue(slowConsumerNotifyTimeInMicroseconds >= slowConsumerDelayTimeInMillis * 1000,
                     String.format( "%s greater than %s ", slowConsumerNotifyTimeInMicroseconds, slowConsumerDelayTimeInMillis * 1000) );

         logger.log( Level.INFO, String.format( "Slow consumer was notified in %,d us.",  slowConsumerNotifyTimeInMicroseconds ) );
         logger.log( Level.INFO, String.format( "Other consumer was notified in %,d us.", otherConsumerNotifyTimeInMicroseconds ) );

         final boolean serviceIsBlocking = otherConsumerNotifyTimeInMicroseconds >= slowConsumerDelayTimeInMillis * 1000;
         logger.log( Level.INFO, String.format( "The service implementation '%s' had the following notification properties: [blocking=%b].\n", serviceImpl, serviceIsBlocking ) );
      } );
   }

   /**
    * This test publishes an increasing count to the SAME consumer and checks whether the service implementation
    * delivers notifications to the consumer with the expected degree of monotonicity and consecutiveness.
    *
    * @param serviceImpl the service implementation.
    * @param lastNotificationValue the last notification value to send in the sequence that starts at 1.
    * @param expectedMonotonic the expectation on whether the service implementation will deliver notifications monotonically.
    * @param expectedConsecutive the expectation on whether the service implementation will deliver notifications consecutively.
    */
   @ParameterizedTest
   @MethodSource( {"getTestConsumerDeliverySequenceArgs"})
   void testConsumerDeliverySequence( String serviceImpl, long lastNotificationValue, boolean expectedMonotonic, boolean expectedConsecutive  )
   {
      Validate.notNull( serviceImpl );

      logger.log( Level.INFO, String.format( "Starting test with service implementation '%s', lastNotificationValue '%,d'", serviceImpl, lastNotificationValue ) );
      assertTimeoutPreemptively( Duration.ofSeconds( 10 ), () ->
      {
         final TestConsumer<Long> testConsumer = TestConsumer.getNormalConsumer();
         final MonitorNotificationServiceFactory factory = new MonitorNotificationServiceFactory( serviceImpl );
         final MonitorNotificationService<? super Long> consumerNotifier = factory.getServiceForConsumer( testConsumer );

         // Now arm the TestConsumer detectors and send all other values in sequence
         testConsumer.setExpectedNotificationValue( lastNotificationValue );
         testConsumer.setNotificationSequenceWasMonotonic();
         testConsumer.setNotificationSequenceWasConsecutive();
         for ( long i = 1; i <= lastNotificationValue; i++ )
         {
            logger.log( Level.FINEST, String.format( "Publishing: %d", i ) );
            if ( ! consumerNotifier.publish( i ) )
            {
               logger.log( Level.INFO, String.format( "Value was dropped: %s", i ) );
            }

         }
         testConsumer.awaitExpectedNotificationValue();

         // Check the service implementation meets the expectations on notification monotonictity and sequentiality
         final boolean serviceIsMonotonic = testConsumer.getNotificationSequenceWasMonotonic();
         final boolean serviceIsConsecutive = testConsumer.getNotificationSequenceWasConsecutive();

         // Only check for monotonicity if we expect this property to be set. This avoids
         // false failures in the case where the service delivers a monotonic result by chance.
         if ( expectedMonotonic )
         {
            assertTrue( serviceIsMonotonic, "monotonic expectation not met");
         }

         // Only check for consecutiveness if we expect this property to be set. This avoids
         // false failures in the case where the service delivers a consecutive result by chance.
         if ( expectedConsecutive )
         {
            assertTrue( serviceIsConsecutive, "consecutive expectation not met");
         }
         logger.log( Level.INFO, String.format( "The service implementation '%s' had the following notification properties: [monotonic=%b, consecutive=%b].", serviceImpl, serviceIsMonotonic, serviceIsConsecutive ) );
      } );
   }

   @ParameterizedTest
   @ValueSource( strings={ "BlockingQueueSingleWorkerMonitorNotificationServiceImpl,1,2",
                           "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl,4,2",
                           "DisruptorOldMonitorNotificationServiceImpl",
                           "DisruptorNewMonitorNotificationServiceImpl",
                           "StripedExecutorServiceMonitorNotificationServiceImpl" } )
   void testBufferOverrunConsumerLastValueAlwaysGetsSent( String serviceImpl  )
   {
      logger.log( Level.INFO, String.format( "Starting test with service implementation '%s'", serviceImpl ) );
      assertTimeoutPreemptively( Duration.ofSeconds( 5 ), () ->
      {
         final TestConsumer<Long> testConsumer = TestConsumer.getBusyWaitingSlowConsumer( 1, TimeUnit.SECONDS );
         final MonitorNotificationServiceFactory factory = new MonitorNotificationServiceFactory( serviceImpl );
         final MonitorNotificationService<? super Long> consumerNotifier = factory.getServiceForConsumer( testConsumer );

         for ( long i = 1; i <= 1000; i++ )
         {
            testConsumer.setExpectedNotificationValue( i );
            logger.log( Level.FINEST, String.format( "Publishing: %d", i ) );

            if ( consumerNotifier.publish( i ) )
            {
               logger.log( Level.FINEST, String.format( " Value %s accepted - buffer OK", i ) );
            }
            else
            {
               logger.log( Level.FINEST, String.format( " Value %s accepted - buffer OVERRUN", i ) );
               testConsumer.awaitExpectedNotificationValue();
               break;
            }
         }
      } );

   }

/*- Private methods ----------------------------------------------------------*/

   // Provides a possible test method source to iterate test over all service implementations
   private static Stream<Arguments> getMonitorNotificationServiceImplementations()
   {
      final List<String> allConfigurations = MonitorNotificationServiceFactory.getAllServiceImplementations();
      return allConfigurations.stream().map(Arguments::of);
   }

   /**
    * Provides the argument data for the specified test.
    * @return the data.
    */
   private static Stream<Arguments> getArgumentsForTestThroughputWithSameConsumer()
   {
      final String aStr = "This is really quite a long string that goes on and on for several tens of characters";
      Integer[] arry = new Integer[ 10 ];

      // Currently (2018-07-24) the build fails with the tests on the Disruptor. To prevent breaking
      // the build they have been removed for the moment.
      final List<String> disabledServiceImpls = Arrays.asList( "DisruptorOldMonitorNotificationServiceImpl",
                                                               "DisruptorNewMonitorNotificationServiceImpl");

      final List<String> allServiceImpls = Arrays.asList( "BlockingQueueSingleWorkerMonitorNotificationServiceImpl",
                                                          "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl",
                                                          "StripedExecutorServiceMonitorNotificationServiceImpl");

      // Note: the final value should be of a Type where equals gives an unequivocal answer (floating point values
      // would not be a good choice !)
      return Stream.of( allServiceImpls.stream().map( s -> Arguments.of( s, 10_000, 1234, 9999,  TestConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ),
                        allServiceImpls.stream().map( s -> Arguments.of( s, 10_000, 567L, 9999,  TestConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ),
                        allServiceImpls.stream().map( s -> Arguments.of( s, 10_000, 8.9f, 9999,  TestConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ),
                        allServiceImpls.stream().map( s -> Arguments.of( s, 10_000, 1.2d, 9999,  TestConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ),
                        allServiceImpls.stream().map( s -> Arguments.of( s, 10_000, aStr, 9999,  TestConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ),
                        allServiceImpls.stream().map( s -> Arguments.of( s, 10_000, arry, 9999,  TestConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ) ).flatMap(s -> s);
   }

   /**
    * Provides the argument data for the specified test.
    * @return the data.
    */
   private static Stream<Arguments> getArgumentsForTestThroughputWithDifferentConsumers()
   {
      // Currently (2018-07-24) the build fails with the tests on the Disruptor. To prevent breaking
      // the build they have been removed for the moment.
      final List<String> disabledServiceImpls = Arrays.asList( "DisruptorOldMonitorNotificationServiceImpl",
                                                               "DisruptorNewMonitorNotificationServiceImpl");

      final List<String> allServiceImpls = Arrays.asList( "BlockingQueueSingleWorkerMonitorNotificationServiceImpl",
                                                          "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl",
                                                          "StripedExecutorServiceMonitorNotificationServiceImpl");

      final String aStr = "This is really quite a long string that goes on and on for several tens of characters";
      final Integer[] arry = new Integer[ 1000 ];
      return Stream.of( allServiceImpls.stream().map( s -> Arguments.of( s, 1000, 1234, TestConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ),
                        allServiceImpls.stream().map( s -> Arguments.of( s, 1000, 567L, TestConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ),
                        allServiceImpls.stream().map( s -> Arguments.of( s, 1000, 8.9f, TestConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ),
                        allServiceImpls.stream().map( s -> Arguments.of( s, 1000, 1.2d, TestConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ),
                        allServiceImpls.stream().map( s -> Arguments.of( s, 1000, aStr, TestConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ),
                        allServiceImpls.stream().map( s -> Arguments.of( s, 1000, arry, TestConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ) ).flatMap(s -> s);
   }


   /**
    * Provides the argument data for the specified test.
    * @return the data.
    */
   private static Stream<Arguments> getArgumentsForTestNotifyConsumerNullPublicationBehaviour()
   {
      // Summary:
      // The Disruptor implementation allows null values to be sent. The new implementations do not support this.
      return Stream.of( Arguments.of( "BlockingQueueSingleWorkerMonitorNotificationServiceImpl",   false ),
                        Arguments.of( "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl", false ),
                        Arguments.of( "DisruptorOldMonitorNotificationServiceImpl",                true  ),
                        Arguments.of( "DisruptorNewMonitorNotificationServiceImpl",                true  ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl",      false ) );
   }

   /**
    * Provides the argument data for the specified test.
    * @return the data.
    */
   private static Stream<Arguments> getTestConsumerDeliverySequenceArgs()
   {
      // Perform tests on all service implementations
      return Stream.of( Arguments.of( "BlockingQueueSingleWorkerMonitorNotificationServiceImpl",   100_000L, true, true  ),
                        Arguments.of( "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl", 100_000L, true, true  ),
                        Arguments.of( "DisruptorOldMonitorNotificationServiceImpl",                100_000L, true, false ),
                        Arguments.of( "DisruptorNewMonitorNotificationServiceImpl",                100_000L, true, false ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl",      100_000L, true, true  ) );
   }


   /**
    * Provides the argument data for the specified test.
    * @return the data.
    */
   private static Stream<Arguments> getArgumentsForTestSlowConsumerBlockingBehaviour()
   {
      // Summary:
      // Perform tests on all service implementations
      // The slow consumer will take 200ms to process incoming notifications.
      // The BlockingQueueSingleWorkerMonitorNotificationServiceImpl is expected to block other consumers.
      // All other service implementations should notify the other consumers almost immediately (say within 10 millis).
      return Stream.of( Arguments.of( "BlockingQueueSingleWorkerMonitorNotificationServiceImpl",   200, 200, 210 ),
                        Arguments.of( "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl", 200,   0,  10 ),
                        Arguments.of( "DisruptorOldMonitorNotificationServiceImpl",                200,   0,  10 ),
                        Arguments.of( "DisruptorNewMonitorNotificationServiceImpl",                200,   0,  10 ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl",      200,   0,  10 ) );
   }

/*- Nested Classes -----------------------------------------------------------*/

}
