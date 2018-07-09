/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor;

/*- Imported packages --------------------------------------------------------*/

import java.util.function.Consumer;

/*- Interface Declaration ----------------------------------------------------*/

/**
 * An interface providing the ability to control a service whose job it is to
 * publish new monitor notification events to interested third parties.
 *
 * @param <T> the type of the new value.
 */
public interface MonitorNotificationService<T>
{

/*- Class Declaration --------------------------------------------------------*/
/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/
/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/

   /**
    * Publishes a new value.
    *
    * @param value the new value.
    */
   void publish( T value );

   /**
    * Indicates that the service should be brought to a state where it is
    * ready to process new publication requests.
    */
   void start();

   /**
    * Indicates that the service should be brought to a state where it will
    * no longer be required to process new publication requests.
    */
   public void dispose();

   /**
    * Indicates that the service should be brought to a state where it will
    * free up all underlying resources.
    */
   public void disposeAllResources();

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
