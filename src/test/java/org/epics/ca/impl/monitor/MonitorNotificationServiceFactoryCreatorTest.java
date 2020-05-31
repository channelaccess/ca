/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca.impl.monitor;

/*- Imported packages --------------------------------------------------------*/

import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

class MonitorNotificationServiceFactoryCreatorTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( MonitorNotificationServiceFactoryCreatorTest.class );

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public attributes --------------------------------------------------------*/

   @Test
   void testCreateMonitorNotificationServiceFactory_ThrowsNullPointerExceptionWhenServiceImplConfigurationNull()
   {
      assertThrows( NullPointerException.class, () -> MonitorNotificationServiceFactoryCreator.create(null ));
   }

   @Test
   void testCreateMonitorNotificationServiceFactory_ThrowsIllegalArgumentExceptionWhenServiceImplConfigurationNotRecognised()
   {
      assertThrows(IllegalArgumentException.class, () -> MonitorNotificationServiceFactoryCreator.create("ThisConfigIsBananas" ));
   }

   @MethodSource( "getMonitorNotificationServiceImplementations" )
   @ParameterizedTest
   void testCreateMonitorNotificationServiceFactory_expected_configurations_are_recognised( String serviceImpl )
   {
      try ( final MonitorNotificationServiceFactory factory = MonitorNotificationServiceFactoryCreator.create(serviceImpl ) )
      {
         assertNotNull( factory );
      }
   }

   /**
    * Data for the test below.
    * @return the data.
    */
   private static Stream<Arguments> getArgumentsForTestMonitorNotificationServiceImpl_NumberOfServiceThreadsArgumentProcessing()
   {
      return Stream.of( Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl", MonitorNotificationServiceFactoryCreator.NUMBER_OF_SERVICE_THREADS_DEFAULT ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,66", 66 ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,XXX", MonitorNotificationServiceFactoryCreator.NUMBER_OF_SERVICE_THREADS_DEFAULT ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,XXX,YYY", MonitorNotificationServiceFactoryCreator.NUMBER_OF_SERVICE_THREADS_DEFAULT ),
                        Arguments.of( "BlockingQueueSingleWorkerMonitorNotificationServiceImpl", 1 ),
                        Arguments.of( "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl", MonitorNotificationServiceFactoryCreator.NUMBER_OF_SERVICE_THREADS_DEFAULT ),
                        Arguments.of( "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl,10", 10 ),
                        Arguments.of( "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl,10,YYY", 10 ),
                        Arguments.of( "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl,XXX", MonitorNotificationServiceFactoryCreator.NUMBER_OF_SERVICE_THREADS_DEFAULT ),
                        Arguments.of( "DisruptorOldMonitorNotificationServiceImpl", 1 ),
                        Arguments.of( "DisruptorOldMonitorNotificationServiceImpl,XXX", 1 ),
                        Arguments.of( "DisruptorOldMonitorNotificationServiceImpl,XXX,YYY", 1 ),
                        Arguments.of( "DisruptorOldMonitorNotificationServiceImpl,27", 1 ) ,
                        Arguments.of( "DisruptorNewMonitorNotificationServiceImpl", 1 ),
                        Arguments.of( "DisruptorNewMonitorNotificationServiceImpl,XXX", 1 ),
                        Arguments.of( "DisruptorNewMonitorNotificationServiceImpl,XXX,YYY", 1 ),
                        Arguments.of( "DisruptorNewMonitorNotificationServiceImpl,52", 1 ));
   }

   /**
    * Tests that the different service implementations correctly handle the argument processing for the required
    * number of threads.
    *
    * @param serviceImpl the service implementation specifier string.
    * @param expectedThreads the expected number of threads that will result with the specified configuration.
    */
   @MethodSource( "getArgumentsForTestMonitorNotificationServiceImpl_NumberOfServiceThreadsArgumentProcessing" )
   @ParameterizedTest
   void testMonitorNotificationServiceImpl_NumberOfServiceThreadsArgumentProcessing( String serviceImpl, int expectedThreads )
   {
      try ( final MonitorNotificationServiceFactory factory = MonitorNotificationServiceFactoryCreator.create(serviceImpl ) )
      {
         assertEquals( expectedThreads, factory.getQosMetricNumberOfNotificationThreadsPerConsumer() );
      }
   }

   /**
    * Data for the test below.
    * @return the data.
    */
   private static Stream<Arguments> getArgumentsForTestMonitorNotificationServiceImpl_BufferSizeArgumentProcessing()
   {
      return Stream.of( Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl", true, Integer.MAX_VALUE ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,,100", true, Integer.MAX_VALUE ),
                        Arguments.of( "StripedExecutorServiceMonitorNotificationServiceImpl,5,100", true, Integer.MAX_VALUE ),
                        Arguments.of( "BlockingQueueSingleWorkerMonitorNotificationServiceImpl",  true, Integer.MAX_VALUE ),
                        Arguments.of( "BlockingQueueSingleWorkerMonitorNotificationServiceImpl,,1",  false, 1 ),
                        Arguments.of( "BlockingQueueSingleWorkerMonitorNotificationServiceImpl,,22",  true, 22 ),
                        Arguments.of( "BlockingQueueSingleWorkerMonitorNotificationServiceImpl,15,79",  true, 79 ),
                        Arguments.of( "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl,,1",  false, 1 ),
                        Arguments.of( "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl,,42",  true, 42 ),
                        Arguments.of( "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl,15,19",  true, 19 ),
                        Arguments.of( "DisruptorOldMonitorNotificationServiceImpl", false, 2 ),
                        Arguments.of( "DisruptorOldMonitorNotificationServiceImpl,XXX", false, 2  ),
                        Arguments.of( "DisruptorOldMonitorNotificationServiceImpl,XXX,YYY", false, 2  ),
                        Arguments.of( "DisruptorNewMonitorNotificationServiceImpl", false, 2 ),
                        Arguments.of( "DisruptorNewMonitorNotificationServiceImpl,XXX", false, 2  ),
                        Arguments.of( "DisruptorNewMonitorNotificationServiceImpl,XXX,YYY", false, 2  ) );
   }

   /**
    * Tests that the different service implementations correctly handle the argument processing for the required
    * buffer size.
    *
    * @param serviceImpl the service implementation specifier string.
    * @param expectedIsBuffered the expected buffering/non-buffering capability of the servic
    * @param expectedBufferSize the expected buffer size that will result with the specified configuration.
    */
   @MethodSource( "getArgumentsForTestMonitorNotificationServiceImpl_BufferSizeArgumentProcessing" )
   @ParameterizedTest
   void testMonitorNotificationServiceImpl_BufferSizeArgumentProcessing( String serviceImpl, boolean expectedIsBuffered, int expectedBufferSize )
   {
      try ( final MonitorNotificationServiceFactory factory = MonitorNotificationServiceFactoryCreator.create(serviceImpl ) )
      {
         assertEquals(expectedIsBuffered, factory.getQosMetricIsBuffered());
         assertEquals(expectedBufferSize, factory.getQosMetricBufferSizePerConsumer());
      }
   }

   /**
    * Data for the test below.
    * @return the data.
    */
   private static Stream<Arguments> getArgumentsForTestServiceImplBufferingBehaviour()
   {
      return Stream.of( Arguments.of(MonitorNotificationServiceFactoryCreator.HUMAN_CONSUMER_IMPL, false ),
                        Arguments.of(MonitorNotificationServiceFactoryCreator.MACHINE_CONSUMER_IMPL, true ) ) ;
   }

   /**
    * Verify that the HUMAN_IMPL service specifier returns a service that is not buffering.
    * Verify that the MACHINE_IMPL service specifier returns a service that is buffering.
    */
   @ParameterizedTest
   @MethodSource( "getArgumentsForTestServiceImplBufferingBehaviour" )
   void testServiceImplBufferingBehaviour( String serviceImpl, boolean expectedResult )
   {
      try ( MonitorNotificationServiceFactory factory = MonitorNotificationServiceFactoryCreator.create(serviceImpl ) )
      {
         assertEquals( expectedResult, factory.getQosMetricIsBuffered());
      }
   }

   @MethodSource( "getMonitorNotificationServiceImplementations" )
   @ParameterizedTest
   void testServiceImplResourceDisposeBehaviour( String serviceImpl )
   {
      logger.info( String.format("Testing resource dispose behaviour of service implementation: '%s'", serviceImpl + ",50"));
      final int numberOfThreadsBaseline = Thread.getAllStackTraces().keySet().size();
      logger.info( String.format("The number of baseline threads in the system was: %d", numberOfThreadsBaseline));

      try ( final MonitorNotificationServiceFactory factory = MonitorNotificationServiceFactoryCreator.create(serviceImpl + ",50" ) )
      {
         final int numberOfThreadsAfterFactoryCreate = Thread.getAllStackTraces().keySet().size();
         logger.info( String.format("The number of threads in the system after factory create was: %d", numberOfThreadsAfterFactoryCreate));

         for ( int i = 0; i < 3; i++ )
         {
            logger.info( "Creating a service...");

            final MonitorNotificationService<Long> service = factory.getServiceForConsumer( v -> {} );
            final int numberOfThreadsAfterServiceCreate = Thread.getAllStackTraces().keySet().size();
            logger.info( String.format("The number of threads in the system after service create was: %d", numberOfThreadsAfterServiceCreate));

            service.publish(123L);
            final int numberOfThreadsAfterServiceFirstPublish = Thread.getAllStackTraces().keySet().size();
            logger.info( String.format("The number of threads in the system after service first publish was: %d", numberOfThreadsAfterServiceFirstPublish));

            service.close();
            final int numberOfThreadsAfterServiceClose = Thread.getAllStackTraces().keySet().size();
            logger.info( String.format("The number of threads in the system after service close was: %d", numberOfThreadsAfterServiceClose));
         }
      }
      final int numberOfThreadsAfterFactoryClose = Thread.getAllStackTraces().keySet().size();

      logger.info( String.format("The number of threads in the system after factory close was: %d\n", numberOfThreadsAfterFactoryClose));
      logger.info( String.format("Still running: %s ", Thread.getAllStackTraces().keySet()));
      assertEquals( numberOfThreadsBaseline, numberOfThreadsAfterFactoryClose );
   }

/*- Private attributes -------------------------------------------------------*/

   // Provides a possible test method source to iterate test over all service implementations
   private static Stream<Arguments> getMonitorNotificationServiceImplementations()
   {
      final List<String> allConfigurations = MonitorNotificationServiceFactoryCreator.getAllServiceImplementations();
      return allConfigurations.stream().map(Arguments::of);
   }

/*- Nested Classes -----------------------------------------------------------*/

}