/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor;

import net.jcip.annotations.Immutable;
import org.apache.commons.lang3.Validate;
import org.epics.ca.impl.BroadcastTransport;

import java.text.MessageFormat;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/*- Imported packages --------------------------------------------------------*/
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
   private static final Logger logger = Logger.getLogger( MonitorNotificationTask.class.getName() );

   private final Supplier<? extends T> valueSupplier;
   private final Consumer<? super T> valueConsumer;


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Constructs a new instance which when run will transfer a single value
    * object of type T from the Supplier to the Consumer.
    *
    * @param valueConsumer the consumer.
    * @param valueSupplier  the supplier.
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
      try
      {
         final T latestValue = valueSupplier.get();
         logger.log( Level.FINEST, "Notifying consumer '%s' with value: '%s'... ", new Object[] { valueConsumer, latestValue } );
         valueConsumer.accept(latestValue);
         logger.log( Level.FINEST, "Notification completed ok" );
      }
      catch ( RuntimeException ex )
      {
         logger.log( Level.WARNING,"Unexpected exception during transfer. Message was: '%s'", ex );
         ex.printStackTrace();
      }
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
