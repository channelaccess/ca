/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor;

/*- Imported packages --------------------------------------------------------*/

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.LifecycleAware;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import net.jcip.annotations.ThreadSafe;
import org.apache.commons.lang3.Validate;
import org.epics.ca.Channel;
import org.epics.ca.impl.ChannelImpl;
import org.epics.ca.impl.ContextImpl;
import org.epics.ca.impl.disruptor.ConnectionInterruptable;
import org.epics.ca.impl.disruptor.MonitorBatchEventProcessor;
import org.epics.ca.util.Holder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

@ThreadSafe
class DisruptorMonitorNotificationServiceImpl2<T> implements MonitorNotificationService<T>
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LoggerFactory.getLogger( DisruptorMonitorNotificationServiceImpl2.class);

   private final ThreadFactory myThreadFactory;
   private final Disruptor<Holder<T>> disruptor;
   private final RingBuffer<Holder<T>> ringBuffer;

   private T overrunValue;
   private Holder<T> lastValue;



/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Creates a new monitor notifier based on the LMAX Disruptor technology.
    *
    * @param consumer the consumer to whom publish evenbts will be sent.
    */
   DisruptorMonitorNotificationServiceImpl2( Consumer<T> consumer )
   {
      Validate.notNull( consumer );

      // The factory for the thread
      myThreadFactory = new MyThreadFactory();

      // Specify the size of the ring buffer, must be power of 2.
      final int bufferSize = 2;

      // Construct the Disruptor
      disruptor = new Disruptor(Holder::new, bufferSize, myThreadFactory);

      disruptor.handleEventsWith (
         ( ringBuffer, barrierSequences ) ->
            new MonitorBatchEventProcessor<Holder<T>> (
               new MyAlwaysOnlineConnectionInterruptable(),
               new Holder<T> (),
               (value) -> (value.value == null),
               disruptor.getRingBuffer(),
               ringBuffer.newBarrier (barrierSequences),
               (e, s, eob) -> new MySpecialEventHandler( consumer ).onEvent( e, s, eob ) ) );

      // Get the ring buffer from the Disruptor to be used for publishing.
      ringBuffer = disruptor.getRingBuffer();
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
      RingBuffer<Holder<T>> ringBuffer = disruptor.getRingBuffer ();
      // this is OK only for single producer
      if ( ringBuffer.hasAvailableCapacity (1) )
      {
         long next = ringBuffer.next ();
         try
         {
            lastValue = ringBuffer.get (next);
            lastValue.value = value;
         }
         finally
         {
            ringBuffer.publish (next);
         }
      }
      else
      {
         overrunValue = value;

         // nasty trick, swap the reference of the last value with overrunValue
         T tmp = lastValue.value;
         lastValue.value = overrunValue;
         overrunValue = tmp;
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
         return new Thread(r, "DisruptorThread-" + String.valueOf( id++ ) );
      }
   };


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
         logger.info( "MySpecialEventHandler started on Thread: " + Thread.currentThread() + "... " );
      }

      @Override
      public void onShutdown()
      {
         logger.info( "MySpecialEventHandler shutdown on Thread: " + Thread.currentThread() + "... " );
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


