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
import org.epics.ca.impl.TypeSupports.TypeSupport;
import org.epics.ca.impl.monitor.MonitorNotificationService;
import org.epics.ca.util.Holder;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

@SuppressWarnings( "DuplicatedCode" )
@ThreadSafe
public class DisruptorOldMonitorNotificationService<T> implements MonitorNotificationService<T>
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = Logger.getLogger ( DisruptorOldMonitorNotificationService.class.getName ());

   // The size of the ring buffer, must be power of 2.
   private static final int NOTIFICATION_VALUE_BUFFER_SIZE = 2;

   private final Disruptor<Holder<T>> disruptor;

   private T overrunValue;
   private Holder<T> lastValue;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Creates a new monitor notifier based on the LMAX Disruptor technology.
    *
    * @param consumer the consumer to whom publish evenbts will be sent.
    */
   DisruptorOldMonitorNotificationService( Consumer<? super T> consumer )
   {
      Validate.notNull( consumer );

      // The factory for the thread
      final ThreadFactory myThreadFactory = new MyThreadFactory();

      // Construct the Disruptor. The size of the ring buffer, must be a power of 2.
      disruptor = new Disruptor<>(Holder::new, NOTIFICATION_VALUE_BUFFER_SIZE, myThreadFactory );

      disruptor.handleEventsWith (
         ( ringBuffer, barrierSequences ) ->
            new MonitorBatchEventProcessor<> (
               new MyAlwaysOnlineConnectionInterruptable(),
               new Holder<> (),
               (value) -> (value.value == null),
               disruptor.getRingBuffer(),
               ringBuffer.newBarrier( barrierSequences ),
               (e, s, eob) -> new MySpecialEventHandler<>( consumer ).onEvent( e, s, eob ) ) );
   }

/*- Public methods -----------------------------------------------------------*/

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean publish( ByteBuffer dataBuffer, TypeSupport<T> typeSupport, int dataCount )
   {
      Validate.notNull( dataBuffer );
      Validate.notNull( typeSupport );

      final RingBuffer<Holder<T>> ringBuffer = disruptor.getRingBuffer ();

      // Note: this is OK only for single producer
      if ( ringBuffer.hasAvailableCapacity (1) )
      {
         long next = ringBuffer.next();
         try
         {
            lastValue = ringBuffer.get( next );
            lastValue.value = typeSupport.deserialize( dataBuffer, lastValue.value, dataCount  );
         }
         finally
         {
            ringBuffer.publish( next );
         }
         return true;
      }
      else
      {
         overrunValue = typeSupport.deserialize( dataBuffer, overrunValue, dataCount );

         // nasty trick, swap the reference of the last value with overrunValue
         T tmp = lastValue.value;
         lastValue.value = overrunValue;
         overrunValue = tmp;
         return false;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean publish( T value )
   {
      final RingBuffer<Holder<T>> ringBuffer = disruptor.getRingBuffer ();

      //noinspection DuplicatedCode
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

   /**
    * {@inheritDoc}
    *
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
    */
   @Override
   public void close()
   {
      // TODO: examine whether this implementation is good enough to meet the service guarantee
      // TODO: that all threads should have been destroyed before exit.

      final int shutdownDelayInMilliseconds = 2000;
      final boolean useHaltImplementation = true;

      //noinspection ConstantConditions
      if ( useHaltImplementation )
      {
         // Note: this will not block or throw an exception
         disruptor.halt();
      }
      else
      {
         try
         {
            disruptor.shutdown();
            // This pause is to allow threads created within the disruptor to die.
            // Note: there is currently no guarantee of this.
            Thread.sleep( shutdownDelayInMilliseconds );
         }
         catch ( InterruptedException ex )
         {
            logger.log(Level.WARNING, "Interrupted whilst waiting for disruptor shutdown");
         }
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
         return new Thread(r, "DisruptorMonitorNotificationServiceThread-" + id++);
      }
   }

   static class MySpecialEventHandler<T> implements EventHandler<Holder<? extends T>>, LifecycleAware
   {
      private final Consumer<? super T> consumer;

      MySpecialEventHandler( Consumer<? super T> consumer )
      {
         this.consumer = consumer;
      }

      public void onEvent( Holder<? extends T> event, long sequence, boolean endOfBatch)
      {
         logger.log(Level.FINEST,"MySpecialEventHandler is digesting Event " + event.value + " on Thread: " + Thread.currentThread() + "... " );
         consumer.accept( event.value );
      }

      @Override
      public void onStart()
      {
         logger.log(Level.FINEST,"MySpecialEventHandler started on Thread: " + Thread.currentThread() + "... " );
      }

      @Override
      public void onShutdown()
      {
         logger.log(Level.FINEST,"MySpecialEventHandler shutdown on Thread: " + Thread.currentThread() + "... " );
      }
   }

   static class MyAlwaysOnlineConnectionInterruptable implements ConnectionInterruptable
   {
      @Override
      public int getConnectionLossId()
      {
         return 0;
      }
   }

}


