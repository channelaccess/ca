/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor.striped;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.epics.ca.impl.TypeSupports;
import org.epics.ca.impl.monitor.MonitorNotificationService;
import org.epics.ca.impl.monitor.blockingqueue.BlockingQueueMonitorNotificationServiceImpl;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class StripedExecutorServiceImpl<T> implements MonitorNotificationService<T>
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   // Get Logger
   private static final Logger logger = Logger.getLogger( StripedExecutorServiceImpl.class.getName() );

   private final Consumer<? super T> consumer;
   private final ExecutorService executorService;
   private final int qosExecutorThreads;

   private T deserializedValue;


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Constructs a new instance to work with the consumer using the specified
    * executor.
    *
    * @param executor provides the executor service needed by the library.
    *
    * @param qosExecutorThreads the number of threads on which consumer callback
    *        may take place
    *
    * @param consumer the consumer to be notified when a new value is published.
    */
   public StripedExecutorServiceImpl( ExecutorService executor, int qosExecutorThreads, Consumer<? super T> consumer )
   {
      Validate.notNull( executor );
      Validate.notNull( consumer );
      Validate.inclusiveBetween( 0, Integer.MAX_VALUE, qosExecutorThreads );

      this.executorService = executor;
      this.consumer = consumer;
      this.deserializedValue = null;
      this.qosExecutorThreads = qosExecutorThreads;
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
   public void start() {}

   /**
    * {@inheritDoc}
    *
    * @implNote
    * There is nothing to do here since the executor associated with this service is
    * potentially still needed to work with other Consumers.
    */
   @Override
   public void dispose() {}

   /**
    * {@inheritDoc}
    */
   @Override
   public void disposeAllResources()
   {
      shutdownExecutor();
   }

   /**
    * {@inheritDoc}
    *
    * @implNote
    * Returns the metric that was passed in during class construction.
    */
   @Override
   public int getQualityOfServiceNumberOfNotificationThreadsPerConsumer()
   {
      return qosExecutorThreads;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean getQualityOfServiceIsNullPublishable()
   {
      return false;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean getQualityOfServiceIsBuffered()
   {
      return true;
   }

   /**
   * {@inheritDoc}
    * @implNote
    *
    * Currently the buffer associated with this implementation is unbounded in size.
    */
   @Override
   public int getQualityOfServiceBufferSizePerConsumer()
   {
      return Integer.MAX_VALUE;
   }

/*- Private methods ----------------------------------------------------------*/

   private void shutdownExecutor()
{
   logger.log ( Level.FINEST, "Starting executor shutdown sequence..." );

   executorService.shutdown();
   try
   {
      logger.log ( Level.FINEST, "Waiting 5 seconds for tasks to finish..." );
      if ( executorService.awaitTermination(10, TimeUnit.SECONDS ) )
      {
         logger.log ( Level.FINEST, "Executor terminated ok." );
      }
      else
      {
         logger.log ( Level.FINEST, "Executor did not yet terminate. Forcing termination..." );
         executorService.shutdownNow();
         executorService.awaitTermination(10, TimeUnit.SECONDS );
      }
   }
   catch ( InterruptedException ex )
   {
      logger.log ( Level.FINEST, "Interrupted whilst waiting for tasks to finish. Propagating interrupt." );
      Thread.currentThread().interrupt();
   }
   logger.log ( Level.FINEST, "Executor shutdown sequence completed." );
}
/*- Nested Classes -----------------------------------------------------------*/

}
