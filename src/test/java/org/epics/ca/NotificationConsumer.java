/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import net.jcip.annotations.ThreadSafe;
import org.apache.commons.lang3.Validate;
import org.epics.ca.util.logging.LibraryLogManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

@ThreadSafe
public class NotificationConsumer<T> implements Consumer<T>
{
   
/*- Public attributes --------------------------------------------------------*/

   public enum ConsumerType
   {
      NORMAL,
      SLOW_WITH_THREAD_SLEEP,
      SLOW_WITH_BUSY_WAIT
   }


/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( NotificationConsumer.class );

   private static CountDownLatch expectedTotalNotificationCountDetectionLatch;
   private static final AtomicLong expectedTotalNotificationCount = new AtomicLong();
   private static final AtomicLong currentTotalNotificationCount = new AtomicLong();

   private final AtomicLong currentNotificationCount = new AtomicLong();
   private final AtomicLong expectedNotificationCount = new AtomicLong();
   private CountDownLatch expectedNotificationCountDetectionLatch;

   private final AtomicReference<T> lastNotificationValue = new AtomicReference<>();
   private final AtomicReference<T> expectedNotificationValue = new AtomicReference<>();
   private CountDownLatch expectedNotificationValueDetectionLatch;

   private final AtomicBoolean notificationSequenceWasMonotonic = new AtomicBoolean(true );
   private final AtomicBoolean notificationSequenceWasConsecutive = new AtomicBoolean( true );

   private final ConsumerType consumerType;
   private final long delayTimeInNanoseconds;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

public NotificationConsumer( ConsumerType type, long delayTime, TimeUnit timeUnit )
{
   Validate.notNull( type );
   Validate.isTrue(delayTime >= 0,"greater than zero" );
   this.consumerType = type;

   switch( timeUnit )
   {
      case NANOSECONDS:
         this.delayTimeInNanoseconds = delayTime;
         break;
      case MICROSECONDS:
         this.delayTimeInNanoseconds = delayTime * 1000L;
         break;
      case MILLISECONDS:
         this.delayTimeInNanoseconds = delayTime * 1_000_000L;
         break;
      case SECONDS:
         this.delayTimeInNanoseconds = delayTime * 1_000_000_000L;
         break;
      default:
         throw new RuntimeException( "Unsupported TimeUnit: " + timeUnit );
   }

   lastNotificationValue.set( null );
   currentNotificationCount.set( 0 );
}

/*- Class methods ------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/

   public static <T> NotificationConsumer<T> getNormalConsumer()
   {
      final long notUsedValue = 0L;
      return new NotificationConsumer<>(ConsumerType.NORMAL, notUsedValue, TimeUnit.SECONDS );
   }

   public static <T> NotificationConsumer<T> getThreadSleepingSlowConsumer( long delayTime, TimeUnit timeUnit )
   {
      return new NotificationConsumer<>(ConsumerType.SLOW_WITH_THREAD_SLEEP, delayTime, timeUnit );
   }

   public static <T> NotificationConsumer<T> getBusyWaitingSlowConsumer( long delayTime, TimeUnit timeUnit )
   {
      return new NotificationConsumer<>(ConsumerType.SLOW_WITH_BUSY_WAIT, delayTime, timeUnit );
   }

   public static long getCurrentTotalNotificationCount()
   {
      return currentTotalNotificationCount.get();
   }

   public static void clearCurrentTotalNotificationCount()
   {
      currentTotalNotificationCount.set( 0 );
   }

   public static void setExpectedTotalNotificationCount( int count )
   {
      expectedTotalNotificationCount.set( count );
      expectedTotalNotificationCountDetectionLatch = new CountDownLatch( 1 );
   }

   public long getCurrentNotificationCount()
   {
      return currentNotificationCount.get();
   }

   public void clearCurrentNotificationCount()
   {
      currentNotificationCount.set( 0 );
   }

   public void setExpectedNotificationCount( int count )
   {
      expectedNotificationCount.set( count );
      expectedNotificationCountDetectionLatch = new CountDownLatch( 1 );
   }

   public void setExpectedNotificationValue( T value )
   {
      expectedNotificationValue.set( value );
      expectedNotificationValueDetectionLatch = new CountDownLatch( 1 );
   }

   public void setNotificationSequenceWasMonotonic()
   {
      notificationSequenceWasMonotonic.set( true );
   }

   public void setNotificationSequenceWasConsecutive()
   {
      notificationSequenceWasConsecutive.set( true );
   }

   public boolean getNotificationSequenceWasMonotonic()
   {
      return notificationSequenceWasMonotonic.get();
   }

   public boolean getNotificationSequenceWasConsecutive()
   {
      return notificationSequenceWasConsecutive.get();
   }

   public static void awaitExpectedTotalNotificationCount()
   {
      try
      {
         expectedTotalNotificationCountDetectionLatch.await();
      }
      catch( InterruptedException ex )
      {
         logger.log(Level.FINEST, "Unexpectedly interrupted from await" );
         Thread.currentThread().interrupt();
      }
   }

   public void awaitExpectedNotificationCount()
   {
      try
      {
         expectedNotificationCountDetectionLatch.await();
      }
      catch( InterruptedException ex )
      {
         logger.log(Level.FINEST, "Unexpectedly interrupted from await" );
         Thread.currentThread().interrupt();
      }
   }

   public void awaitExpectedNotificationValue()
   {
      try
      {
         expectedNotificationValueDetectionLatch.await();
      }
      catch( InterruptedException ex )
      {
         logger.log(Level.FINEST, "Unexpectedly interrupted from await" );
         Thread.currentThread().interrupt();
      }
   }

   @Override
   public void accept( T newValue )
   {
//      logger.log(Level.INFO, String.format("Consumer: Thread: %s has notified me with value %s", Thread.currentThread(), newValue ) );

      // Sleep for the appropriate processing time according to the type of Consumer
      simulateProcessingTime( delayTimeInNanoseconds );

      // Accumulate the count of notifications
      currentNotificationCount.incrementAndGet();
      currentTotalNotificationCount.incrementAndGet();

      // If the expected notification value is seen open the gate

      if ( ( expectedNotificationValueDetectionLatch != null ) && ( newValue.equals( expectedNotificationValue.get() )  ) )
      {
         expectedNotificationValueDetectionLatch.countDown();
      }

      // If the expected notification count is reached open the gate
      if ( ( expectedNotificationCountDetectionLatch != null ) && ( currentNotificationCount.get() == expectedNotificationCount.get() ) )
      {
         expectedNotificationCountDetectionLatch.countDown();
      }

      // If the expected total notification count is reached open the gate
      if ( ( expectedTotalNotificationCountDetectionLatch != null ) && ( currentTotalNotificationCount.get() == expectedTotalNotificationCount.get() ) )
      {
         expectedTotalNotificationCountDetectionLatch.countDown();
      }

      // Perform the tests on the new value to determine whether it is consecutive and/or monotonic.
      // Do not trigger the test on the first notification because the lastNotification value is not yet valid.
      // Only tritgger the tests when the Type is either Integer or Long
      if ( ( lastNotificationValue.get() != null ) && ( lastNotificationValue.get() instanceof Integer ) && ( newValue instanceof Integer ) )
      {
         //noinspection RedundantCast
         if ( (Integer) newValue < (Integer) lastNotificationValue.get() )
         {
            notificationSequenceWasMonotonic.set( false );
         }
         if ( (Integer) newValue != (Integer) lastNotificationValue.get() + 1 )
         {
            notificationSequenceWasConsecutive.set( false );
         }
      }

      // Hold onto the latest notification value
      lastNotificationValue.set( newValue );

      logger.log(Level.FINEST, String.format("Consumer: Thread %s has finished consuming", Thread.currentThread() ) );
   }

   public T getLastNotificationValue()
   {
      return lastNotificationValue.get();
   }

   @Override
   public String toString()
   {
      return "TestConsumer<" + consumerType + "," + delayTimeInNanoseconds + '>';
   }


/*- Private methods ----------------------------------------------------------*/

