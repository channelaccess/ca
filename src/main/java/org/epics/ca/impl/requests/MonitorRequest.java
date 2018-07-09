package org.epics.ca.impl.requests;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.Validate;
import org.epics.ca.Monitor;
import org.epics.ca.Status;
import org.epics.ca.impl.*;
import org.epics.ca.impl.TypeSupports.TypeSupport;
import org.epics.ca.impl.monitor.MonitorNotificationService;

/**
 * CA monitor.
 */
public class MonitorRequest<T> implements Monitor<T>, NotifyResponseRequest
{

   // Get Logger
   private static final Logger logger = Logger.getLogger (MonitorRequest.class.getName ());

   /**
    * Context.
    */
   protected final ContextImpl context;

   /**
    * I/O ID given by the context when registered.
    */
   protected final int ioid;

   /**
    * Channel.
    */
   protected final ChannelImpl<?> channel;

   /**
    * Type support.
    */
   protected final TypeSupport<T> typeSupport;

   /**
    * Monitor mask.
    */
   protected final int mask;

   /**
    * Reference to an object which will push out notifications to the Consumer.
    */
   protected MonitorNotificationService notifier;

   /**
    * Reference to an object which will consume monitor update events.
    */
   protected Consumer<? super T> consumer;

   /**
    * Closed flag.
    */
   protected final AtomicBoolean closed = new AtomicBoolean ();

   /**
    * @param channel the channel.
    * @param transport the transport.
    * @param typeSupport the object which will provide type support.
    * @param mask the mask.
    * @param notifier the monitor notification service.
    */
   public MonitorRequest( ChannelImpl<?> channel, Transport transport, TypeSupport<T> typeSupport, int mask, MonitorNotificationService notifier, Consumer<? super T> consumer  )
   {
      this.channel = Validate.notNull( channel );
      this.typeSupport = Validate.notNull(typeSupport );
      this.mask = mask;
      this.notifier = Validate.notNull(notifier );
      this.consumer = Validate.notNull(consumer );

      context = transport.getContext ();
      ioid = context.registerResponseRequest (this);
      channel.registerResponseRequest (this);

      resubscribe (transport);
   }

   @Override
   public int getIOID()
   {
      return ioid;
   }

   @Override
   public void response( int status, short dataType, int dataCount, ByteBuffer dataPayloadBuffer )
   {
      Validate.notNull( dataPayloadBuffer );
      Status caStatus = Status.forStatusCode (status);
      if ( caStatus == Status.NORMAL )
      {
         T value = typeSupport.deserialize (dataPayloadBuffer, null, dataCount);

         // Publish the new value to the consumer. Or simply update the value that will
         // be sent if a notification is already scheduled.
         notifier.publish( value );
      }
      else
      {
         cancel ();
      }
   }

   @Override
   public void cancel()
   {
      // unregister response request
      context.unregisterResponseRequest (this);
      channel.unregisterResponseRequest (this);

      // Tell the monitor notifier that we are finished handling events for
      // this consumer.
      notifier.dispose();

   }

   public void resubscribe( Transport transport )
   {
      int dataCount = typeSupport.getForcedElementCount ();

      if ( dataCount == 0 && channel.getTransport ().getMinorRevision () < 13 )
         dataCount = channel.getNativeElementCount ();

      Messages.createSubscriptionMessage (
            transport, typeSupport.getDataType (),
            dataCount, channel.getSID (), ioid, mask);
      transport.flush ();
   }

   @Override
   public void exception( int errorCode, String errorMessage )
   {
      Status status = Status.forStatusCode (errorCode);
      if ( status == null )
      {
         logger.warning (() -> "Unknown CA status code received for monitor, code: " + errorCode + ", message: " + errorMessage);
         return;
      }

      // shutdown disruptor and remove subscription on channel destroy only
      if ( status == Status.CHANDESTROY )
      {
         cancel ();
      }
      else if ( status == Status.DISCONN )
      {
         // The old Disruptor-based implementation pushes out a null here,
         // but only if there is room in the buffer. If there is no room
         // in the buffer the event was quietly dropped.
         // notifier.publish( null );
      }
      else
      {
         logger.warning (() -> "Exception with CA status " + status + " received for monitor, message: " + ((errorMessage != null) ? errorMessage : status.getMessage ()));
      }
   }

   @Override
   public void close()
   {

      if ( closed.getAndSet (true) )
         return;

      cancel ();

      Transport transport = channel.getTransport ();
      if ( transport == null )
         return;

      int dataCount = typeSupport.getForcedElementCount ();

      if ( dataCount == 0 && channel.getTransport ().getMinorRevision () < 13 )
         dataCount = channel.getNativeElementCount ();

      try
      {
         Messages.cancelSubscriptionMessage (
               transport, typeSupport.getDataType (), dataCount,
               channel.getSID (), ioid);
         transport.flush ();
      }
      catch ( Throwable th )
      {
         logger.log (Level.FINER, "Failed to send 'cancel subscription' message.", th);
      }
   }


}
