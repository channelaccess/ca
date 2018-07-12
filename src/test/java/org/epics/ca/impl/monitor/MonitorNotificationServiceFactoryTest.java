package org.epics.ca.impl.monitor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;


class MonitorNotificationServiceFactoryTest
{
   // Provides a possible method source to iterate test over all service implementations
   private static Stream<Arguments> getMonitorNotificationServiceImplementations()
   {
      return Stream.of ( Arguments.of( "SingleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
                         Arguments.of( "MultipleWorkerBlockingQueueMonitorNotificationServiceImpl" ),
                         Arguments.of( "DisruptorMonitorNotificationServiceOldImpl" ),
                         Arguments.of( "DisruptorMonitorNotificationServiceNewImpl" ) );
   }

   @Test
   void testConstructMonitorNotificationServiceFactory_ThrowsNullPointerExceptionWhenServiceImplConfigurationNull()
   {
      assertThrows(NullPointerException.class, () -> new MonitorNotificationServiceFactory(null ));
   }

   @Test
   void testConstructMonitorNotificationServiceFactory_ThrowsIllegalArgumentExceptionWhenServiceImplConfigurationNotRecognised()
   {
      assertThrows(IllegalArgumentException.class, () -> new MonitorNotificationServiceFactory("ThisConfigIsBananas"));
   }

   @MethodSource( "getMonitorNotificationServiceImplementations" )
   @ParameterizedTest
   void testConstructMonitorNotificationServiceFactory_expected_configurations_are_recognised( String serviceImplConfiguration )
   {
      new MonitorNotificationServiceFactory( serviceImplConfiguration );
   }

}

