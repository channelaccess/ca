/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor;

/*- Imported packages --------------------------------------------------------*/

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

import net.jcip.annotations.ThreadSafe;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

@ThreadSafe
class BlockingQueueMonitorNotificationServiceImpl<T> implements MonitorNotificationService<T>, Supplier<T>
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private final Logger logger = LoggerFactory.getLogger( BlockingQueueMonitorNotificationServiceImpl.class);

   private final ThreadPoolExecutor executor;
   private final Consumer<T> consumer;
   private final AtomicReference<Holder<T>> latestValue = new AtomicReference<>();


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
   BlockingQueueMonitorNotificationServiceImpl( ThreadPoolExecutor executor, Consumer<T> consumer  )
   {
      this.executor = Validate.notNull( executor );
      this.consumer = Validate.notNull( consumer );
   }

/*- Public methods -----------------------------------------------------------*/

   /**
    * {@inheritDoc}
    *
    * @param value the new value.
    */
   @Override
   public void publish( T value )
   {
      //Validate.notNull( value );

      if ( latestValue.getAndSet( new Holder( value ) ) == null )
      {
         //logger.info( "notifyConsumer: Queueing Task for consumer '{}' on work queue '{}'. Latest value is: {}", consumer, executor.getQueue().hashCode(), value );
         final MonitorNotificationTask<T> task = new MonitorNotificationTask<>(consumer, this );
         executor.submit( task );
      }
      else
      {
         //logger.info( "notifyConsumer: Update Task is already pending - latest value now set to {}", value );
      }
   }

   /**
    * {@inheritDoc}
    * <p>
    * The implementation here returns the latest published value.
    *
    * @return
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
   public void dispose() {}


   @Override
   public void disposeAllResources()
   {

   }

   /**
    *
    */
   public void shutdownAll()
   {
      logger.info ("Shutting down this executor" );
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



/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

   static class Holder<T>
   {
      T value;

      Holder( T value )
      {
         this.value = value;
      }
   }


}
