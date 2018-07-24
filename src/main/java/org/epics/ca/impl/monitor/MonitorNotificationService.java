/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor;

/*- Imported packages --------------------------------------------------------*/
/*- Interface Declaration ----------------------------------------------------*/

import org.epics.ca.impl.TypeSupports.TypeSupport;

import java.nio.ByteBuffer;

/**
 * An interface providing the ability to control a service whose job it is to
 * publish new monitor notification events of a particular type to interested
 * third party consumers (in EPICS terms the monitor's subscribers).
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
   void start();

   /**
    * Brings this service to a state where it has released any resources that it can and
    * where it will no longer accept new publication requests.
    */
   void dispose();

   /**
    * Brings the service to a state where it has released all underlying resources.
    */
   void disposeAllResources();

   /**
    * Returns an indication of whether this service implementation can be expected
    * to drop notifications or whether it will buffer them in situations where
    * the publication rate exceeds the rate at which the consumers can process the
    * data.
    *
    * @return the result.
    */
   boolean getQualityOfServiceIsBuffered();

   /**
    * Returns an indication of the size of each consumer's notification buffer
    * (for service implementations which provide buffering).
    *
    * Returns 1 in the case that the service implementation is not buffered.
    *
    * @return the result.
    */
   int getQualityOfServiceBufferSizePerConsumer();

   /**
    * Returns an indication of how many threads the consumer may be called back on.
    * Where multiple threads are involved the consumer may wish to synchronize their
    * accept method to force serialisation of the notification sequence so that
    * events get notified in the same sequence as they were published.
    *
    * @return the result.
    */
   int getQualityOfServiceNumberOfNotificationThreadsPerConsumer();


   /**
    * Returns an indication of whether the publish method accepts null
    * as a valid token to be sent to the Coinsumer.
    *
    * @return the result.
    */
   boolean getQualityOfServiceIsNullPublishable();

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
