/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor;

/*- Imported packages --------------------------------------------------------*/
/*- Interface Declaration ----------------------------------------------------*/

import org.epics.ca.impl.TypeSupports.TypeSupport;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An interface providing the ability to control a service whose job it is to
 * publish new monitor notification events of a particular type to interested
 * third party consumers (in EPICS terms the monitor's subscribers).
 *
 * @param <T> the type of the new value.
 */
public interface MonitorNotificationService<T> extends AutoCloseable
{

/*- Class Declaration --------------------------------------------------------*/
/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/
/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/

   /**
    * Publishes a new value to a monitor's subscriber using a value that must
    * first be deserialized from the supplied data buffer.
    *
    * This method places the responsibility of value deserialization on the service
    * implementor. This provides the extra flexibility to reuse data structures and
    * to avoid the cost of object creation.
    *
    * @param dataBuffer the byte buffer containing the new value (which must
    *        first be deserialized).
    *
    * @param typeSupport reference to an object which has the capability of
    *        deserializing the information in the byte buffer.
    *
    * @param dataCount the number of items in the buffer to be deserialized.
    *
    * @return true when the new value was accepted without any loss of data;
    *         false if the oldest value in the notification buffer was discarded.
    *
    */
   boolean publish( ByteBuffer dataBuffer, TypeSupport<T> typeSupport, int dataCount );

   /**
    * Publishes a new value to a monitor's subscriber (which this library models as
    * a Java Consumer) using the value that is  directly supplied.
    *
    * The new value will be published to the Consumer on one or more notification
    * threads. Where the subscriber is not immediately able to process it (eg the
    * previous call to the Consumer is blocked) the value may optionally be
    * buffered.
    *
    * If the value notification buffer becomes full the oldest value in the
    * buffer is thrown away.
    *
    * @param value the new value.
    *
    * @return true when the new value was accepted without any loss of data;
    *         false if the oldest value in the notification buffer was discarded.
    */
   boolean publish( T value );

   /**
    * Brings this service to a state where it is ready to process new publication requests.
    */
   void init();

   /**
    * Brings this service to a state where it has disposed of its resources.
    * Threads that were created within the service are guaranteed to be
    * destroyed.
    */
   void close();

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
