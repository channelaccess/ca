/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor;

/*- Imported packages --------------------------------------------------------*/

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import net.jcip.annotations.ThreadSafe;
import org.apache.commons.lang3.Validate;
import org.epics.ca.impl.BroadcastTransport;
import org.epics.ca.impl.TypeSupports;
import org.epics.ca.util.Holder;

import java.nio.ByteBuffer;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

@ThreadSafe
class DisruptorMonitorNotificationServiceNewImpl<T> implements MonitorNotificationService<T>
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   // Get Logger
   private static final Logger logger = Logger.getLogger ( DisruptorMonitorNotificationServiceNewImpl.class.getName ());

   private final Disruptor<Holder<T>> disruptor;
   private final MySpecialEventProducer<T> producer;

   private T deserializedValue;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Creates a new monitor notifier based on the LMAX Disruptor technology.
    *
    * @param consumer the consumer to whom publish events will be sent.
    */
   DisruptorMonitorNotificationServiceNewImpl( Consumer<? super T> consumer )
   {
      Validate.notNull( consumer );

      // The factory for the thread
      final ThreadFactory myThreadFactory = new MyThreadFactory();

      // Specify the size of the ring buffer, must be power of 2.
      final int bufferSize = 2;

      // Construct the Disruptor
      disruptor = new Disruptor<>(Holder::new, bufferSize, myThreadFactory);
      disruptor.handleEventsWith( new MySpecialEventHandler<>( consumer ));

      // Get the ring buffer from the Disruptor to be used for publishing.
      final RingBuffer<Holder<T>> ringBuffer = disruptor.getRingBuffer();
      producer = new MySpecialEventProducer<>( ringBuffer );

      this.deserializedValue = null;
   }


/*- Public methods -----------------------------------------------------------*/

   /**
    * {@inheritDoc}
    */
   @Override
   public void publish( ByteBuffer dataBuffer, TypeSupports.TypeSupport<T> typeSupport, int dataCount )
   {
      Validate.notNull( dataBuffer );
      Validate.notNull( typeSupport );

      // The deserializer is optimised to reuse the same data structure thus
      // avoiding the cost of object creation
      deserializedValue = typeSupport.deserialize (dataBuffer, deserializedValue, dataCount );
      publish( deserializedValue );
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void publish( T value )
   {
      producer.publish( value );
   }

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
    * The implementation here waits for all events to be processed then
    * shuts down the executor.
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
   }

   static class MySpecialEventProducer<T>
   {
      private final RingBuffer<Holder<T>> ringBuffer;

      MySpecialEventProducer( RingBuffer<Holder<T>> ringBuffer)
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
            Holder<T> event = ringBuffer.get( sequence );

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
      private final Consumer<? super T> consumer;

      MySpecialEventHandler( Consumer<? super T> consumer )
      {
         this.consumer = consumer;
      }

      public void onEvent( Holder<T> event, long sequence, boolean endOfBatch)
      {
         logger.log( Level.FINEST, "MySpecialEventHandler is digesting Event " + event.value + " on Thread: " + Thread.currentThread() + "... " );
         consumer.accept( event.value );
      }

      @Override
      public void onStart()
      {
         logger.log( Level.FINEST,"MySpecialEventHandler started on Thread: " + Thread.currentThread() + "... " );
      }

      @Override
      public void onShutdown()
      {
         logger.log( Level.FINEST,"MySpecialEventHandler shutdown on Thread: " + Thread.currentThread() + "... " );
      }
   }

}


