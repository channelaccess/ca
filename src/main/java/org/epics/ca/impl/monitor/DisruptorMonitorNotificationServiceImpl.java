/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor;

/*- Imported packages --------------------------------------------------------*/

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import net.jcip.annotations.ThreadSafe;
import org.apache.commons.lang3.Validate;
import org.epics.ca.util.Holder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.function.Consumer;

@ThreadSafe
class DisruptorMonitorNotificationServiceImpl<T> implements MonitorNotificationService<T>
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LoggerFactory.getLogger( DisruptorMonitorNotificationServiceImpl.class);

   private final Consumer<T> consumer;
   private final ThreadFactory myThreadFactory;
   private final Disruptor<Holder<T>> disruptor;
   private final RingBuffer<Holder<T>> ringBuffer;
   private final MySpecialEventProducer producer;


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Creates a new monitor notifier based on the LMAX Disruptor technology.
    *
    * @param consumer the consumer to whom publish evenbts will be sent.
    */
   DisruptorMonitorNotificationServiceImpl( Consumer<T> consumer )
   {
      this.consumer = Validate.notNull( consumer );

      // The factory for the thread
      myThreadFactory = new MyThreadFactory();

      // Specify the size of the ring buffer, must be power of 2.
      final int bufferSize = 2;

      // Construct the Disruptor
      disruptor = new Disruptor(Holder::new, bufferSize, myThreadFactory);
      disruptor.handleEventsWith( new MySpecialEventHandler( consumer ));

      // Get the ring buffer from the Disruptor to be used for publishing.
      ringBuffer = disruptor.getRingBuffer();
      producer = new MySpecialEventProducer(ringBuffer);
   }


/*- Public methods -----------------------------------------------------------*/

   /**
    * {@inheritDoc}
    *
    * The implementation here starts a single thread to take events off the
    * Disruptor RingBuffer and to publish them to the Consumer.
    */
   @Override
   public void start()
   {
      disruptor.start();
   }

   /**
    * {@inheritDoc}
    *
    * The implementation here waits for all events to be processed
    */
   @Override
   public void dispose()
   {
      disruptor.shutdown();
   }

   @Override
   public void disposeAllResources()
   {

   }

   /**
    * @param value the new value.
    */
   @Override
   public void publish( T value )
   {
      producer.publish( value );
   }


/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

   // ThreadFactory that will be used to construct new threads for consumers
   static class MyThreadFactory implements ThreadFactory
   {
      private static int id=1;

      @Override
      public Thread newThread( Runnable r )
      {
         return new Thread(r, "DisruptorThread-" + String.valueOf( id++ ) );
      }
   };

   static class MySpecialEventProducer<T>
   {
      private final RingBuffer<Holder<T>> ringBuffer;

      public MySpecialEventProducer( RingBuffer<Holder<T>> ringBuffer)
      {
         this.ringBuffer = ringBuffer;
      }

      public void publish( T value )
      {
         long sequence;

         // Increment and return the next sequence for the ring buffer
         // or if there is no room simply overwrite the existing value
         if ( ringBuffer.hasAvailableCapacity (1 ) )
         {
            sequence = ringBuffer.next();
         }
         else
         {
            sequence = ringBuffer.getCursor();
         }

         try
         {
            // Get the entry in the Disruptor for the sequence
            Holder<T> event = ringBuffer.get(sequence);

            // Fill with data
            event.value = value;
         }
         finally
         {
            ringBuffer.publish(sequence);
         }
      }
   }

   static class MySpecialEventHandler<T> implements EventHandler<Holder<T>>, LifecycleAware
   {
      private final Consumer<T> consumer;

      public MySpecialEventHandler( Consumer<T> consumer )
      {
         this.consumer = consumer;
      }

      public void onEvent( Holder<T> event, long sequence, boolean endOfBatch) throws InterruptedException
      {
         //logger.info( "MySpecialEventHandler is digesting Event " + event.value + " on Thread: " + Thread.currentThread() + "... " );
         consumer.accept( event.value );
      }

      @Override
      public void onStart()
      {
         //logger.info( "MySpecialEventHandler started on Thread: " + Thread.currentThread() + "... " );
      }

      @Override
      public void onShutdown()
      {
         logger.info( "MySpecialEventHandler shutdown on Thread: " + Thread.currentThread() + "... " );
      }
   }

}


