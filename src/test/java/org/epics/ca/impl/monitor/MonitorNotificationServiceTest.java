/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.time.StopWatch;
import org.epics.ca.NotificationConsumer;
import org.epics.ca.ThreadWatcher;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
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

   private static final Logger logger = LibraryLogManager.getLogger( MonitorNotificationServiceTest.class );

   private ThreadWatcher threadWatcher;


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/

   @BeforeEach
   void beforeEach()
   {
      threadWatcher = ThreadWatcher.start();
   }

   @AfterEach
   void afterEach()
   {
      threadWatcher.verify();
   }

   @ParameterizedTest
   @MethodSource( "getMonitorNotificationServiceImplementations" )
   void testGetNotifierForConsumer_ThrowsNullPointerExceptionWhenConsumerNull( String serviceImpl )
   {
      try ( final MonitorNotificationServiceFactory factory = MonitorNotificationServiceFactoryCreator.create( serviceImpl ) )
      {
         assertThrows( NullPointerException.class, () -> factory.getServiceForConsumer( null) );
      }   
   }

   @ParameterizedTest
   @MethodSource( "getArgumentsForTestNotifyConsumerNullPublicationBehaviour" )
   void testNotifyConsumerNullPublicationBehaviour( String serviceImpl, boolean acceptsNullExpectation )
   {
      Validate.notNull( serviceImpl );
      logger.info( String.format("Assessing null publication behaviour of service implementation: '%s'", serviceImpl ) );

      boolean nullWasAccepted = true;
      final boolean nullAcceptanceWasAdvertised;
      try ( final MonitorNotificationServiceFactory factory = MonitorNotificationServiceFactoryCreator.create( serviceImpl ) )
      {
         nullAcceptanceWasAdvertised = factory.getQosMetricIsNullPublishable();

         final MonitorNotificationService<Long> notifier;
         final NotificationConsumer<Long> consumer = NotificationConsumer.getNormalConsumer();
         notifier = factory.getServiceForConsumer( consumer );
         try
         {
            notifier.publish(null );
         }
         catch ( NullPointerException ex )
         {
            nullWasAccepted = false;
         }
      }
      assertEquals( acceptsNullExpectation, nullWasAccepted );
      assertEquals( acceptsNullExpectation, nullAcceptanceWasAdvertised );
      logger.info( String.format( "The service implementation '%s' had the following null publication property: [advertises null acceptance = %b; accepts null=%b].\n", serviceImpl, nullAcceptanceWasAdvertised, nullWasAccepted ) );
   }

   @ParameterizedTest
   @MethodSource( "getArgumentsForTestThroughputUntilLastValueReceived" )
   public <T> void testThroughputUntilLastValueReceived( String serviceImpl, int notifications, T notifyValue, T lastNotifyValue, NotificationConsumer.ConsumerType consumerType, int consumerProcessingTimeInMicroseconds )
   {
      Validate.notNull( serviceImpl );
      Validate.notNull( notifyValue );
      Validate.notNull( lastNotifyValue );
      Validate.notNull( consumerType );

      final String notificationValueType = notifyValue.getClass().getName();
      logger.info( String.format( "Starting test with service implementation '%s', '%s' notifications, and notification value type: '%s'", serviceImpl, notifications, notificationValueType ) );

      assertTimeout( Duration.ofSeconds( 30 ), () ->
      {
         final long elapsedTimeInMicroseconds;
         try ( final MonitorNotificationServiceFactory factory = MonitorNotificationServiceFactoryCreator.create( serviceImpl ) )
         {
            // Startup the service
            final NotificationConsumer<T> notificationConsumer = new NotificationConsumer<>( consumerType, consumerProcessingTimeInMicroseconds, TimeUnit.MICROSECONDS );
            final MonitorNotificationService<? super T> notifier = factory.getServiceForConsumer( notificationConsumer);

            // Start the timer and send all the notifications but one
            // logger.info( "Sending {} notifications...", notifications );
            final StopWatch stopWatch = StopWatch.createStarted();
            for ( long notification = 0; notification < notifications - 1; notification++ )
            {
               if ( notifier.publish( notifyValue) )
               {
                  logger.finest( String.format( "Value %s accepted - buffer OK", notifyValue));
               }
               else
               {
                  logger.finest( String.format( "Value %s accepted - buffer OVERRUN", notifyValue));
               }
            }

            // For the last notification send a different value, so that we can check it gets
            // notified correctly.
            notificationConsumer.setExpectedNotificationValue( lastNotifyValue );
            if ( notifier.publish( lastNotifyValue ) )
            {
               logger.finest( String.format( "Value %s accepted - buffer OK", lastNotifyValue ) );
            }
            else
            {
               logger.warning( String.format( "Value %s accepted - buffer OVERRUN - oldest value DISCARDED", lastNotifyValue ) );
            }

            // Wait for the last notification. Poll relatively often so that it doesn't
            // perturb the timing measurement too much.
            notificationConsumer.awaitExpectedNotificationValue();
            elapsedTimeInMicroseconds = stopWatch.getTime( TimeUnit.MICROSECONDS );
         } 

         final double averageNotificationTimeInMicroseconds = (double) elapsedTimeInMicroseconds / (double) notifications;
         final double elapsedTimeInMilliseconds = (double) elapsedTimeInMicroseconds / 1000;
         final double throughput = 1_000_000 / averageNotificationTimeInMicroseconds;
         logger.info( String.format( "Time to send '%s' notification to a SINGLE Consumer was: %,.3f ms ", notifications, elapsedTimeInMilliseconds ) );
         logger.info( String.format( "Average notification time was: %,.3f us ", averageNotificationTimeInMicroseconds) );
         logger.info( String.format( "Throughput was: %,.0f notifications per second.\n", throughput ) );
      });
   }

   /**
    * Measures the throughput performance of different service implementations when sending a variable
    * number of notifications of different value types to distinct consumers with different processing times.
    *
    * @param serviceImpl the service implementation.
    * @param notifications the number of notifications to be sent.
    * @param notifyValue the notification value.
    * @param consumerProcessingTimeInMicroseconds the simulated consumer processing time.
    * @param <T> the notification type.
    */
   @ParameterizedTest
   @MethodSource( "getArgumentsForTestThroughputUntilExpectedNotificationCountReceived" )
   public <T> void testThroughputUntilExpectedNotificationCountReceived( String serviceImpl, int notifications, T notifyValue, NotificationConsumer.ConsumerType consumerType, int consumerProcessingTimeInMicroseconds )
   {
      Validate.notNull( serviceImpl );
      Validate.notNull( notifyValue );
      Validate.notNull( consumerType );

      final String notificationValueType = notifyValue.getClass().getSimpleName();
      logger.info( String.format( "Starting test with service implementation '%s', '%s' notifications, and notification value type: '%s'", serviceImpl, notifications, notificationValueType ) );

      assertTimeout( Duration.ofSeconds( 120 ), () ->
      {
         final long elapsedTimeInMicroseconds;
         final int numberOfNotificationThreadsPerConsumerAsAdvertised;
         try ( final MonitorNotificationServiceFactory factory = MonitorNotificationServiceFactoryCreator.create( serviceImpl ) )
         {
            numberOfNotificationThreadsPerConsumerAsAdvertised = factory.getQosMetricNumberOfNotificationThreadsPerConsumer();

            // Set the expectation on how many notifications should be received. Since even the lossy
            // monitor notification serviceImpls must notify each DISTINCT consumer we can simply wait
            // for each consumer to notify.
            NotificationConsumer.clearCurrentTotalNotificationCount();
            NotificationConsumer.setExpectedTotalNotificationCount( notifications );

            // Start the stopwatch and send all the notifications
            logger.finest( String.format("Sending '%s' notifications with value: '%s' ", notifications, notifyValue));
            MonitorNotificationService<T> notifier;
            final StopWatch stopWatch = StopWatch.createStarted();
            for ( long notification = 0; notification < notifications; notification++ )
            {
               final NotificationConsumer<T> notificationConsumer = new NotificationConsumer<>( consumerType, consumerProcessingTimeInMicroseconds, TimeUnit.MICROSECONDS);
               notifier = factory.getServiceForConsumer(notificationConsumer);
               if ( !notifier.publish( notifyValue ) )
               {
                  logger.info( String.format( "Value was dropped: %s", notifyValue ) );
               }
            }

            NotificationConsumer.awaitExpectedTotalNotificationCount();
            elapsedTimeInMicroseconds = stopWatch.getTime(TimeUnit.MICROSECONDS);

            // Check that the expected number of notification events were received from the consumers
            assertEquals( notifications, NotificationConsumer.getCurrentTotalNotificationCount(), "unexpected number of notifications received");
         }

         final double averageNotificationTimeInMicroseconds = (double) elapsedTimeInMicroseconds / (double) notifications;
         final double elapsedTimeInMilliseconds = (double) elapsedTimeInMicroseconds / 1000;
         final double throughput = 1_000_000 / averageNotificationTimeInMicroseconds;

         // The theoretical limit is based on there being an sufficient CPU cores to devote themeslved exclusively to the number of processing threads.
         final double theoreticalThroughputLimit =  (double) 1_000_000 / ( (double) consumerProcessingTimeInMicroseconds / (double) numberOfNotificationThreadsPerConsumerAsAdvertised );

         // The CPU bound theoretical limit takes into account the fact that there may be less CPU cores than processing threads so in the situation
         // that the consumer requires heavy processing there may not be enough CPU time available.
         final int cpuCores = Runtime.getRuntime().availableProcessors();
         logger.finest(  String.format( "CPU Cores are %d", cpuCores ) );

         final int availableThreads = Math.min( cpuCores, numberOfNotificationThreadsPerConsumerAsAdvertised );
         logger.finest(  String.format( "Available threads are %d", availableThreads ) );
         final double cpuBoundedThroughputLimitOnThisMachine =  (double) 1_000_000 / ( (double) consumerProcessingTimeInMicroseconds / (double) availableThreads );

         final NotificationConsumer<Long> exampleConsumer = NotificationConsumer.getBusyWaitingSlowConsumer(consumerProcessingTimeInMicroseconds, TimeUnit.MICROSECONDS );
         logger.info( String.format( "Service Implementation '%s' took '%,.3f' ms to send %,d notifications of value type '%s' to DISTINCT consumers of type '%s'", serviceImpl, elapsedTimeInMilliseconds, notifications, notificationValueType, exampleConsumer ) );
         logger.info( String.format( "Average notification time was: '%,.3f' us ", averageNotificationTimeInMicroseconds ) );
         logger.info( String.format( "Throughput was: '%,.0f' notifications per second.", throughput ) );
         logger.info( String.format( "Theoretical limits were: theoretical / cpuBoundOnThisMachine '%,.0f' / '%,.0f' notifications per second.\n", theoreticalThroughputLimit, cpuBoundedThroughputLimitOnThisMachine ) );
      } );

   }

   /**
    * This test verifies the blocking behaviour for Consumers that take a long time to process notification
    * requests. The goal is that a single slow consumer cannot block others.
    *
    * @param serviceImpl the service implementation.
    * @param slowConsumerDelayTimeInMillis the required processing time for the slow consumer.
    * @param slowConsumerMinNotifyTimeInMillis the minimum time in which the slow consumer should be notified.
    * @param slowConsumerMaxNotifyTimeInMillis the maximum time in which the slow consumer should be notified.
    * @param normalConsumerMinNotifyTimeInMillis the minimum time in which the normal consumer should be notified.
    * @param normalConsumerMaxNotifyTimeInMillis the maximum time in which the normal consumer should be notified.
    */
   @ParameterizedTest
   @MethodSource( "getArgumentsForTestSlowConsumerBlockingBehaviour" )
   void testSlowConsumerBlockingBehaviour( String serviceImpl, long slowConsumerDelayTimeInMillis,
                                           long slowConsumerMinNotifyTimeInMillis, long slowConsumerMaxNotifyTimeInMillis,
                                           long normalConsumerMinNotifyTimeInMillis, long normalConsumerMaxNotifyTimeInMillis )
   {
      Validate.notNull( serviceImpl );

      logger.info( String.format( "Starting test with service implementation '%s', delay='%d', minNotify='%d', maxNotify='%d'",
              serviceImpl, slowConsumerDelayTimeInMillis,  normalConsumerMinNotifyTimeInMillis, normalConsumerMaxNotifyTimeInMillis ) );
      assertTimeout( Duration.ofSeconds( 10 ), () ->
      {
         // Create in advance the parameters for the test
         final Long slowConsumerValue = 123L;
         final Long normalConsumerValue = 456L;

         final long normalConsumerNotifyTimeInMilliseconds;
         final long slowConsumerNotifyTimeInMilliseconds;
         try ( final MonitorNotificationServiceFactory factory = MonitorNotificationServiceFactoryCreator.create( serviceImpl ) )
         {
            final NotificationConsumer<Long> slowConsumer = NotificationConsumer.getThreadSleepingSlowConsumer( slowConsumerDelayTimeInMillis, TimeUnit.MILLISECONDS );
            final NotificationConsumer<Long> normalConsumer = NotificationConsumer.getNormalConsumer();
            final MonitorNotificationService<Long> slowConsumerNotifier = factory.getServiceForConsumer( slowConsumer );
            final MonitorNotificationService<Long> normalConsumerNotifier = factory.getServiceForConsumer( normalConsumer );

            slowConsumer.setExpectedNotificationCount( 1 );
            slowConsumer.setExpectedNotificationValue( slowConsumerValue );
            normalConsumer.setExpectedNotificationCount( 1 );
            normalConsumer.setExpectedNotificationValue( normalConsumerValue );

            // Start the stopwatch
            final StopWatch stopWatch = StopWatch.createStarted();

            // Publish FIRSTLY a value to the slow consumer.
            if ( ! slowConsumerNotifier.publish( slowConsumerValue) )
            {
               logger.info( String.format( "Value was dropped: %s", slowConsumerValue ) );
            }

            // Publish SECONDLY a value to the normal consumer.
            if ( ! normalConsumerNotifier.publish( normalConsumerValue ) )
            {
               logger.info( String.format( "Value was dropped: %s", normalConsumerValue ) );
            }

            // Record the elapsed time when the normal consumer sees the notification of the expected value.
            // In the case of a non-blocking service implementation this will arrive before the the slow
            // consumer sees their own notification.
            normalConsumer.awaitExpectedNotificationCount();
            normalConsumer.awaitExpectedNotificationValue();
            normalConsumerNotifyTimeInMilliseconds = stopWatch.getTime( TimeUnit.MILLISECONDS );

            // Record the elapsed time when the slow consumer sees the expected value.
            slowConsumer.awaitExpectedNotificationCount();
            slowConsumer.awaitExpectedNotificationValue();
            slowConsumerNotifyTimeInMilliseconds = stopWatch.getTime( TimeUnit.MILLISECONDS );
         }

         logger.info( String.format( "Slow consumer was notified in %,d ms.", slowConsumerNotifyTimeInMilliseconds ) );
         logger.info( String.format( "Normal consumer was notified in %,d ms.", normalConsumerNotifyTimeInMilliseconds ) );

         // Check that the notification arrived in the expected time frame.
         assertTrue(normalConsumerNotifyTimeInMilliseconds >= normalConsumerMinNotifyTimeInMillis,
                    String.format( "Normal Consumer: notify time: '%s' greater than '%s'", normalConsumerNotifyTimeInMilliseconds, normalConsumerMinNotifyTimeInMillis ) );

         assertTrue(normalConsumerNotifyTimeInMilliseconds < normalConsumerMaxNotifyTimeInMillis,
                    String.format( "Normal Consumer:notify time: '%s' less than '%s'", normalConsumerNotifyTimeInMilliseconds, normalConsumerMaxNotifyTimeInMillis ) );

         assertTrue(slowConsumerNotifyTimeInMilliseconds >= slowConsumerMinNotifyTimeInMillis,
                 String.format( "Slow Consumer: notify time: '%s' greater than '%s'", slowConsumerNotifyTimeInMilliseconds, slowConsumerMinNotifyTimeInMillis ) );

         assertTrue(slowConsumerNotifyTimeInMilliseconds < slowConsumerMaxNotifyTimeInMillis,
                 String.format( "Slow Consumer:notify time: '%s' less than '%s'", slowConsumerNotifyTimeInMilliseconds, slowConsumerMaxNotifyTimeInMillis ) );

         final boolean serviceIsBlocking = normalConsumerNotifyTimeInMilliseconds >= slowConsumerDelayTimeInMillis;
         logger.info( String.format("The service implementation '%s' had the following notification properties: [blocking=%b].\n", serviceImpl, serviceIsBlocking));
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

      logger.info( String.format( "Starting test with service implementation '%s', lastNotificationValue '%,d'", serviceImpl, lastNotificationValue ) );
      assertTimeout( Duration.ofSeconds( 30 ), () ->
      {
         final NotificationConsumer<Long> notificationConsumer = NotificationConsumer.getNormalConsumer();
         try ( final MonitorNotificationServiceFactory factory = MonitorNotificationServiceFactoryCreator.create( serviceImpl ) )
         {
            final MonitorNotificationService<? super Long> consumerNotifier = factory.getServiceForConsumer( notificationConsumer );

            // Now arm the TestConsumer detectors and send all other values in sequence
            notificationConsumer.setExpectedNotificationValue( lastNotificationValue );
            notificationConsumer.setNotificationSequenceWasMonotonic();
            notificationConsumer.setNotificationSequenceWasConsecutive();
            for ( long i = 1; i <= lastNotificationValue; i++ )
            {
               logger.finest( "Publishing: " + i );
               if ( ! consumerNotifier.publish( i ) )
               {
                  logger.finest( "Value was dropped: " + i );
               }
            }
            notificationConsumer.awaitExpectedNotificationValue();
         }

         // Check the service implementation meets the expectations on notification monotonictity and sequentiality
         final boolean serviceIsMonotonic = notificationConsumer.getNotificationSequenceWasMonotonic();
         final boolean serviceIsConsecutive = notificationConsumer.getNotificationSequenceWasConsecutive();

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
         logger.info( String.format( "The service implementation '%s' had the following notification properties: [monotonic=%b, consecutive=%b].", serviceImpl, serviceIsMonotonic, serviceIsConsecutive ) );
      } );
   }

   @ParameterizedTest
   @ValueSource( strings={ "BlockingQueueSingleWorkerMonitorNotificationServiceImpl,1,2",
                           "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl,4,2",
                           "StripedExecutorServiceMonitorNotificationServiceImpl" } )
   void testBufferOverrunConsumerLastValueAlwaysGetsSent( String serviceImpl  )
   {
      logger.info( String.format( "Starting test with service implementation '%s'", serviceImpl ) );
      assertTimeout( Duration.ofSeconds( 30 ), () ->
      {
         final NotificationConsumer<Long> notificationConsumer = NotificationConsumer.getBusyWaitingSlowConsumer(1, TimeUnit.SECONDS );
         try ( final MonitorNotificationServiceFactory factory = MonitorNotificationServiceFactoryCreator.create( serviceImpl ) )
         {
            final MonitorNotificationService<? super Long> consumerNotifier = factory.getServiceForConsumer(notificationConsumer);

            for ( long i = 1; i <= 1000; i++ )
            {
               notificationConsumer.setExpectedNotificationValue( i );
               logger.info( String.format("Publishing: %d", i ) );

               if ( consumerNotifier.publish( i ) )
               {
                  logger.finest(  String.format(" Value %s accepted - buffer OK", i ) );
               }
               else
               {
                  logger.info( String.format(" Value %s accepted - buffer OVERRUN", i ) );
                  notificationConsumer.awaitExpectedNotificationValue();
                  break;
               }
            }
         }
      } );

   }

/*- Private methods ----------------------------------------------------------*/

   // Provides a possible test method source to iterate test over all service implementations
   private static Stream<Arguments> getMonitorNotificationServiceImplementations()
   {
      final List<String> allConfigurations = MonitorNotificationServiceFactoryCreator.getAllServiceImplementations();
      return allConfigurations.stream().map(Arguments::of);
   }

   /**
    * Provides the argument data for the specified test.
    * @return the data.
    */
   private static Stream<Arguments> getArgumentsForTestThroughputUntilLastValueReceived()
   {
      final String aStr = "This is really quite a long string that goes on and on for several tens of characters";
      Integer[] arry = new Integer[ 10 ];

      final List<String> allServiceImpls = Arrays.asList( "BlockingQueueSingleWorkerMonitorNotificationServiceImpl",
                                                          "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl",
                                                          "StripedExecutorServiceMonitorNotificationServiceImpl" );

      // Note: the final value should be of a Type where equals gives an unequivocal answer (floating point values
      // would not be a good choice !)
      return Stream.of( allServiceImpls.stream().map( s -> Arguments.of( s, 10_000, 1234, 9999, NotificationConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ),
                        allServiceImpls.stream().map( s -> Arguments.of( s, 10_000, 567L, 9999, NotificationConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ),
                        allServiceImpls.stream().map( s -> Arguments.of( s, 10_000, 8.9f, 9999, NotificationConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ),
                        allServiceImpls.stream().map( s -> Arguments.of( s, 10_000, 1.2d, 9999, NotificationConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ),
                        allServiceImpls.stream().map( s -> Arguments.of( s, 10_000, aStr, 9999, NotificationConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ),
                        allServiceImpls.stream().map( s -> Arguments.of( s, 10_000, arry, 9999, NotificationConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ) ).flatMap(s -> s);
   }

   /**
    * Provides the argument data for the specified test.
    * @return the data.
    */
   private static Stream<Arguments> getArgumentsForTestThroughputUntilExpectedNotificationCountReceived()
   {
      final List<String> allServiceImpls = Arrays.asList( "BlockingQueueSingleWorkerMonitorNotificationServiceImpl",
                                                          "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl",
                                                          "StripedExecutorServiceMonitorNotificationServiceImpl" );

      final String aStr = "This is really quite a long string that goes on and on for several tens of characters";
      final Integer[] arry = new Integer[ 1000 ];
      for( int i = 0; i < 1000; i++ )
      {
         arry[ i ] = i;
      }

      return Stream.of( allServiceImpls.stream().map( s -> Arguments.of( s, 1000, 1234, NotificationConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ),
                        allServiceImpls.stream().map( s -> Arguments.of( s, 1000, 567L, NotificationConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ),
                        allServiceImpls.stream().map( s -> Arguments.of( s, 1000, 8.9f, NotificationConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ),
                        allServiceImpls.stream().map( s -> Arguments.of( s, 1000, 1.2d, NotificationConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ),
                        allServiceImpls.stream().map( s -> Arguments.of( s, 1000, aStr, NotificationConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ),
                        allServiceImpls.stream().map( s -> Arguments.of( s, 1000, arry, NotificationConsumer.ConsumerType.SLOW_WITH_BUSY_WAIT, 100 ) ) ).flatMap(s -> s);
   }


   /**
    * Provides the argument data for the specified test.
    * @return the data.
    */
   private static Stream<Arguments> getArgumentsForTestNotifyConsumerNullPublicationBehaviour()
   {
      // Summary:
      // The Disruptor implementation (before it was retired) allowed null values to be sent. The new implementations
      // do not support this.
      return Stream.of( Arguments.of( "BlockingQueueSingleWorkerMonitorNotificationServiceImpl",   false ),
                        Arguments.of( "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl", false ),
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
      // The slow consumer will take 500ms to process incoming notifications.
      // On Windows systems time resolution may only be as good as 20ms.
      // The BlockingQueueSingleWorkerMonitorNotificationServiceImpl is expected to block subsequent notifications
      // to the other consumers. All other service implementations should notify the other consumers almost
      // immediately. However, since the timing on ANY test may be perturbed by the JVM GC activities the notification
      // expectation windows are set very generously.
      return Stream.of( Arguments.of( "BlockingQueueSingleWorkerMonitorNotificationServiceImpl",   500, 400, 800, 400, 800 ),
                        Arguments.of( "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl", 500, 400, 800,  0,  200 ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl",      500, 400, 800,  0,  200 ) );
   }

/*- Nested Classes -----------------------------------------------------------*/

}