   private void simulateProcessingTime( long delayTimeInNanoseconds )
   {
      // Impose the appropriate delay depending on the type of Consumer
      switch( consumerType )
      {
         case NORMAL:
         {
            break;
         }

         case SLOW_WITH_BUSY_WAIT:
         {
            busyWait( delayTimeInNanoseconds );
            break;
         }

         case SLOW_WITH_THREAD_SLEEP:
         {
            threadSleep( delayTimeInNanoseconds );
            break;
         }
      }
   }

   private static void threadSleep( long delaytimeInNanoseconds )
   {
      try
      {
         logger.log( Level.FINEST, String.format( "Sleep request for %d nanoseconds", delaytimeInNanoseconds) );
         long millis = ( delaytimeInNanoseconds / 1_000_000L );
         long nanos =  ( delaytimeInNanoseconds % 1_000_000L );
         logger.log( Level.FINEST, String.format( "Sleeping for %d millis and %d nanos", millis, nanos ) );
         Thread.sleep( millis, (int) nanos );
         logger.log( Level.FINEST, "Done with sleeping" );
      }
      catch( InterruptedException ex )
      {
         logger.log( Level.FINEST, "Unexpectedly awoke from sleep" );
         Thread.currentThread().interrupt();
      }
   }

   private static void busyWait( long delaytimeInNanoseconds )
   {
      long startTime = System.nanoTime();
      long endTime = startTime + delaytimeInNanoseconds;

      while( System.nanoTime() < endTime )
      {
         // doNothing
         noop();
         //logger.info( "Spinning..." );
      }
   }

   private static void noop() {}

/*- Nested Classes -----------------------------------------------------------*/

}


