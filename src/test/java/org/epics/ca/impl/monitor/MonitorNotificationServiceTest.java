package org.epics.ca.impl.monitor;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests all underlying implementations of the MonitorNotificationService.
 */
class MonitorNotificationServiceTest
{
   private static Logger logger = LoggerFactory.getLogger( MonitorNotificationServiceTest.class );

   // Provides a possible method source to iterate test over all service implementations
   private static Stream<Arguments> getMonitorNotificationServiceImplementations()
   {
      return Stream.of ( Arguments.of( "SingleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
                         Arguments.of( "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
                         Arguments.of( "DisruptorMonitorNotificationServiceImpl" ),
                         Arguments.of( "DisruptorMonitorNotificationServiceImpl2" ));
   }

   @BeforeAll
   static void beforeAll()
   {
      System.setProperty( "java.util.logging.SimpleFormatter.format", "%1$tF %1$tT.%1$tL %4$s  %5$s%6$s%n");
   }

   @ParameterizedTest
   @MethodSource( "getMonitorNotificationServiceImplementations" )
   void testGetNotifierForConsumer_ThrowsNullPointerExceptionWhenConsumerNull( String monitorNotifierImpl )
   {
      assertThrows( NullPointerException.class, () -> new MonitorNotificationServiceFactory( monitorNotifierImpl ).getServiceForConsumer(null ) );
   }

   @ParameterizedTest
   @MethodSource( "getMonitorNotificationServiceImplementations" )
   void testNotifyConsumer_ThrowsNullPointerExceptionWhenValueNull( String monitorNotifierImpl )
   {
      assertThrows( NullPointerException.class, () ->
      {
         final ConsumerImpl<Long> consumer = new ConsumerImpl<>( 0L );
         final MonitorNotificationService<? super Long> notifier = new MonitorNotificationServiceFactory(monitorNotifierImpl ).getServiceForConsumer(consumer );
         notifier.publish( null );
      } );
   }

   private static Stream<Arguments> getArgumentsForTestThroughputWithSameConsumer()
   {
      Integer[] intArray1 = new Integer[ 100_000 ];
      Integer[] intArray2 = new Integer[ 100_000 ];

      return Stream.of (
            Arguments.of( 1_000, intArray1, intArray2, "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
            Arguments.of( 2_000, intArray1, intArray2, "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
            Arguments.of( 1_000, intArray1, intArray2, "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
            Arguments.of( 2_000, intArray1, intArray2, "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
            Arguments.of( 1_000, intArray1, intArray2, "DisruptorMonitorNotificationServiceImpl"                   ),
            Arguments.of( 2_000, intArray1, intArray2, "DisruptorMonitorNotificationServiceImpl"                   ),
            Arguments.of( 1_000, intArray1, intArray2, "DisruptorMonitorNotificationServiceImpl2"                   ),
            Arguments.of( 2_000, intArray1, intArray2, "DisruptorMonitorNotificationServiceImpl2"                   ),

            // Perform String throughput tests on all implementations
            Arguments.of( 1_000_000, "Str1", "Str2", "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
            Arguments.of( 2_000_000, "Str1", "Str2", "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
            Arguments.of( 1_000_000, "Str1", "Str2", "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
            Arguments.of( 2_000_000, "Str1", "Str2", "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
            Arguments.of( 1_000_000, "Str1", "Str2", "DisruptorMonitorNotificationServiceImpl"                   ),
            Arguments.of( 2_000_000, "Str1", "Str2", "DisruptorMonitorNotificationServiceImpl"                   ),
            Arguments.of( 1_000_000, "Str1", "Str2", "DisruptorMonitorNotificationServiceImpl2"                   ),
            Arguments.of( 2_000_000, "Str1", "Str2", "DisruptorMonitorNotificationServiceImpl2"                   ),

            Arguments.of( 1_000_000, "Str1", "Str2", "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
            Arguments.of( 2_000_000, "Str1", "Str2", "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
            Arguments.of( 1_000_000, "Str1", "Str2", "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
            Arguments.of( 2_000_000, "Str1", "Str2", "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
            Arguments.of( 1_000_000, "Str1", "Str2", "DisruptorMonitorNotificationServiceImpl"                   ),
            Arguments.of( 2_000_000, "Str1", "Str2", "DisruptorMonitorNotificationServiceImpl"                   ),
            Arguments.of( 1_000_000, "Str1", "Str2", "DisruptorMonitorNotificationServiceImpl2"                   ),
            Arguments.of( 2_000_000, "Str1", "Str2", "DisruptorMonitorNotificationServiceImpl2"                   ),

            // Perform Long throughput tests on all implementations
            Arguments.of( 1_000_000, 123L, 456L,   "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"     ),
            Arguments.of( 2_000_000, 123L, 456L,   "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"     ),
            Arguments.of( 1_000_000, 123L, 456L,   "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
            Arguments.of( 2_000_000, 123L, 456L,   "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
            Arguments.of( 1_000_000, 123L, 456L,   "DisruptorMonitorNotificationServiceImpl"                     ),
            Arguments.of( 2_000_000, 123L, 456L,   "DisruptorMonitorNotificationServiceImpl"                     ),
            Arguments.of( 1_000_000, 123L, 456L,   "DisruptorMonitorNotificationServiceImpl2"                     ),
            Arguments.of( 2_000_000, 123L, 456L,   "DisruptorMonitorNotificationServiceImpl2"                     ),

            // Perform Double throughput tests on all implementations
            Arguments.of( 1_000_000, 15.8, 12.2,   "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"     ),
            Arguments.of( 2_000_000, 15.8, 12.2,   "SingleWorkerBlockingQueueMonitorNotificationServiceImpl"     ),
            Arguments.of( 1_000_000, 15.8, 12.2,   "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
            Arguments.of( 2_000_000, 15.8, 12.2,   "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl"   ),
            Arguments.of( 1_000_000, 15.8, 12.2,   "DisruptorMonitorNotificationServiceImpl"                     ),
            Arguments.of( 2_000_000, 15.8, 12.2,   "DisruptorMonitorNotificationServiceImpl"                     ),
            Arguments.of( 1_000_000, 15.8, 12.2,   "DisruptorMonitorNotificationServiceImpl2"                     ),
            Arguments.of( 2_000_000, 15.8, 12.2,   "DisruptorMonitorNotificationServiceImpl2"                     )
      );
   }
   @ParameterizedTest
   @MethodSource( "getArgumentsForTestThroughputWithSameConsumer" )
   <T> void testThroughputWithSameConsumer( int notifications,  T notifyValue1,  T notifyValue2, String monitorNotifierImpl )
   {
      logger.info( "Starting test with {} notifications, Type: '{}' and service configuration '{}'", notifications, notifyValue1.getClass(), monitorNotifierImpl );
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
         while ( ! consumer.getValue().equals( notifyValue2 ) )
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
         logger.info("Time to send {} notification to a SINGLE Consumer was: {} ms ", notifications, String.format(Locale.ROOT, "%,.3f", elapsedTimeInMilliseconds ) );
         logger.info("Average notification time was: {} us ", String.format(Locale.ROOT, "%,.3f", averageNotificationTimeInMicroseconds));
         logger.info("Throughput was: {} notifications per second.\n", String.format(Locale.ROOT, "%,.0f", throughput));
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
         Arguments.of( 100,       "SomeString", "DisruptorMonitorNotificationServiceImpl"                   ),
         Arguments.of( 200,       "SomeString", "DisruptorMonitorNotificationServiceImpl"                   ),
         Arguments.of( 500,       "SomeString", "DisruptorMonitorNotificationServiceImpl"                   ),
         Arguments.of( 1000,      "SomeString", "DisruptorMonitorNotificationServiceImpl"                   ),
         Arguments.of( 100,       "SomeString", "DisruptorMonitorNotificationServiceImpl2"                   ),
         Arguments.of( 200,       "SomeString", "DisruptorMonitorNotificationServiceImpl2"                   ),
         Arguments.of( 500,       "SomeString", "DisruptorMonitorNotificationServiceImpl2"                   ),
         Arguments.of( 1000,      "SomeString", "DisruptorMonitorNotificationServiceImpl2"                   ),

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
         Arguments.of( 100,       123L,         "DisruptorMonitorNotificationServiceImpl"                   ),
         Arguments.of( 200,       123L,         "DisruptorMonitorNotificationServiceImpl"                   ),
         Arguments.of( 500,       123L,         "DisruptorMonitorNotificationServiceImpl"                   ),
         Arguments.of( 1000,      123L,         "DisruptorMonitorNotificationServiceImpl"                   ),
         Arguments.of( 100,       123L,         "DisruptorMonitorNotificationServiceImpl2"                   ),
         Arguments.of( 200,       123L,         "DisruptorMonitorNotificationServiceImpl2"                   ),
         Arguments.of( 500,       123L,         "DisruptorMonitorNotificationServiceImpl2"                   ),
         Arguments.of( 1000,      123L,         "DisruptorMonitorNotificationServiceImpl2"                   ),

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
         Arguments.of( 100,       49.3,         "DisruptorMonitorNotificationServiceImpl"                   ),
         Arguments.of( 200,       49.3,         "DisruptorMonitorNotificationServiceImpl"                   ),
         Arguments.of( 500,       49.3,         "DisruptorMonitorNotificationServiceImpl"                   ),
         Arguments.of( 1000,      49.3,         "DisruptorMonitorNotificationServiceImpl"                   ),
         Arguments.of( 100,       49.3,         "DisruptorMonitorNotificationServiceImpl2"                   ),
         Arguments.of( 200,       49.3,         "DisruptorMonitorNotificationServiceImpl2"                   ),
         Arguments.of( 500,       49.3,         "DisruptorMonitorNotificationServiceImpl2"                   ),
         Arguments.of( 1000,      49.3,         "DisruptorMonitorNotificationServiceImpl2"                   )
      );
   }

   @ParameterizedTest
   @MethodSource( "getArgumentsForTestThroughputWithDifferentConsumers" )
   <T> void testThroughputWithDifferentConsumers( int notifications, T notifyValue, String monitorNotifierImpl )
   {
      logger.info( "Starting test with {} notifications, Type: '{}' and service configuration '{}'", notifications, notifyValue.getClass(), monitorNotifierImpl );

      // Create the Notifier factory:
      final MonitorNotificationServiceFactory factory = new MonitorNotificationServiceFactory( monitorNotifierImpl );

      // Clear the count of received notifications
      ConsumerImpl.clearNotificationCounter();

      // Start the stopwatch and send all the notifications
      logger.info( "Sending {} notifications. Value to send is '{}' ", notifications, notifyValue );

      List<MonitorNotificationService<? super T> > resourceList = new ArrayList<>();

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
      resourceList.stream().forEach( r -> r.dispose() );
      notifier.disposeAllResources();

      // Check that the expected number of notification events were received from all the consumers
      assertEquals( notifications, ConsumerImpl.getNotificationCounter() );

      double averageNotificationTimeInMicroseconds = (double) elapsedTimeInMicroseconds / (double) notifications;
      double elapsedTimeInMilliseconds = (double) elapsedTimeInMicroseconds / 1000;
      double throughput = 1_000_000 / averageNotificationTimeInMicroseconds;

      logger.info("{} Time to send notification to {} DISTINCT Consumers of Type '{}' was: {} ms ", monitorNotifierImpl, notifications, notifyValue.getClass(), String.format(Locale.ROOT, "%,.3f", elapsedTimeInMilliseconds ) );
      logger.info("Average notification time was: {} us ", String.format(Locale.ROOT, "%,.3f", averageNotificationTimeInMicroseconds));
      logger.info("Throughput was: {} notifications per second.\n", String.format(Locale.ROOT, "%,.0f", throughput));
   }


   @ParameterizedTest
   @ValueSource( strings = { "SingleWorkerBlockingQueueMonitorNotificationServiceImpl", "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl", "DisruptorMonitorNotificationServiceImpl" } )
   void testSlowConsumerBehavior( String monitorNotifierImpl )
   {
      logger.info( "Starting test with MonitorNotifier configuration '{}'", monitorNotifierImpl );
      assertTimeoutPreemptively( Duration.ofSeconds( 10 ), () ->
      {
         // Setup  a slow and a normal consumer
         final Consumer<Long> slowConsumer = v -> {
            logger.info( "Slow Consumer: accept called" );
            busyWait( 5_000 );
            logger.info( "Slow Consumer: Thread: {} I've been notified with value: {} ", Thread.currentThread(),  v );
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

