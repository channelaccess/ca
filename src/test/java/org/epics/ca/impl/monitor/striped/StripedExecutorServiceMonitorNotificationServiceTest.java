package org.epics.ca.impl.monitor.striped;

import org.epics.ca.impl.monitor.MonitorNotificationServiceTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Locale;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.epics.ca.NotificationConsumer.*;

/**
 * Tests all underlying implementations of the MonitorNotificationService.
 */
class StripedExecutorServiceMonitorNotificationServiceTest
{
   // Get Logger
   private static final Logger logger = Logger.getLogger( MonitorNotificationServiceTest.class.getName() );

   @BeforeAll
   static void beforeAll()
   {
      System.setProperty( "java.util.logging.SimpleFormatter.format", "%1$tF %1$tT.%1$tL %4$s  %5$s%6$s%n");
      Locale.setDefault(Locale.ROOT );
   }

   /**
    * Data for the test below.
    * @return the data.
    */
   private static Stream<Arguments> getArgumentsForTestsWithSameConsumer()
   {
      return Stream.of( Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,1",   10_000, 123L, 456L ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,5",   10_000, 123L, 456L ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,10",  10_000, 123L, 456L ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,50",  10_000, 123L, 456L ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,100", 10_000, 123L, 456L ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,1",  100_000, 123L, 456L ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,5",  100_000, 123L, 456L ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,10", 100_000, 123L, 456L ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,50", 100_000, 123L, 456L ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,100",100_000, 123L, 456L ) );
   }

   /**
    *
    * @param notifications
    * @param notifyValue1
    * @param notifyValue2
    * @param serviceImpl
    * @param <T>
    */
   @ParameterizedTest
   @MethodSource( "getArgumentsForTestsWithSameConsumer" )
   <T> void testStripedExecutorServiceImplThroughputWithSameConsumers( String serviceImpl, int notifications, T notifyValue1, T notifyValue2 )
   {
      final int consumerProcessingTimeInMicroseconds = 100;
      final ConsumerType consumerType = ConsumerType.NORMAL;
      new MonitorNotificationServiceTest().testThroughputWithSameConsumer( serviceImpl, notifications, notifyValue1, notifyValue2,  consumerType, consumerProcessingTimeInMicroseconds );
   }

   /**
    * Data for the test below.
    * @return the data.
    */
   private static Stream<Arguments> getArgumentsForTestsWithDifferentConsumers()
   {
      return Stream.of( Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,1",   50_000, 123L, ConsumerType.NORMAL                 ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,2",   50_000, 123L, ConsumerType.NORMAL                 ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,4",   50_000, 123L, ConsumerType.NORMAL                 ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,8",   50_000, 123L, ConsumerType.NORMAL                 ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,16",  50_000, 123L, ConsumerType.NORMAL                 ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,32",  50_000, 123L, ConsumerType.NORMAL                 ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,64",  50_000, 123L, ConsumerType.NORMAL                 ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,128", 50_000, 123L, ConsumerType.NORMAL                 ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,1",   50_000, 123L, ConsumerType.SLOW_WITH_BUSY_WAIT    ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,2",   50_000, 123L, ConsumerType.SLOW_WITH_BUSY_WAIT    ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,4",   50_000, 123L, ConsumerType.SLOW_WITH_BUSY_WAIT    ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,8",   50_000, 123L, ConsumerType.SLOW_WITH_BUSY_WAIT    ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,16",  50_000, 123L, ConsumerType.SLOW_WITH_BUSY_WAIT    ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,32",  50_000, 123L, ConsumerType.SLOW_WITH_BUSY_WAIT    ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,64",  50_000, 123L, ConsumerType.SLOW_WITH_BUSY_WAIT    ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,128", 50_000, 123L, ConsumerType.SLOW_WITH_BUSY_WAIT    ),

// Currently (2018-07-24 this test is taken out of the build because it takes too long.
//                       Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,1",   50_000, 123L, ConsumerType.SLOW_WITH_THREAD_SLEEP ),

                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,2",   50_000, 123L, ConsumerType.SLOW_WITH_THREAD_SLEEP ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,4",   50_000, 123L, ConsumerType.SLOW_WITH_THREAD_SLEEP ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,8",   50_000, 123L, ConsumerType.SLOW_WITH_THREAD_SLEEP ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,16",  50_000, 123L, ConsumerType.SLOW_WITH_THREAD_SLEEP ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,32",  50_000, 123L, ConsumerType.SLOW_WITH_THREAD_SLEEP ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,64",  50_000, 123L, ConsumerType.SLOW_WITH_THREAD_SLEEP ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,128", 50_000, 123L, ConsumerType.SLOW_WITH_THREAD_SLEEP ) );
   }

   /**
    *
    * @param serviceImpl the service implementation.
    * @param notifications the number of notifications to be sent.
    * @param notifyValue the notification value.
    * @param <T> the notification type.
    */
   @ParameterizedTest
   @MethodSource( "getArgumentsForTestsWithDifferentConsumers" )
   <T> void testStripedExecutorServiceImplThroughputWithDifferentConsumers( String serviceImpl, int notifications, T notifyValue, ConsumerType consumerType )
   {
      final int consumerProcessingTimeInMicroseconds = ( consumerType == ConsumerType.NORMAL ) ? 1 : 100;
      new MonitorNotificationServiceTest().testThroughputWithDifferentConsumers( serviceImpl, notifications, notifyValue, consumerType, consumerProcessingTimeInMicroseconds );
   }

}
