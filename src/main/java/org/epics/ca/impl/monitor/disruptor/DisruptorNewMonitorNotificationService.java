/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor.disruptor;

/*- Imported packages --------------------------------------------------------*/
/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.LifecycleAware;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import net.jcip.annotations.ThreadSafe;
import org.apache.commons.lang3.Validate;
import org.epics.ca.impl.TypeSupports;
import org.epics.ca.impl.monitor.MonitorNotificationService;
import org.epics.ca.util.Holder;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

@ThreadSafe
public class DisruptorNewMonitorNotificationService<T> implements MonitorNotificationService<T>
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = Logger.getLogger( DisruptorNewMonitorNotificationService.class.getName() );

   // The size of the ring buffer, must be power of 2.
   private static final int NOTIFICATION_VALUE_BUFFER_SIZE = 2;

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
   DisruptorNewMonitorNotificationService( Consumer<? super T> consumer )
   {
      Validate.notNull( consumer );

      // The factory for the thread
      final ThreadFactory myThreadFactory = new MyThreadFactory();

      // Construct the Disruptor. The size of the ring buffer, must be a power of 2.
      disruptor = new Disruptor<>( Holder::new, NOTIFICATION_VALUE_BUFFER_SIZE, myThreadFactory );

      EventHandler eventHandler = new MySpecialEventHandler<>( consumer );
      disruptor.handleEventsWith( eventHandler );

      // Get the ring buffer from the Disruptor to be used for publishing.
      producer = new MySpecialEventProducer<>( disruptor.getRingBuffer() );
      this.deserializedValue = null;
   }


/*- Public methods -----------------------------------------------------------*/

   /**
    * {@inheritDoc}
    *
    * @implNote
    * Since this service implementation always overwrites the oldest data
    */
   @Override
   public boolean publish( ByteBuffer dataBuffer, TypeSupports.TypeSupport<T> typeSupport, int dataCount )
   {
      Validate.notNull( dataBuffer );
      Validate.notNull( typeSupport );

      // The deserializer is optimised to reuse the same data structure thus
      // avoiding the cost of object creation
      deserializedValue = typeSupport.deserialize (dataBuffer, deserializedValue, dataCount );
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
      return producer.publish( value );
   }

   /**
    * {@inheritDoc}
    *
    * @implNote
    * The implementation here starts a single thread to take events off the
    * Disruptor RingBuffer and to publish them to the Consumer.
    */
   @Override
   public void init()
   {
      disruptor.start();
   }

   /**
    * {@inheritDoc}
    *
    * @implNote
    * The implementation here waits for all events to be processed then shuts down the executor.
    */
   @Override
   public void close()
   {
      try
      {
         disruptor.shutdown();

         // TODO: examine whether this is good enough to meet the service guarantee
         // TODO: that all threads should have been destroyed before exit.
         // This pause is to allow threads created within the disruptor to die.
         // Note: there is currently no guarantee of this.
         Thread.sleep( 2000 );
      }
      catch ( InterruptedException ex )
      {
         logger.log( Level.WARNING, "Interrupted whilst waiting for disruptor shutdown" );
      }
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
         return new Thread( r, "DisruptorMonitorNotificationServiceThread-" + String.valueOf( id++ ) );
      }
   }

   static class MySpecialEventProducer<T>
   {
      private final RingBuffer<Holder<T>> ringBuffer;

      MySpecialEventProducer( RingBuffer<Holder<T>> ringBuffer )
      {
         this.ringBuffer = ringBuffer;
      }

      public boolean publish( T value )
      {
         if ( ringBuffer.hasAvailableCapacity (1 ) )
         {
            long nextSequence = ringBuffer.next();
            try
            {
               // Get the entry in the Disruptor for the sequence
               final Holder<T> nextEventHolder = ringBuffer.get( nextSequence );

               // Fill with data
               nextEventHolder.value = value;
            }
            finally
            {
               ringBuffer.publish( nextSequence );
            }
            return true;
         }
         else
         {
            long oldestSequence = ringBuffer.getCursor();
            final Holder<T> oldestEventHolder = ringBuffer.get( oldestSequence );
            oldestEventHolder.value = value;
            return false;
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

      public void onEvent( Holder<T> event, long sequence, boolean endOfBatch )
      {
         logger.log( Level.FINEST, String.format( "MySpecialEventHandler: Consuming Event - START. Sequence Number is: %d, Value is: %s ", sequence, event.value ) );
         consumer.accept( event.value );
         logger.log( Level.FINEST, String.format( "MySpecialEventHandler: Consuming Event - FINISH. Sequence Number is: %d, Value was: %s ", sequence, event.value ) );
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
