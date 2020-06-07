/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor.striped;

/*- Imported packages --------------------------------------------------------*/

import org.epics.ca.ThreadWatcher;
import org.epics.ca.impl.monitor.MonitorNotificationServiceTest;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.epics.ca.NotificationConsumer.*;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Tests all underlying implementations of the MonitorNotificationService.
 */
class StripedExecutorServiceMonitorNotificationServiceTest
{
   
/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/
   
   private static final Logger logger = LibraryLogManager.getLogger( StripedExecutorServiceMonitorNotificationServiceTest.class );

   private ThreadWatcher threadWatcher;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/

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

   /**
    * @param serviceImpl the service implementation.
    * @param notifications the number of notifications to be sent.
    * @param notifyValue the notification value.
    * @param <T> the notification type.
    */
   @ParameterizedTest
   @MethodSource( "getArgumentsForTestThroughputUntilExpectedNotificationsReceived" )
   <T> void testThroughputUntilExpectedNotificationsReceived( String serviceImpl, int notifications, T notifyValue, ConsumerType consumerType )
   {
      final int consumerProcessingTimeInMicroseconds = ( consumerType == ConsumerType.NORMAL ) ? 1 : 100;

      // Call test in main package
      new MonitorNotificationServiceTest().testThroughputUntilExpectedNotificationsReceived( serviceImpl, notifications, notifyValue, consumerType, consumerProcessingTimeInMicroseconds );
   }

   /**
    * @param serviceImpl the service implementation.
    * @param notifications the number of notifications to be sent.
    * @param notifyValue1 the first example notification value.
    * @param notifyValue2 the second example notification value.
    * @param <T> the notification type.
    */
   @ParameterizedTest
   @MethodSource( "getArgumentsForTestThroughputUntilLastValueReceived" )
   <T> void testThroughputUntilLastValueReceived( String serviceImpl, int notifications, T notifyValue1, T notifyValue2 )
   {
      final int consumerProcessingTimeInMicroseconds = 100;
      final ConsumerType consumerType = ConsumerType.NORMAL;
      new MonitorNotificationServiceTest().testThroughputUntilLastValueReceived( serviceImpl, notifications, notifyValue1, notifyValue2, consumerType, consumerProcessingTimeInMicroseconds );
   }
   
/*- Private methods ----------------------------------------------------------*/
   
   private static Stream<Arguments> getArgumentsForTestThroughputUntilLastValueReceived()
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
   
   private static Stream<Arguments> getArgumentsForTestThroughputUntilExpectedNotificationsReceived()
   {
      return Stream.of( Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,1",    50_000, 123L, ConsumerType.NORMAL                 ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,2",    50_000, 123L, ConsumerType.NORMAL                 ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,4",    50_000, 123L, ConsumerType.NORMAL                 ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,8",    50_000, 123L, ConsumerType.NORMAL                 ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,16",   50_000, 123L, ConsumerType.NORMAL                 ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,32",   50_000, 123L, ConsumerType.NORMAL                 ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,64",   50_000, 123L, ConsumerType.NORMAL                 ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,128",  50_000, 123L, ConsumerType.NORMAL                 ),

                        // Note: some of the tests below with only a few threads are currently (2020-05-26) removed from
                        // the test sequence because they take too long.
                        // Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,1", 50_000, 123L, ConsumerType.SLOW_WITH_BUSY_WAIT    ),
                        // Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,2", 50_000, 123L, ConsumerType.SLOW_WITH_BUSY_WAIT    ),
                        // Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,4", 50_000, 123L, ConsumerType.SLOW_WITH_BUSY_WAIT    ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,8",    50_000, 123L, ConsumerType.SLOW_WITH_BUSY_WAIT    ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,16",   50_000, 123L, ConsumerType.SLOW_WITH_BUSY_WAIT    ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,32",   50_000, 123L, ConsumerType.SLOW_WITH_BUSY_WAIT    ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,64",   50_000, 123L, ConsumerType.SLOW_WITH_BUSY_WAIT    ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,128",  50_000, 123L, ConsumerType.SLOW_WITH_BUSY_WAIT    ),

                        // Note: some of the tests below with only a few threads are currently (2020-05-26) removed from
                        // the test sequence because they take too long.
                        // Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,1", 50_000, 123L, ConsumerType.SLOW_WITH_THREAD_SLEEP ),
                        // Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,2", 50_000, 123L, ConsumerType.SLOW_WITH_THREAD_SLEEP ),
                        // Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,4", 50_000, 123L, ConsumerType.SLOW_WITH_THREAD_SLEEP ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,8",    50_000, 123L, ConsumerType.SLOW_WITH_THREAD_SLEEP ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,16",   50_000, 123L, ConsumerType.SLOW_WITH_THREAD_SLEEP ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,32",   50_000, 123L, ConsumerType.SLOW_WITH_THREAD_SLEEP ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,64",   50_000, 123L, ConsumerType.SLOW_WITH_THREAD_SLEEP ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,128",  50_000, 123L, ConsumerType.SLOW_WITH_THREAD_SLEEP ) );
   }

}
