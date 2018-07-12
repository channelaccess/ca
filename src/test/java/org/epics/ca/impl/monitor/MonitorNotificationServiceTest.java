package org.epics.ca.impl.monitor;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests all underlying implementations of the MonitorNotificationService.
 */
class MonitorNotificationServiceTest
{
   // Get Logger
   private static final Logger logger = Logger.getLogger( MonitorNotificationTask.class.getName() );

   // Provides a possible method source to iterate test over all service implementations
   private static Stream<Arguments> getMonitorNotificationServiceImplementations()
   {
      return Stream.of ( Arguments.of( "SingleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
                         Arguments.of( "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
                         Arguments.of( "DisruptorMonitorNotificationServiceNewImpl" ),
                         Arguments.of( "DisruptorMonitorNotificationServiceOldImpl" ));
   }

   @BeforeAll
   static void beforeAll()
   {
      System.setProperty( "java.util.logging.SimpleFormatter.format", "%1$tF %1$tT.%1$tL %4$s  %5$s%6$s%n");
      Locale.setDefault(Locale.ROOT );
   }

   @ParameterizedTest
   @MethodSource( "getMonitorNotificationServiceImplementations" )
   void testGetNotifierForConsumer_ThrowsNullPointerExceptionWhenConsumerNull( String monitorNotifierImpl )
   {
      assertThrows( NullPointerException.class, () -> new MonitorNotificationServiceFactory( monitorNotifierImpl ).getServiceForConsumer(null ) );
   }


   @Disabled( "Still to be decided: whether publication of null values should be allowed (in the old implementation it was) ")
   @ParameterizedTest
   @MethodSource( "getMonitorNotificationServiceImplementations" )
   void testNotifyConsumer_ThrowsNullPointerExceptionWhenValueNull( String monitorNotifierImpl )
   {
      assertThrows( NullPointerException.class, () ->
      {
         final ConsumerImpl<? super Long> consumer = new ConsumerImpl<>( 0L );
         final MonitorNotificationServiceFactory factory = new MonitorNotificationServiceFactory( monitorNotifierImpl );
         final MonitorNotificationService<Long> notifier = factory.getServiceForConsumer( consumer );
         notifier.publish( null );
      } );
   }

   private static Stream<Arguments> getArgumentsForTestThroughputWithSameConsumer()
   {
      Integer[] intArray1 = new Integer[ 10 ];
      Integer[] intArray2 = new Integer[ 100];

      return Stream.of (
            Arguments.of( 1_000, intArray1, intArray2, "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
            Arguments.of( 2_000, intArray1, intArray2, "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
            Arguments.of( 1_000, intArray1, intArray2, "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
            Arguments.of( 2_000, intArray1, intArray2, "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
            Arguments.of( 1_000, intArray1, intArray2, "DisruptorMonitorNotificationServiceNewImpl"                   ),
            Arguments.of( 2_000, intArray1, intArray2, "DisruptorMonitorNotificationServiceNewImpl"                   ),
            Arguments.of( 1_000, intArray1, intArray2, "DisruptorMonitorNotificationServiceOldImpl"                   ),
            Arguments.of( 2_000, intArray1, intArray2, "DisruptorMonitorNotificationServiceOldImpl"                   ),

            // Perform String throughput tests on all implementations
            Arguments.of( 1_000_000, "Str1", "Str2", "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
            Arguments.of( 2_000_000, "Str1", "Str2", "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
            Arguments.of( 1_000_000, "Str1", "Str2", "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
            Arguments.of( 2_000_000, "Str1", "Str2", "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
            Arguments.of( 1_000_000, "Str1", "Str2", "DisruptorMonitorNotificationServiceNewImpl"                   ),
            Arguments.of( 2_000_000, "Str1", "Str2", "DisruptorMonitorNotificationServiceNewImpl"                   ),
            Arguments.of( 1_000_000, "Str1", "Str2", "DisruptorMonitorNotificationServiceOldImpl"                   ),
            Arguments.of( 2_000_000, "Str1", "Str2", "DisruptorMonitorNotificationServiceOldImpl"                   ),

            Arguments.of( 1_000_000, "Str1", "Str2", "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
            Arguments.of( 2_000_000, "Str1", "Str2", "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
            Arguments.of( 1_000_000, "Str1", "Str2", "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
            Arguments.of( 2_000_000, "Str1", "Str2", "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
            Arguments.of( 1_000_000, "Str1", "Str2", "DisruptorMonitorNotificationServiceNewImpl"                   ),
            Arguments.of( 2_000_000, "Str1", "Str2", "DisruptorMonitorNotificationServiceNewImpl"                   ),
            Arguments.of( 1_000_000, "Str1", "Str2", "DisruptorMonitorNotificationServiceOldImpl"                   ),
            Arguments.of( 2_000_000, "Str1", "Str2", "DisruptorMonitorNotificationServiceOldImpl"                   ),

            // Perform Long throughput tests on all implementations
            Arguments.of( 1_000_000, 123L, 456L,   "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"     ),
            Arguments.of( 2_000_000, 123L, 456L,   "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"     ),
            Arguments.of( 1_000_000, 123L, 456L,   "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
            Arguments.of( 2_000_000, 123L, 456L,   "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
            Arguments.of( 1_000_000, 123L, 456L,   "DisruptorMonitorNotificationServiceNewImpl"                     ),
            Arguments.of( 2_000_000, 123L, 456L,   "DisruptorMonitorNotificationServiceNewImpl"                     ),
            Arguments.of( 1_000_000, 123L, 456L,   "DisruptorMonitorNotificationServiceOldImpl"                     ),
            Arguments.of( 2_000_000, 123L, 456L,   "DisruptorMonitorNotificationServiceOldImpl"                     ),

            // Perform Double throughput tests on all implementations
            Arguments.of( 1_000_000, 15.8, 12.2,   "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"     ),
            Arguments.of( 2_000_000, 15.8, 12.2,   "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"     ),
            Arguments.of( 1_000_000, 15.8, 12.2,   "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
            Arguments.of( 2_000_000, 15.8, 12.2,   "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
            Arguments.of( 1_000_000, 15.8, 12.2,   "DisruptorMonitorNotificationServiceNewImpl"                     ),
            Arguments.of( 2_000_000, 15.8, 12.2,   "DisruptorMonitorNotificationServiceNewImpl"                     ),
            Arguments.of( 1_000_000, 15.8, 12.2,   "DisruptorMonitorNotificationServiceOldImpl"                     ),
            Arguments.of( 2_000_000, 15.8, 12.2,   "DisruptorMonitorNotificationServiceOldImpl"                     )
      );
   }
   @ParameterizedTest
   @MethodSource( "getArgumentsForTestThroughputWithSameConsumer" )
   <T> void testThroughputWithSameConsumer( int notifications, T notifyValue1, T notifyValue2, String monitorNotifierImpl )
   {
      Validate.notNull( notifyValue1 );
      Validate.notNull( notifyValue2 );
      Validate.notNull( monitorNotifierImpl );

      logger.log( Level.INFO, String.format( "Starting test with '%s' notifications, Type: '%s' and service configuration '%s'", notifications, notifyValue1.getClass(), monitorNotifierImpl ) );
      assertTimeoutPreemptively( Duration.ofSeconds( 10 ), () ->
      {
         // Startup the service
         final ConsumerImpl<T> consumer = new ConsumerImpl<>( null );
         final MonitorNotificationService<? super T> notifier = new MonitorNotificationServiceFactory( monitorNotifierImpl ).getServiceForConsumer(consumer );

         // Start the timer and send all the notifications but one
         //logger.info( "Sending {} notifications...", notifications );
         final StopWatch stopWatch = StopWatch.createStarted();
         for ( long notification = 0; notification < notifications - 1; notification++ )
         {
            notifier.publish( notifyValue1 );
         }

         // For the last notification send a different value, so that we can check it gets
         // notified correctly
         notifier.publish( notifyValue2 );

         // Wait for the last notification. Poll relatively often so that it doesn't
         // perturb the timing measurement too much.
         while ( ! notifyValue2.equals( consumer.getValue()) )
         {
            busyWait(1 );
         }
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


   private static Stream<Arguments> getArgumentsForTestThroughputWithDifferentConsumers()
   {
      return Stream.of (
         // Perform String throughput tests on all implementations
         Arguments.of( 1_000,     "SomeString", "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 2_000,     "SomeString", "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 5_000,     "SomeString", "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 10_000,    "SomeString", "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 20_000,    "SomeString", "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 50_000,    "SomeString", "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 100_000,   "SomeString", "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 200_000,   "SomeString", "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 500_000,   "SomeString", "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 1_000_000, "SomeString", "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 1_000,     "SomeString", "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 2_000,     "SomeString", "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 5_000,     "SomeString", "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 10_000,    "SomeString", "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 20_000,    "SomeString", "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 50_000,    "SomeString", "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 100_000,   "SomeString", "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 200_000,   "SomeString", "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 500_000,   "SomeString", "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 1000_000,  "SomeString", "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 100,       "SomeString", "DisruptorMonitorNotificationServiceNewImpl"                   ),
         Arguments.of( 200,       "SomeString", "DisruptorMonitorNotificationServiceNewImpl"                   ),
         Arguments.of( 500,       "SomeString", "DisruptorMonitorNotificationServiceNewImpl"                   ),
         Arguments.of( 1000,      "SomeString", "DisruptorMonitorNotificationServiceNewImpl"                   ),
         Arguments.of( 100,       "SomeString", "DisruptorMonitorNotificationServiceOldImpl"                   ),
         Arguments.of( 200,       "SomeString", "DisruptorMonitorNotificationServiceOldImpl"                   ),
         Arguments.of( 500,       "SomeString", "DisruptorMonitorNotificationServiceOldImpl"                   ),
         Arguments.of( 1000,      "SomeString", "DisruptorMonitorNotificationServiceOldImpl"                   ),

         // Perform Long throughput tests on all implementations
         Arguments.of( 1_000,     123L,         "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 2_000,     123L,         "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 5_000,     123L,         "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 10_000,    123L,         "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 20_000,    123L,         "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 50_000,    123L,         "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 100_000,   123L,         "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 200_000,   123L,         "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 500_000,   123L,         "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 1_000_000, 123L,         "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 1_000,     123L,         "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 2_000,     123L,         "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 5_000,     123L,         "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 10_000,    123L,         "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 20_000,    123L,         "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 50_000,    123L,         "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 100_000,   123L,         "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 200_000,   123L,         "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 500_000,   123L,         "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 1000_000,  123L,         "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 100,       123L,         "DisruptorMonitorNotificationServiceNewImpl"                   ),
         Arguments.of( 200,       123L,         "DisruptorMonitorNotificationServiceNewImpl"                   ),
         Arguments.of( 500,       123L,         "DisruptorMonitorNotificationServiceNewImpl"                   ),
         Arguments.of( 1000,      123L,         "DisruptorMonitorNotificationServiceNewImpl"                   ),
         Arguments.of( 100,       123L,         "DisruptorMonitorNotificationServiceOldImpl"                   ),
         Arguments.of( 200,       123L,         "DisruptorMonitorNotificationServiceOldImpl"                   ),
         Arguments.of( 500,       123L,         "DisruptorMonitorNotificationServiceOldImpl"                   ),
         Arguments.of( 1000,      123L,         "DisruptorMonitorNotificationServiceOldImpl"                   ),

         // Perform Double throughput tests on all implementations
         Arguments.of( 1_000,     49.3,         "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 2_000,     49.3,         "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 5_000,     49.3,         "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 10_000,    49.3,         "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 20_000,    49.3,         "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 50_000,    49.3,         "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 100_000,   49.3,         "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 200_000,   49.3,         "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 500_000,   49.3,         "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 1_000_000, 49.3,         "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
         Arguments.of( 1_000,     49.3,         "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 2_000,     49.3,         "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 5_000,     49.3,         "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 10_000,    49.3,         "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 20_000,    49.3,         "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 50_000,    49.3,         "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 100_000,   49.3,         "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 200_000,   49.3,         "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 500_000,   49.3,         "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 1000_000,  49.3,         "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
         Arguments.of( 100,       49.3,         "DisruptorMonitorNotificationServiceNewImpl"                   ),
         Arguments.of( 200,       49.3,         "DisruptorMonitorNotificationServiceNewImpl"                   ),
         Arguments.of( 500,       49.3,         "DisruptorMonitorNotificationServiceNewImpl"                   ),
         Arguments.of( 1000,      49.3,         "DisruptorMonitorNotificationServiceNewImpl"                   ),
         Arguments.of( 100,       49.3,         "DisruptorMonitorNotificationServiceOldImpl"                   ),
         Arguments.of( 200,       49.3,         "DisruptorMonitorNotificationServiceOldImpl"                   ),
         Arguments.of( 500,       49.3,         "DisruptorMonitorNotificationServiceOldImpl"                   ),
         Arguments.of( 1000,      49.3,         "DisruptorMonitorNotificationServiceOldImpl"                   )
      );
   }

   @ParameterizedTest
   @MethodSource( "getArgumentsForTestThroughputWithDifferentConsumers" )
   <T> void testThroughputWithDifferentConsumers( int notifications, T notifyValue, String monitorNotifierImpl )
   {
      logger.log( Level.INFO, String.format( "Starting test with '%s' notifications, Type: '%s' and service configuration '%s'", notifications, notifyValue.getClass(), monitorNotifierImpl ) );

      // Create the Notifier factory:
      final MonitorNotificationServiceFactory factory = new MonitorNotificationServiceFactory( monitorNotifierImpl );

      // Clear the count of received notifications
      ConsumerImpl.clearNotificationCounter();

      // Start the stopwatch and send all the notifications
      logger.log( Level.INFO,  String.format( "Sending '%s' notifications. Value to send is '%s' ", notifications, notifyValue ) );

      final List<MonitorNotificationService<? super T> > resourceList = new ArrayList<>();

      MonitorNotificationService<? super T> notifier = null;
      final StopWatch stopWatch = StopWatch.createStarted();
      for ( long notification = 0; notification < notifications; notification++)
      {
         final ConsumerImpl<T> consumer = new ConsumerImpl<>( null );
         notifier = factory.getServiceForConsumer( consumer );
         notifier.publish( notifyValue );
         resourceList.add( notifier );
      }

      // Wait for any pending notifications to complete then stop timing
      while ( ConsumerImpl.getNotificationCounter() < notifications )
      {
         busyWait( 1 );
      }
      final long elapsedTimeInMicroseconds = stopWatch.getTime( TimeUnit.MICROSECONDS );

      // Now shutdown the services for good housekeeping
      resourceList.forEach( MonitorNotificationService::dispose );
      notifier.disposeAllResources();

      // Check that the expected number of notification events were received from all the consumers
      assertEquals( notifications, ConsumerImpl.getNotificationCounter() );

      double averageNotificationTimeInMicroseconds = (double) elapsedTimeInMicroseconds / (double) notifications;
      double elapsedTimeInMilliseconds = (double) elapsedTimeInMicroseconds / 1000;
      double throughput = 1_000_000 / averageNotificationTimeInMicroseconds;

      logger.log( Level.INFO, String.format( "'%s' Time to send notification to '%s' DISTINCT Consumers of Type '%s' was: '%,.3f' ms ", monitorNotifierImpl, notifications, notifyValue.getClass(), elapsedTimeInMilliseconds ) );
      logger.log( Level.INFO, String.format( "Average notification time was: '%,.3f' us ", averageNotificationTimeInMicroseconds ) );
      logger.log( Level.INFO, String.format( "Throughput was: '%,.0f' notifications per second.\n", throughput ) );
   }


   @ParameterizedTest
   @ValueSource( strings = { "SingleWorkerBlockingQueueMonitorNotificationServiceImpl", "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl", "DisruptorMonitorNotificationServiceNewImpl" } )
   void testSlowConsumerBehavior( String monitorNotifierImpl )
   {
      logger.log( Level.INFO,  "Starting test with MonitorNotifier configuration '{}'", monitorNotifierImpl );
      assertTimeoutPreemptively( Duration.ofSeconds( 10 ), () ->
      {
         // Setup  a slow and a normal consumer
         final Consumer<Long> slowConsumer = v -> {
            logger.log( Level.INFO,  "Slow Consumer: accept called" );
            busyWait( 5_000 );
            logger.log( Level.INFO, String.format( "Slow Consumer: Thread: '%s' I've been notified with value: '%s' ", Thread.currentThread(), v ) );
         };
         final ConsumerImpl<Long> normalConsumer = new ConsumerImpl<>( 0L );

         final MonitorNotificationServiceFactory factory = new MonitorNotificationServiceFactory(monitorNotifierImpl );
         final MonitorNotificationService<? super Long> slowConsumerNotifier = factory.getServiceForConsumer(slowConsumer );
         final MonitorNotificationService<? super Long> normalConsumerNotifier = factory.getServiceForConsumer(normalConsumer );

         // Create in advance the parameters to pass to the service
         final Long value1 = 123L;
         final Long value2 = 456L;
         ConsumerImpl.clearNotificationCounter();

         // Notify a very slow consumer
         slowConsumerNotifier.publish( value1 );

         // Start the stopwatch and notify our test consumer
         final StopWatch stopWatch = StopWatch.createStarted();
         normalConsumerNotifier.publish( value2 );

         // Stop the stopwatch when the test consumer sees the notification
         while ( ConsumerImpl.getNotificationCounter() < 1 )
         {
            busyWait(1) ;
         }
         final long elapsedTime = stopWatch.getTime(TimeUnit.MILLISECONDS);

         // Check that the notification arrived in the expected time frame.
         // For all implementations other than the single threaded queue the normal Consumer
         // shouldn't be blocked by the slow one.
         if ( monitorNotifierImpl.equals(MonitorNotificationServiceFactory.Configuration.SingleWorkerBlockingQueueMonitorNotificationServiceImpl.toString() ) )
         {
            assertTrue(elapsedTime > 1000);
         }
         else
         {
            assertTrue(elapsedTime < 1000);
         }
      } );
   }

   private void busyWait( int timeInMilliseconds )
   {
      long startTime = System.nanoTime();
      long endTime = startTime + timeInMilliseconds * 1_000_000L;

      while( System.nanoTime() < endTime )
      {
         // doNothing
         noop();
         //logger.info( "Spinning..." );
      }
   }

   private void noop() {}

   static class ConsumerImpl<T> implements Consumer<T>
   {
      private static AtomicInteger notificationCounter = new AtomicInteger(0 );
      private AtomicReference<T> value = new AtomicReference<>();

      static int getNotificationCounter()
      {
         return notificationCounter.get();
      }

      static void clearNotificationCounter()
      {
         notificationCounter.set( 0 );
      }

      ConsumerImpl( T initialValue )
      {
         value.set( initialValue );
      }


      @Override
      public void accept( T t )
      {
         //logger.info( "Normal Consumer: Thread: {} I've been notified with value {}", Thread.currentThread(), t);
         value.set( t );
         notificationCounter.incrementAndGet();
      }

      T getValue()
      {
         return value.get();
      }
   }

}


