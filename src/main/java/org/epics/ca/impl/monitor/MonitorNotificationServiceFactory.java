/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.monitor;

/*- Imported packages --------------------------------------------------------*/
/*- Interface Declaration ----------------------------------------------------*/

import java.util.function.Consumer;

public interface MonitorNotificationServiceFactory extends AutoCloseable
{

/*- Class Declaration --------------------------------------------------------*/
/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/
/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/

   /**
    * Returns a service instance which will publish events to the specified Consumer.
    *
    * @param consumer the consumer to publish to.
    * @param <T> the type of events that this service instance will publish.
    *
    * @return the service instance.
    */
   <T> MonitorNotificationService<T> getServiceForConsumer( Consumer<? super T> consumer );

   /**
    * Closes down this service factory and releases all underlying resources.
    */
   @Override
   void close();

   /**
    * Returns the count of service instances created by this provider.
    *
    * @return the result.
    */
   int getServiceCount();

   /**
    * Returns an indication of whether this service implementation can be expected
    * to drop notifications or whether it will buffer them in situations where
    * the publication rate exceeds the rate at which the consumers can process the
    * data.
    *
    * @return the result.
    */
   boolean getQosMetricIsBuffered();

   /**
    * Returns an indication of the size of each consumer's notification buffer
    * (for service implementations which provide buffering).
    *
    * Returns 1 in the case that the service implementation is not buffered.
    *
    * @return the result.
    */
   int getQosMetricBufferSizePerConsumer();

   /**
    * Returns an indication of how many threads the consumer may be called back on.
    * Where multiple threads are involved the consumer may wish to synchronize their
    * accept method to force serialisation of the notification sequence so that
    * events get notified in the same sequence as they were published.
    *
    * @return the result.
    */
   int getQosMetricNumberOfNotificationThreadsPerConsumer();

   /**
    * Returns an indication of whether the publish method accepts null
    * as a valid token to be sent to the Coinsumer.
    *
    * @return the result.
    */
   boolean getQosMetricIsNullPublishable();


/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
