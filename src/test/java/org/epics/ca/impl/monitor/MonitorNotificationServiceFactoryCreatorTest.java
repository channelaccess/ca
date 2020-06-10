/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca.impl.monitor;

/*- Imported packages --------------------------------------------------------*/

import org.epics.ca.ThreadWatcher;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

   @Test
   void testCreateMonitorNotificationServiceFactory_ThrowsNullPointerExceptionWhenServiceImplConfigurationNull()
   {
      assertThrows( NullPointerException.class, () -> MonitorNotificationServiceFactoryCreator.create( null ));
   }

   @Test
   void testCreateMonitorNotificationServiceFactory_ThrowsIllegalArgumentExceptionWhenServiceImplConfigurationNotRecognised()
   {
      assertThrows(IllegalArgumentException.class, () -> MonitorNotificationServiceFactoryCreator.create( "ThisConfigIsBananas" ));
   }

   @MethodSource( "getMonitorNotificationServiceImplementations" )
   @ParameterizedTest
   void testCreateMonitorNotificationServiceFactory_expected_configurations_are_recognised( String serviceImpl )
   {
      try ( final MonitorNotificationServiceFactory factory = MonitorNotificationServiceFactoryCreator.create( serviceImpl ) )
      {
         assertNotNull( factory );
      }
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
      try ( final MonitorNotificationServiceFactory factory = MonitorNotificationServiceFactoryCreator.create( serviceImpl ) )
      {
         assertEquals( expectedThreads, factory.getQosMetricNumberOfNotificationThreadsPerConsumer() );
      }
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
   void testServiceImplResourceDisposeBehaviour_checkFactoryAutoClose( String serviceImpl )
   {
      logger.info( String.format( "Testing resource autoclose behaviour of service implementation: '%s'", serviceImpl + ",50" ) );

      final ThreadWatcher threadWatcher = ThreadWatcher.start();
      try( MonitorNotificationServiceFactory factory = MonitorNotificationServiceFactoryCreator.create( serviceImpl + ",50" ) )
      {
         final MonitorNotificationService<Long> service = factory.getServiceForConsumer( v -> {} );
         service.publish(123L );
      }

      // Check threads after factory autoclose are the same as at start of test.
      threadWatcher.verify();
   }

   @MethodSource( "getMonitorNotificationServiceImplementations" )
   @ParameterizedTest
   void testServiceImplResourceDisposeBehaviour_checkFactoryManualClose( String serviceImpl )
   {
      logger.info( String.format( "Testing resource autoclose behaviour of service implementation: '%s'", serviceImpl + ",50" ) );

      final ThreadWatcher threadWatcher = ThreadWatcher.start();
      final MonitorNotificationServiceFactory factory = MonitorNotificationServiceFactoryCreator.create( serviceImpl + ",50" );
      final MonitorNotificationService<Long> service = factory.getServiceForConsumer( v -> {} );
      service.publish(123L );
      factory.close();

      // Check threads after factory manual close are the same as at start of test.
      threadWatcher.verify();
   }


/*- Private methods ----------------------------------------------------------*/

   // Provides a possible test method source to iterate test over all service implementations
   private static Stream<Arguments> getMonitorNotificationServiceImplementations()
   {
      final List<String> allConfigurations = MonitorNotificationServiceFactoryCreator.getAllServiceImplementations();
      return allConfigurations.stream().map(Arguments::of);
   }
   
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
                        Arguments.of( "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl,15,19",  true, 19 ) );
   }
   
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
                        Arguments.of( "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl,XXX", MonitorNotificationServiceFactoryCreator.NUMBER_OF_SERVICE_THREADS_DEFAULT ) );
   }
   
   private static Stream<Arguments> getArgumentsForTestServiceImplBufferingBehaviour()
   {
      return Stream.of( Arguments.of( MonitorNotificationServiceFactoryCreator.HUMAN_CONSUMER_IMPL, false ),
                        Arguments.of( MonitorNotificationServiceFactoryCreator.MACHINE_CONSUMER_IMPL, true ) ) ;
   }

/*- Nested Classes -----------------------------------------------------------*/

}