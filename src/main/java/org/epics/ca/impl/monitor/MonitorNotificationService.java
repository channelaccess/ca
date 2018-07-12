/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor;

/*- Imported packages --------------------------------------------------------*/
/*- Interface Declaration ----------------------------------------------------*/

import org.epics.ca.impl.TypeSupports.TypeSupport;

import java.nio.ByteBuffer;

/**
 * An interface providing the ability to control a service whose job it is to
 * publish new monitor notification events of a particular type to interested
 * third party consumers.
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
    * Publishes a new value to a monitor's subscriber using a value that is
    * deserialized from the supplied data buffer.
    *
    * This method places the responsibility of value deserialization on the service
    * implementor. This provides some extra flexibility and opens the possibility
    * of reusing data structures htus avoiding the cost of object creation.
    *
    * @param dataBuffer the byte buffer containing the new value (which must
    *        first be deserialized).
    *
    * @param typeSupport reference to an object which has the capability of
    *        deserializing the information in the byte buffer.
    *
    * @param dataCount the number of items in the buffer to be deserialized.
    */
    void publish( ByteBuffer dataBuffer, TypeSupport<T> typeSupport, int dataCount );

   /**
    * Publishes a new value to a monitor's subscriber.
    *
    * @param value the new value.
    */
   void publish( T value );

   /**
    * Brings the service to a state where it is ready to process new publication requests.
    */
   void start();

   /**
    * Brings the service to a state where it will no longer accept new publication requests.
    */
   void dispose();

   /**
    * Brings the service to a state where it has released all underlying resources.
    */
   void disposeAllResources();


/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
