/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;

import java.util.concurrent.*;
import java.util.function.Consumer;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class MonitorNotificationServiceFactory
{

/*- Public attributes --------------------------------------------------------*/

   public enum Configuration
   {
      SingleWorkerBlockingQueueMonitorNotificationServiceImpl,
      MultipleWorkerBlockingQueueMonitorNotificationServiceImpl,
      DisruptorMonitorNotificationServiceImpl,
      DisruptorMonitorNotificationServiceImpl2
   }

/*- Private attributes -------------------------------------------------------*/

   private final Configuration configuration;
   private final ThreadPoolExecutor executor;

   private static final int SINGLE_WORKER_THREADS = 1;
   private static final int MULTIPLE_WORKER_THREADS = 10;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Constructs a factory which provides service instances based on the required
    * underlying implementation and configuration.
    *
    * The following properties are supported:
    * <ul>
    * <li> SingleWorkerBlockingQueueMonitorNotificationServiceImpl</li>
    * <li> MultipleWorkerBlockingQueueMonitorNotificationServiceImpl</li>
    * <li> DisruptorMonitorNotificationServiceImpl</li>
    * </ul>
    *
    * @param serviceImplConfiguration specifies the properties of the service
    * instances that this factory will provide.
    *
    * @throws IllegalArgumentException if the configuration string was of the
    * incorrect format.
    */
   public MonitorNotificationServiceFactory( String serviceImplConfiguration )
   {
      Validate.notNull( serviceImplConfiguration );

      // Attempt to convert the configuration string specifier to one of the known enum
      // values. Throws IllegalArgumentException if not recognised.
      configuration = Configuration.valueOf( serviceImplConfiguration );

      switch( configuration )
      {
         case SingleWorkerBlockingQueueMonitorNotificationServiceImpl:
         {
            executor = new ThreadPoolExecutor( SINGLE_WORKER_THREADS, SINGLE_WORKER_THREADS, Long.MAX_VALUE, TimeUnit.DAYS,  new LinkedBlockingQueue<>(), new MyThreadFactory( "SingleWorker" ) );
            executor.prestartAllCoreThreads();
         }
         break;

         case MultipleWorkerBlockingQueueMonitorNotificationServiceImpl:
         {
            executor = new ThreadPoolExecutor( MULTIPLE_WORKER_THREADS, MULTIPLE_WORKER_THREADS, Long.MAX_VALUE, TimeUnit.DAYS,  new LinkedBlockingQueue<>(), new MyThreadFactory( "MultipleWorker" ) );
            executor.prestartAllCoreThreads();
         }
         break;

         default:
            executor = null;
      }

    }

   /*- Public methods -----------------------------------------------------------*/

   /**
    * Returns a service instance which will publish events to the specified Consumer.
    *
    * @param consumer the consumer to publish to.
    * @param <T> the type of events that this service instance will publish.
    *
    * @return the service instance.
    */
   public <T> MonitorNotificationService<? super T> getServiceForConsumer( Consumer<? super T> consumer )
   {
      Validate.notNull( consumer );

      switch( configuration )
      {
         case SingleWorkerBlockingQueueMonitorNotificationServiceImpl:
         case MultipleWorkerBlockingQueueMonitorNotificationServiceImpl:
         {
            final MonitorNotificationService<? super T> instance = new BlockingQueueMonitorNotificationServiceImpl<>( executor, consumer );
            instance.start();
            return instance;
         }

         case DisruptorMonitorNotificationServiceImpl:
         {
            final MonitorNotificationService<? super T> instance = new DisruptorMonitorNotificationServiceImpl<>( consumer );
            instance.start();
            return instance;
         }
         case DisruptorMonitorNotificationServiceImpl2:
         {
            final MonitorNotificationService<? super T> instance = new DisruptorMonitorNotificationServiceImpl2<>( consumer );
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
         return new Thread(r, prefix + "BlockingQueueMonitorNotificationThread-" + String.valueOf( id++ ) );
      }
   };


}
