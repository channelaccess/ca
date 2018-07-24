/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor.blockingqueue;

/*- Imported packages --------------------------------------------------------*/

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

import net.jcip.annotations.ThreadSafe;
import org.apache.commons.lang3.Validate;
import org.epics.ca.impl.TypeSupports;
import org.epics.ca.impl.monitor.MonitorNotificationService;

import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

@ThreadSafe
public class BlockingQueueMonitorNotificationServiceImpl<T> implements MonitorNotificationService<T>, Supplier<T>
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   // Get Logger
   private static final Logger logger = Logger.getLogger( BlockingQueueMonitorNotificationServiceImpl.class.getName() );

   private final ThreadPoolExecutor executor;
   private final Consumer<? super T> consumer;
   private final BlockingQueue<T> valueQueue;

   private final int qosNotificationBufferSize;
   private final int qosNumberOfNotificationThreadsPerConsumer;

   private T deserializedValue;


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Creates a new monitor notifier based on a standard Java BlockingQueue
    * and a ThreadPoolExecutor.
    *
    * The supplied executor should be appropriately configured according to
    * the needs of the service. For example it should leverage off the
    * appropriate type of BlockingQueue (bounded or unbounded) and support
    * the appropriate number of threads (single or multiple).
    *
    * Similarly the supplied queue should be appropriately configured (it
    * could be bounded or unbounded).
    *
    * @param executor the executor.
    * @param valueQueue the consumer's value notification queue.
    * @param consumer the consumer to whom published values will be sent.
    *
    * @throws NullPointerException if the executor was null.
    * @throws NullPointerException if the consumer was null.
    */
   public BlockingQueueMonitorNotificationServiceImpl( ThreadPoolExecutor executor, BlockingQueue<T> valueQueue, Consumer<? super T> consumer  )
   {
      this.executor = Validate.notNull( executor );
      this.valueQueue = Validate.notNull( valueQueue );
      this.consumer = Validate.notNull( consumer );

      this.deserializedValue = null;

      // The Quality of Service indicators can be deduced by interrogating the
      // objects which are passed in.
      this.qosNotificationBufferSize = valueQueue.remainingCapacity();
      this.qosNumberOfNotificationThreadsPerConsumer = executor.getCorePoolSize();
   }

/*- Public methods -----------------------------------------------------------*/

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean getQualityOfServiceIsBuffered()
   {
      return ( qosNotificationBufferSize > 1 );
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int getQualityOfServiceBufferSizePerConsumer()
   {
      return qosNotificationBufferSize;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int getQualityOfServiceNumberOfNotificationThreadsPerConsumer()
   {
      return qosNumberOfNotificationThreadsPerConsumer;
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
    *
    * @implNote
    * This implementation does not accept null as a valid publication value.
    *
    * @throws NullPointerException if the passed value was null.
    */
   @Override
   public boolean publish( ByteBuffer dataBuffer, TypeSupports.TypeSupport<T> typeSupport, int dataCount )
   {
      // The deserializer is optimised to reuse the same data structure thus
      // avoiding the cost of object creation
      deserializedValue = typeSupport.deserialize( dataBuffer, deserializedValue , dataCount );
      return publish( deserializedValue );
   }

   /**
    * {@inheritDoc}
    *
    * @implNote
    * This implementation does not accept null as a publication value.
    *
    * @throws NullPointerException if the passed value was null.
    */
   @Override
   synchronized public boolean publish( T value )
   {
      Validate.notNull( value );

      // Add the latest value to the tail of the notification queue, where necessary evicting
      // the oldest value to ensure success.
      boolean overrun = false;
      if ( ! valueQueue.offer( value ) )
      {
         logger.log( Level.FINEST, String.format( "Buffer is full [size is: %d]", valueQueue.size() ) );
         overrun = true;
         final T discardedValue = valueQueue.remove();
         logger.log( Level.FINEST, String.format( "Removing and throwing away oldest queue item, %s", discardedValue ) );

         // Theoretically this call could throw an IllegalStateException but it should
         // not do so since the previous remove operation should now guarantee success.
         valueQueue.add( value );
      }
      else
      {
         logger.log(Level.FINEST, String.format("Added new item to buffer [size is: %d]", valueQueue.size() ) );

         // In the case that there is a new notifcation item in the queue create a new task to pass the
         // value on to the consumer.
         logger.log( Level.FINEST, String.format( "Queueing Task for consumer '%s' on work queue '%s'. Latest value is: '%s'", consumer, executor.getQueue().hashCode(), value ) );
         executor.submit( new MonitorNotificationTask<>( consumer, this ) );
      }

      // Return true for success; false if there was a buffer overrun.
      return ! overrun;
   }

   /**
    * {@inheritDoc}
    *
    * @implNote
    * This implementation returns the oldest value from the notification queue.
    */
   @Override
   synchronized public T get()
   {
      // Check the precondition has not been violated. If it has there is a programming error
      Validate.isTrue( ! valueQueue.isEmpty(), "programming error - value notification queue was unexpectedly empty" );

      final T value = valueQueue.remove();
      logger.log( Level.FINEST, String.format( "Retrieved value '%s'", value ) );

      // Get the oldest value from the head of the notification value queue
      return value;
   }

   /**
    * {@inheritDoc}
    * <p>
    * The implementation here does not need to do anything since the service leverages
    * off a shared ThreadPoolExecutor whose lifecycle is managed outside the scope of
    * this object's lifetime.
    */
   @Override public void start() {}

   /**
    * {@inheritDoc}
    * <p>
    * The implementation here does not need to do anything since the service leverages
    * off a shared ThreadPoolExecutor whose lifecycle is managed outside the scope of
    * this object's lifetime.
    */
   @Override
   public void dispose()
   {

   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void disposeAllResources()
   {
      shutdownExecutor();
   }


/*- Private methods ----------------------------------------------------------*/

   private void shutdownExecutor()
   {
      logger.log ( Level.FINEST, "Starting executor shutdown sequence..." );

      executor.shutdown();
      try
      {
         logger.log ( Level.FINEST, "Waiting 5 seconds for tasks to finish..." );
         if ( executor.awaitTermination(10, TimeUnit.SECONDS ) )
         {
            logger.log ( Level.FINEST, "Executor terminated ok." );
         }
         else
         {
            logger.log ( Level.FINEST, "Executor did not yet terminate. Forcing termination..." );
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS );
         }
      }
      catch ( InterruptedException ex )
      {
         logger.log ( Level.WARNING, "Interrupted whilst waiting for tasks to finish. Propagating interrupt." );
         Thread.currentThread().interrupt();
      }
      logger.log ( Level.FINEST, "Executor shutdown sequence completed." );
   }

/*- Nested Classes -----------------------------------------------------------*/

}
