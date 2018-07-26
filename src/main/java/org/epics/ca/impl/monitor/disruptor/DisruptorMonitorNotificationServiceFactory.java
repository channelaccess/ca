/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor.disruptor;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.epics.ca.impl.monitor.MonitorNotificationServiceFactory;
import org.epics.ca.impl.monitor.MonitorNotificationService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class DisruptorMonitorNotificationServiceFactory implements MonitorNotificationServiceFactory, AutoCloseable
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = Logger.getLogger( DisruptorMonitorNotificationServiceFactory.class.getName() );
   private final List<MonitorNotificationService> serviceList = new ArrayList<>();
   private final boolean oldImpl;


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Creates a new factory with the capability of generating MonitorNotificationService
    * instances on a per Consumer basis. This factory provides service instances based
    * on the LMAX Disruptor technology.
    *
    * @param oldImpl determines the backwards compatibility mode for the Disruptor.
    *                True means the original Disruptor implementation will be used.
    */
   public DisruptorMonitorNotificationServiceFactory( boolean oldImpl )
   {
      this.oldImpl = oldImpl;

      final int numberOfThreadsBaseline = Thread.getAllStackTraces().keySet().size();
      logger.log( Level.INFO, String.format("The number of baseline threads in the system is: %d", numberOfThreadsBaseline));

      if ( oldImpl )
      {
         logger.log( Level.INFO, "A DisruptorMonitorNotificationServiceFactory is being created to work with the OLD Disruptor implementation..." );
      }
      else
      {
         logger.log( Level.INFO, "A DisruptorMonitorNotificationServiceFactory is being created to work with the NEW Disruptor implementation..." );
      }
   }

/*- Public methods -----------------------------------------------------------*/

   /**
    * {@inheritDoc}
    */
   @Override
   public <T> MonitorNotificationService<T> getServiceForConsumer( Consumer<? super T> consumer )
   {
      Validate.notNull( consumer );

      final MonitorNotificationService<T> instance = oldImpl ? new DisruptorOldMonitorNotificationService<>( consumer ) : new DisruptorNewMonitorNotificationService<>( consumer );
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
      logger.log(Level.INFO, String.format( "A DisruptorMonitorNotificationServiceFactory is being closed with %d service entries...", getServiceCount() ) );
      for ( MonitorNotificationService service : serviceList )
      {
         service.close();
      }
      serviceList.clear();
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
    * Always 1 for this service factory.
    *
    */
   @Override
   public int getQosMetricNumberOfNotificationThreadsPerConsumer()
   {
      return 1;
   }

   /**
    * {@inheritDoc}
    *
    * @implNote
    * True for this service factory.
    */
   @Override
   public boolean getQosMetricIsNullPublishable()
   {
      return true;
   }

   /**
    * {@inheritDoc}
    *
    * @implNote
    * This implementation has a minimal buffer holding the previous value only.
    * Thus, for all practical purposes it can be considered unbuffered when
    * it comes to smoothing out bursty traffic requests.
    */
   @Override
   public boolean getQosMetricIsBuffered()
   {
      return false;
   }

   /**
    * {@inheritDoc}
    *
    * @implNote
    * This implementation has a minimal buffer holding the previous value only.
    */
   @Override
   public int getQosMetricBufferSizePerConsumer()
   {
      return 2;
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
