/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor.blockingqueue;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.epics.ca.impl.monitor.MonitorNotificationServiceFactoryCreator;
import org.epics.ca.impl.monitor.MonitorNotificationServiceFactory;
import org.epics.ca.impl.monitor.MonitorNotificationService;
import org.epics.ca.util.logging.LibraryLogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class BlockingQueueMonitorNotificationServiceFactory implements MonitorNotificationServiceFactory, AutoCloseable
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( BlockingQueueMonitorNotificationServiceFactory.class );

   private final List<MonitorNotificationService<?>> serviceList = new ArrayList<>();
   private final ThreadPoolExecutor threadPoolExecutor;
   private final int bufferSize;
   private final int numberOfThreads;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Creates a new factory with the capability of generating MonitorNotificationService
    * instances for each Consumer. This factory provides service instances based
    * on a shared work queue, multiple notification threads and a value notification
    * buffer of configurable size.
    *
    * @param numberOfThreads the number of threads that will take items off the work queue.
    * @param bufferSize the size of the notification value buffer.
    */
   public BlockingQueueMonitorNotificationServiceFactory( int numberOfThreads, int bufferSize )
   {
      Validate.inclusiveBetween( 1, Integer.MAX_VALUE, numberOfThreads );
      Validate.inclusiveBetween( 1, Integer.MAX_VALUE, bufferSize );
      this.bufferSize = bufferSize;
      this.numberOfThreads = numberOfThreads;

      final int numberOfThreadsBaseline = Thread.getAllStackTraces().keySet().size();
      logger.finest( String.format( "The number of baseline threads in the system was: %d", numberOfThreadsBaseline ) );
      logger.fine( String.format( "A BlockingQueueMonitorNotificationServiceFactory is being created with %d threads and a buffer size with %d notification entries...", numberOfThreads, bufferSize ) );

      final BlockingQueue<Runnable> notificationTaskQueue = new LinkedBlockingQueue<>();
      threadPoolExecutor = new ThreadPoolExecutor( numberOfThreads, numberOfThreads, Long.MAX_VALUE, TimeUnit.DAYS, notificationTaskQueue, new MyThreadFactory("BlockingQueueMonitorNotificationServiceThread-" ) );
      threadPoolExecutor.prestartAllCoreThreads();
   }

/*- Public methods -----------------------------------------------------------*/

   /**
    * {@inheritDoc}
    */
   @Override
   public <T> MonitorNotificationService<T> getServiceForConsumer( Consumer<? super T> consumer )
   {
       Validate.notNull( consumer );

       final BlockingQueue<T> notificationValueQueue = new LinkedBlockingQueue<>( bufferSize );
       final MonitorNotificationService<T> instance = new BlockingQueueMonitorNotificationService<>( threadPoolExecutor, notificationValueQueue, consumer );
       serviceList.add( instance );
       instance.init();
       return instance;
   }

   /**
    * {@inheritDoc}
    * @implNote
    *
    * This implementation calls close on all service instances that it previously created.
    */
   @Override
   public void close()
   {
      logger.finest(  String.format( "A BlockingQueueMonitorNotificationServiceFactory is being closed with %d service entries...", getServiceCount() ) );
      for ( MonitorNotificationService<?> service : serviceList )
      {
         service.close();
      }
      serviceList.clear();
      MonitorNotificationServiceFactoryCreator.shutdownExecutor( threadPoolExecutor );
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int getServiceCount()
   {
      return serviceList.size();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean getQosMetricIsBuffered()
   {
      return ( bufferSize > 1 );
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int getQosMetricBufferSizePerConsumer()
   {
      return bufferSize;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int getQosMetricNumberOfNotificationThreadsPerConsumer()
   {
      return numberOfThreads;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean getQosMetricIsNullPublishable()
   {
      return false;
   }


/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

   // ThreadFactory that will be used to construct new threads for consumers
   private static class MyThreadFactory implements ThreadFactory
   {
      private static int id=1;
      private final String prefix;

      private MyThreadFactory( String prefix )
      {
         this.prefix = prefix;
      }

      @Override
      public Thread newThread( Runnable r )
      {
         return new Thread(r, prefix + id++);
      }
   }

}
