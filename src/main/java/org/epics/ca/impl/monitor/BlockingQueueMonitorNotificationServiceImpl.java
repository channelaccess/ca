/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor;

/*- Imported packages --------------------------------------------------------*/

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

import net.jcip.annotations.ThreadSafe;
import org.apache.commons.lang3.Validate;
import org.epics.ca.impl.BroadcastTransport;
import org.epics.ca.impl.TypeSupports;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

@ThreadSafe
class BlockingQueueMonitorNotificationServiceImpl<T> implements MonitorNotificationService<T>, Supplier<T>
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   // Get Logger
   private static final Logger logger = Logger.getLogger( BlockingQueueMonitorNotificationServiceImpl.class.getName() );

   private final ThreadPoolExecutor executor;
   private final Consumer<? super T> consumer;

   private final AtomicReference<Holder<T>> latestValue = new AtomicReference<>();
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
    * @param executor the executor.
    * @param consumer the consumer to whom publish evenbts will be sent.
    *
    * @throws NullPointerException if the executor was null.
    * @throws NullPointerException if the consumer was null.
    */
   BlockingQueueMonitorNotificationServiceImpl( ThreadPoolExecutor executor, Consumer<? super T> consumer  )
   {
      this.executor = Validate.notNull( executor );
      this.consumer = Validate.notNull( consumer );
      this.deserializedValue = null;
   }

/*- Public methods -----------------------------------------------------------*/

   /**
    * {@inheritDoc}
    */
   @Override
   public void publish( ByteBuffer dataBuffer, TypeSupports.TypeSupport<T> typeSupport, int dataCount )
   {
      // The deserializer is optimised to reuse the same data structure thus
      // avoiding the cost of object creation
      deserializedValue = typeSupport.deserialize( dataBuffer, deserializedValue , dataCount );
      publish( deserializedValue );
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void publish( T value )
   {
      // This may or may not be appropriate depending on the contract
      // Validate.notNull( value );

      if ( latestValue.getAndSet( new Holder<>( value ) ) == null )
      {
         logger.log( Level.FINEST, String.format( "notifyConsumer: Queueing Task for consumer '%s' on work queue '%s'. Latest value is: '%s'", consumer, executor.getQueue().hashCode(), value ) );
         final MonitorNotificationTask<T> task = new MonitorNotificationTask<>( consumer, this );
         executor.submit( task );
      }
      else
      {
         logger.log(Level.FINEST,"publish: Update Task is already pending - latest value now set to '%s'", value.toString() );
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public T get()
   {
      return latestValue.getAndSet( null ).value;
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
      shutdownAll();
   }


/*- Private methods ----------------------------------------------------------*/

   private void shutdownAll()
   {
      logger.log ( Level.INFO, "Shutting down this executor" );
      executor.shutdown();
      try
      {
         executor.awaitTermination(10, TimeUnit.SECONDS);
      }
      catch ( InterruptedException ex )
      {
         Thread.currentThread().interrupt();
      }
   }

/*- Nested Classes -----------------------------------------------------------*/

   // Note: this class is only needed if the service needs the ability to
   // publish null values.
   static class Holder<T>
   {
      final T value;

      Holder( T value )
      {
         this.value = value;
      }
   }


}
