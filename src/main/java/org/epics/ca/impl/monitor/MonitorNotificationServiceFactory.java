/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor;

/*- Imported packages --------------------------------------------------------*/

import heinz.StripedExecutorService;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.math.NumberUtils;
import org.epics.ca.impl.monitor.blockingqueue.BlockingQueueMonitorNotificationServiceImpl;
import org.epics.ca.impl.monitor.disruptor.DisruptorNewMonitorNotificationServiceImpl;
import org.epics.ca.impl.monitor.disruptor.DisruptorOldMonitorNotificationServiceImpl;
import org.epics.ca.impl.monitor.striped.StripedExecutorServiceImpl;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class MonitorNotificationServiceFactory
{

/*- Public attributes --------------------------------------------------------*/

   enum Configuration
   {
      BlockingQueueSingleWorkerMonitorNotificationServiceImpl,
      BlockingQueueMultipleWorkerMonitorNotificationServiceImpl,
      DisruptorOldMonitorNotificationServiceImpl,
      DisruptorNewMonitorNotificationServiceImpl,
      StripedExecutorServiceMonitorNotificationServiceImpl;

      private static final Configuration[] copyOfValues = values();

      public static boolean isRecognisedConfiguration( String name )
      {
         for ( Configuration value : copyOfValues )
         {
            if (value.name().equals(name))
            {
               return true;
            }
         }
         return false;
      }
   }

   /**
    * This definition configures the behaviour of the CA library V_1_1_0 release.
    *
    * @implNote
    * This release used a BlockingQueueMonitorNotificationServiceImpl with 5 queue workers.
    */
   public static final String V1_1_0_DEFAULT_IMPL = Configuration.BlockingQueueMultipleWorkerMonitorNotificationServiceImpl.name() + ",5";

   /**
    * This definition configures the behaviour that is applicable for a wide range of clients.
    *
    * @implNote
    * The current implementation uses a BlockingQueueMultipleWorkerMonitorNotificationServiceImpl running with 16 notification threads.
    * This may change in future releases.
    */
   public static final String DEFAULT_IMPL = Configuration.BlockingQueueMultipleWorkerMonitorNotificationServiceImpl.name() + ",16";

   /**
    * This definition configures the behaviour for a client that does not mind dropping intermediate events to keep up
    * with the notification rate.
    *
    * @implNote
    * The current implementation uses a BlockingQueueMultipleWorkerMonitorNotificationServiceImpl running with 100
    * notification threads and a non-buffering queue.
    *
    * This may change in future releases.
    */
   public static final String HUMAN_CONSUMER_IMPL = Configuration.BlockingQueueMultipleWorkerMonitorNotificationServiceImpl + ",100,1";

   /**
    * This definition configures the behaviour for a client which requires events to be buffered to keep up with the
    * notification rate during busy periods.
    *
    * @implNote
    * The current implementation uses a StripedExecutorMonitorNotificationService running with 100 notification threads.
    * This may change in future releases.
    */
   public static final String MACHINE_CONSUMER_IMPL = Configuration.BlockingQueueMultipleWorkerMonitorNotificationServiceImpl.name() + ",100";

   /**
    * The number of service threads that will be used by default for service implementations which require
    * more than one thread.
    *
    * @implNote
    * This definition currently applies to the BlockingQueueMultipleWorkerMonitorNotificationServiceImpl and
    * StripedExecutorServiceMonitorNotificationServiceImpl service implementations.
    */
   public static final int NUMBER_OF_SERVICE_THREADS_DEFAULT = 10;

   /**
    * The size of the notification value buffer which will be used by default.
    *
    * @implNote
    * This definition currently applies to the BlockingQueueSingleWorkerMonitorNotificationServiceImpl,
    * BlockingQueueMultipleWorkerMonitorNotificationServiceImpl service implementations.
    */
   public static final int NOTIFICATION_VALUE_BUFFER_SIZE_DEFAULT = Integer.MAX_VALUE;

/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = Logger.getLogger( MonitorNotificationServiceFactory.class.getName() );

   private final Configuration configuration;
   private final StripedExecutorService stripedExecutorService;
   private final ThreadPoolExecutor blockingQueueThreadPoolExecutor;
   private final int totalNumberOfServiceThreads;
   private final int notificationValueBufferQueueSize;


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Constructs a factory which will generate service instances based on the
    * specified underlying implementation and configuration.
    *
    * The following properties are supported:
    * <ul>
    * <li> BlockingQueueSingleWorkerMonitorNotificationServiceImpl</li>
    * <li> BlockingQueueMultipleWorkerMonitorNotificationServiceImpl</li>
    * <li> DisruptorOldMonitorNotificationServiceImpl</li>
    * <li> DisruptorNewMonitorNotificationServiceImpl</li>
    * <li> StripedExecutorServiceMonitorNotificationServiceImpl</li>
    * </ul>
    *
    * @param serviceImpl specifies the properties of the service
    * instances that this factory will provide.
    *
    * @throws IllegalArgumentException if the configuration string was of the
    * incorrect format.
    */
   public MonitorNotificationServiceFactory( String serviceImpl )
   {
      logger.log( Level.INFO, String.format("MonitorNotificationServiceFactory has been called with service implementation: %s", serviceImpl ) );

      Validate.notEmpty( serviceImpl );

      final String[] args = serviceImpl.split( "," );
      Validate.isTrue( args.length >= 1 );
      Validate.isTrue( Configuration.isRecognisedConfiguration( args[ 0 ]));

      configuration = Configuration.valueOf( args[ 0 ] );

      switch( configuration )
      {
         case BlockingQueueSingleWorkerMonitorNotificationServiceImpl:
         {
            if( (args.length >= 2) )
            {
               logger.log( Level.INFO, "Note: in this implementation the configuration value for the number of notification threads will be ignored and set to 1." );
            }

            totalNumberOfServiceThreads = 1;
            notificationValueBufferQueueSize = (args.length == 3) ? NumberUtils.toInt(args[ 2 ], NOTIFICATION_VALUE_BUFFER_SIZE_DEFAULT )
                                                                  : NOTIFICATION_VALUE_BUFFER_SIZE_DEFAULT ;

            final BlockingQueue<Runnable> notificationTaskQueue = new LinkedBlockingQueue<>();
            blockingQueueThreadPoolExecutor = new ThreadPoolExecutor( totalNumberOfServiceThreads, totalNumberOfServiceThreads, Long.MAX_VALUE, TimeUnit.DAYS, notificationTaskQueue, new MyThreadFactory("BlockingQueueSingleWorkerMonitorNotificationServiceThread-" ) );
            blockingQueueThreadPoolExecutor.prestartAllCoreThreads();

            // Initialise other vars so that we can make them final
            stripedExecutorService = null;
         }
         break;

         case BlockingQueueMultipleWorkerMonitorNotificationServiceImpl:
         {
            totalNumberOfServiceThreads = (args.length >= 2) ? NumberUtils.toInt(args[ 1 ], NUMBER_OF_SERVICE_THREADS_DEFAULT)
                                                             : NUMBER_OF_SERVICE_THREADS_DEFAULT;

            logger.log( Level.INFO, String.format( "Note: the number of notification threads has been set to %d.", totalNumberOfServiceThreads ) );

            notificationValueBufferQueueSize = (args.length == 3) ? NumberUtils.toInt(args[ 2 ], NOTIFICATION_VALUE_BUFFER_SIZE_DEFAULT )
                                                                  : NOTIFICATION_VALUE_BUFFER_SIZE_DEFAULT ;

            logger.log( Level.INFO, String.format( "Note: the notification buffer size has been set to %d.", notificationValueBufferQueueSize ) );

            final BlockingQueue<Runnable> notificationTaskQueue = new LinkedBlockingQueue<>();
            blockingQueueThreadPoolExecutor = new ThreadPoolExecutor( totalNumberOfServiceThreads, totalNumberOfServiceThreads, Long.MAX_VALUE, TimeUnit.DAYS, notificationTaskQueue, new MyThreadFactory("BlockingQueueMultipleWorkerMonitorNotificationServiceThread-" ) );
            blockingQueueThreadPoolExecutor.prestartAllCoreThreads();

            // Initialise other vars so that we can make them final
            stripedExecutorService = null;
         }
         break;

         case StripedExecutorServiceMonitorNotificationServiceImpl:
         {
            totalNumberOfServiceThreads = (args.length == 2) ? NumberUtils.toInt(args[ 1 ], NUMBER_OF_SERVICE_THREADS_DEFAULT)
                                                             : NUMBER_OF_SERVICE_THREADS_DEFAULT;

            logger.log( Level.INFO, String.format( "Note: the number of notification threads has been set to %d.", totalNumberOfServiceThreads ) );
            logger.log( Level.INFO, "Note: the notification buffer size has been set to unlimited." );
            stripedExecutorService = new StripedExecutorService( totalNumberOfServiceThreads );

            // Initialise other vars so that we can make them final
            blockingQueueThreadPoolExecutor = null;
            notificationValueBufferQueueSize = Integer.MAX_VALUE;
         }
         break;

         case DisruptorOldMonitorNotificationServiceImpl:
         case DisruptorNewMonitorNotificationServiceImpl:
         default:
            // Initialise other vars so that we can make them final
            stripedExecutorService = null;
            blockingQueueThreadPoolExecutor = null;
            notificationValueBufferQueueSize = 0;
            totalNumberOfServiceThreads = 0;
            break;
      }
   }

/*- Public methods -----------------------------------------------------------*/

   public static List<String> getAllServiceImplementations()
   {
      return Arrays.stream( Configuration.values() ).map( Configuration::toString ).collect( Collectors.toList() );
   }

   /**
    * Returns a service instance which will publish events to the specified Consumer.
    *
    * @param consumer the consumer to publish to.
    * @param <T> the type of events that this service instance will publish.
    *
    * @return the service instance.
    */
   public <T> MonitorNotificationService<T> getServiceForConsumer( Consumer<? super T> consumer )
   {
      Validate.notNull( consumer );

      switch( configuration )
      {
         case BlockingQueueSingleWorkerMonitorNotificationServiceImpl:
         case BlockingQueueMultipleWorkerMonitorNotificationServiceImpl:
         {
            final BlockingQueue<T> notificationValueQueue = new LinkedBlockingQueue<>( notificationValueBufferQueueSize );
            final MonitorNotificationService<T> instance = new BlockingQueueMonitorNotificationServiceImpl<>(blockingQueueThreadPoolExecutor, notificationValueQueue, consumer );
            instance.start();
            return instance;
         }

         case DisruptorNewMonitorNotificationServiceImpl:
         {
            final MonitorNotificationService<T> instance = new DisruptorNewMonitorNotificationServiceImpl<>(consumer );
            instance.start();
            return instance;
         }

         case DisruptorOldMonitorNotificationServiceImpl:
         {
            final MonitorNotificationService<T> instance = new DisruptorOldMonitorNotificationServiceImpl<>(consumer );
            instance.start();
            return instance;
         }

         case StripedExecutorServiceMonitorNotificationServiceImpl:
         {
            final MonitorNotificationService<T> instance = new StripedExecutorServiceImpl<>( stripedExecutorService, totalNumberOfServiceThreads, consumer );
            instance.start();
            return instance;
         }
      }
      throw new IllegalArgumentException( "The implementation policy was not recognised ! ");
   }


/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

   // ThreadFactory that will be used to construct new threads for consumers
   static class MyThreadFactory implements ThreadFactory
   {
      private static int id=1;
      private final String prefix;

      MyThreadFactory( String prefix )
      {
         this.prefix = prefix;
      }

      @Override
      public Thread newThread( Runnable r )
      {
         return new Thread(r, prefix + String.valueOf( id++ ) );
      }
   }


}
