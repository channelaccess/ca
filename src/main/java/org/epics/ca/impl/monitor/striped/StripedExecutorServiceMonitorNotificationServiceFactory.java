/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor.striped;

/*- Imported packages --------------------------------------------------------*/

import eu.javaspecialists.tjsn.concurrency.stripedexecutor.StripedExecutorService;
import org.apache.commons.lang3.Validate;
import org.epics.ca.impl.monitor.MonitorNotificationServiceFactoryCreator;
import org.epics.ca.impl.monitor.MonitorNotificationServiceFactory;
import org.epics.ca.impl.monitor.MonitorNotificationService;
import org.epics.ca.util.logging.LibraryLogManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class StripedExecutorServiceMonitorNotificationServiceFactory implements MonitorNotificationServiceFactory, AutoCloseable
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger(StripedExecutorServiceMonitorNotificationServiceFactory.class);
   private final List<MonitorNotificationService<?>> serviceList = new ArrayList<>();

   private final StripedExecutorService stripedExecutorService;
   private final  int numberOfThreads;


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Creates a new factory with the capability of generating MonitorNotificationService
    * instances for each Consumer. This factory provides service instances based
    * on a StripedExecutorService, multiple notification threads and an unbounded
    * value notification buffer.
    *
    * @param numberOfThreads the number of threads that for the StripedExecutorService.
    */
   public StripedExecutorServiceMonitorNotificationServiceFactory( int numberOfThreads )
   {
      Validate.inclusiveBetween( 1, Integer.MAX_VALUE, numberOfThreads );
      this.numberOfThreads = numberOfThreads;

      final int numberOfThreadsBaseline = Thread.getAllStackTraces().keySet().size();
      logger.log(Level.FINEST, String.format("The number of baseline threads in the system is: %d", numberOfThreadsBaseline));
      logger.log(Level.INFO, String.format( "A StripedExecutorServiceMonitorNotificationServiceFactory is being created with %d threads and an unlimited notification entry buffer size...", numberOfThreads ) );
      stripedExecutorService = new StripedExecutorService( numberOfThreads );
   }

/*- Public methods -----------------------------------------------------------*/

    /**
    * {@inheritDoc}
    */
   @Override
   public <T> MonitorNotificationService<T> getServiceForConsumer( Consumer<? super T> consumer )
   {
       Validate.notNull( consumer );

       final MonitorNotificationService<T> instance = new StripedExecutorServiceMonitorNotificationService<>(this, stripedExecutorService, consumer );
       serviceList.add( instance );
       instance.init();
       return instance;
   }

   /**
    * {@inheritDoc}
    *
    * This implementation calls close on all service instances that it previously created.
    */
   @Override
   public void close()
   {
      logger.log(Level.FINEST, String.format( "A StripedExecutorServiceMonitorNotificationServiceFactory is being closed with %d service entries...", getServiceCount() ) );
      for ( MonitorNotificationService<?> service : serviceList )
      {
         service.close();
      }
      serviceList.clear();
      MonitorNotificationServiceFactoryCreator.shutdownExecutor( stripedExecutorService );
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
    *
    * @implNote
    *
    * Returns the value that was configured during class construction.
    */
   @Override
   public int getQosMetricNumberOfNotificationThreadsPerConsumer()
   {
      return numberOfThreads;
   }

   /**
    * {@inheritDoc}
    *  @implNote
    *
    * False for this service factory.
    */
   @Override
   public boolean getQosMetricIsNullPublishable()
   {
      return false;
   }

   /**
    * {@inheritDoc}
    * @implNote
    *
    * True for this service factory.
    */
   @Override
   public boolean getQosMetricIsBuffered()
   {
      return true;
   }

   /**
    * {@inheritDoc}
    * @implNote
    *
    * Unbounded in size for this service factory.
    */
   @Override
   public int getQosMetricBufferSizePerConsumer()
   {
      return Integer.MAX_VALUE;
   }


/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
