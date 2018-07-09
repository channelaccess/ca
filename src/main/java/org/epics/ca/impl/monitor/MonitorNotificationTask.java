/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor;

import net.jcip.annotations.Immutable;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import java.util.function.Supplier;

/*- Imported packages --------------------------------------------------------*/
/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Runnable for transferring a monitor notification from a supplier to a consumer.
 *
 * @param <T> the type of the object to transfer. May sometimes refer to
 *  monitor metadata or simply the most recent monitor value.
 */
@Immutable
class MonitorNotificationTask<T> implements Runnable
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private final Logger logger = LoggerFactory.getLogger( MonitorNotificationTask.class );

   private final Supplier<T> valueSupplier;
   private final Consumer<T> valueConsumer;


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Constructs a new instance which when run will transfer a single value
    * object of type T from the supplier to the consumer.
    *
    * @param valueConsumer the consumer.
    * @param valueSupplier  the supplier.
    */
    MonitorNotificationTask( Consumer<T> valueConsumer, Supplier<T> valueSupplier )
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
         //logger.info( "Notifying consumer {} with value: {}... ", valueConsumer, latestValue );
         valueConsumer.accept(latestValue);
         //logger.info( "Notification completed ok" );
      }
      catch ( RuntimeException ex )
      {
         logger.info( "Unexpected exception during transfer> Message was: {} ", ex.toString() );
         ex.printStackTrace();
      }
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
