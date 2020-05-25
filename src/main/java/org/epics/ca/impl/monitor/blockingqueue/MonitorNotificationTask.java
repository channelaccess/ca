/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor.blockingqueue;

/*- Imported packages --------------------------------------------------------*/

import net.jcip.annotations.Immutable;
import org.apache.commons.lang3.Validate;
import org.epics.ca.util.logging.LibraryLogManager;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;


/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Runnable for transferring a monitor notification from a supplier to a consumer.
 *
 * @param <T> the type of the object to transfer. May sometimes refer to
 *           monitor metadata or simply the most recent monitor value.
 */
@Immutable
class MonitorNotificationTask<T> implements Runnable
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   // Get Logger
   private static final Logger logger = LibraryLogManager.getLogger( MonitorNotificationTask.class );

   private final Supplier<? extends T> valueSupplier;
   private final Consumer<? super T> valueConsumer;


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Constructs a new instance which when run will transfer a single value
    * object of type T from the Supplier to the Consumer.
    *
    * @param valueConsumer the consumer.
    * @param valueSupplier the supplier.
    */
    MonitorNotificationTask( Consumer<? super T> valueConsumer, Supplier<? extends T> valueSupplier )
    {
       this.valueConsumer = Validate.notNull( valueConsumer );
       this.valueSupplier = Validate.notNull( valueSupplier );
    }

/*- Public methods -----------------------------------------------------------*/

   /**
    * Transfers a single value from the supplier to the consumer.
    */
   @Override
   public void run()
   {
      // This synchronization lock is essential to ensure that Consumers get
      // called in the same order that notification values were obtained from
      // the Supplier. However, it violates the principle that it is a bad
      // idea to make an open call whilst holding a synchronization lock.
      //
      // In the current situation I do not believe this to be a problem but
      // for more information see this article:
      // https://www.javaworld.com/article/2075692/java-concurrency/avoid-synchronization-deadlocks.html
      synchronized( this )
      {
         try
         {
            final T latestValue = valueSupplier.get();
            logger.finest( String.format("Notifying consumer '%s' with value: '%s'... ", valueConsumer, latestValue ) );
            valueConsumer.accept(latestValue);
            logger.finest( "Notification completed ok" );
         }
         catch ( RuntimeException ex )
         {
            logger.log(Level.WARNING, String.format("Unexpected exception during transfer. Message was: '%s'", ex ) );
            ex.printStackTrace();
         }
      }
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
