/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor.striped;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.epics.ca.impl.TypeSupports;
import org.epics.ca.impl.monitor.MonitorNotificationService;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class StripedExecutorServiceMonitorNotificationService<T> implements MonitorNotificationService<T>
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   // Get Logger
   private static final Logger logger = Logger.getLogger( StripedExecutorServiceMonitorNotificationService.class.getName() );

   private final StripedExecutorServiceMonitorNotificationServiceFactory factory;
   private final Consumer<? super T> consumer;
   private final ExecutorService executorService;

   private T deserializedValue;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Constructs a new instance to work with the consumer using the specified
    * executor.
    *
    * @param factory reference to the factory who created this instance.
    * @param executor provides the executor service needed by the library.
    * @param consumer the consumer to be notified when a new value is published.
    */
   StripedExecutorServiceMonitorNotificationService( StripedExecutorServiceMonitorNotificationServiceFactory factory, ExecutorService executor, Consumer<? super T> consumer )
   {
      this.factory= Validate.notNull( factory );
      this.executorService = Validate.notNull( executor );
      this.consumer = Validate.notNull( consumer );

      this.deserializedValue = null;
   }

   /**
    * {@inheritDoc}
    *
    * @implNote
    * Since this service implementation uses an unbounded queue this method always returns true.
    */
   @Override
   public boolean publish( ByteBuffer dataBuffer, TypeSupports.TypeSupport<T> typeSupport, int dataCount )
   {
      Validate.notNull( dataBuffer );
      Validate.notNull( typeSupport );
      Validate.inclusiveBetween( 0, Integer.MAX_VALUE, dataCount);

      // The deserializer is optimised to reuse the same data structure thus
      // avoiding the cost of object creation
      deserializedValue = typeSupport.deserialize( dataBuffer, deserializedValue , dataCount );

      return publish( deserializedValue );
   }

   /**
    * {@inheritDoc}
    *
    * @implNote
    * Since this service implementation uses an unbounded queue this method always returns true.
    */
   @Override
   public boolean publish( T value )
   {
      final StripedMonitorNotificationTask<T> task = new StripedMonitorNotificationTask<>( consumer, value );
      logger.log(Level.FINEST, String.format( "Submitting task on stripe: '%s' ", task.getStripe() ) );
      executorService.submit( task );

      return true;
   }

   /**
    * {@inheritDoc}
    *
    * @implNote
    * There is nothing to do here. The service should already have been handed an executor
    * that is already in a viable state to cooperatively work with multiple consumers.
    */
   @Override
   public void init() {}

   /**
    * {@inheritDoc}
    *
    * @implNote
    * There is nothing to do here since the executor associated with this service is
    * potentially still needed to work with other Consumers.
    */
   @Override
   public void close() {}

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
