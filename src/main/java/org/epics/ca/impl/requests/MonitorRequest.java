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
import org.epics.ca.util.logging.LibraryLogManager;

/**
 * CA monitor for Type T.
 * @param <T> the type of data which the monitor will transport.
 */
public class MonitorRequest<T> implements Monitor<T>, NotifyResponseRequest
{
   private static final Logger logger = LibraryLogManager.getLogger( MonitorRequest.class );

   private int bufferOverrunWarningCount = 0;

   /**
    * Context.
    */
   private final ContextImpl context;

   /**
    * I/O ID given by the context when registered.
    */
   private final int ioid;

   /**
    * Channel.
    */
   protected final ChannelImpl<?> channel;

   /**
    * Type support.
    */
   private final TypeSupport<T> typeSupport;

   /**
    * Monitor mask.
    */
   private final int mask;

   /**
    * Reference to an object which will push out notifications to the Consumer.
    */
   private final MonitorNotificationService<T> monitorNotificationService;

   /**
    * Reference to an object which will consume monitor update events.
    */
   protected final Consumer<? super T> consumer;

   /**
    * Closed flag.
    */
   protected final AtomicBoolean closed = new AtomicBoolean ();

   /**
    * @param channel the channel.
    * @param transport the transport.
    * @param typeSupport the object which will provide type support.
    * @param mask the mask.
    * @param monitorNotificationService the monitor notification service.
    * @param consumer the consumer to be informed of monitor update events.
    */
   public MonitorRequest( ChannelImpl<?> channel, Transport transport, TypeSupport<T> typeSupport, int mask, MonitorNotificationService<T> monitorNotificationService, Consumer<? super T> consumer  )
   {
      this.channel = Validate.notNull( channel );
      this.typeSupport = Validate.notNull( typeSupport );
      this.mask = mask;
      this.monitorNotificationService = Validate.notNull( monitorNotificationService );
      this.consumer = Validate.notNull( consumer );

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
         // Publish the new value to the consumer.
         final boolean overrun = ! monitorNotificationService.publish( dataPayloadBuffer, typeSupport, dataCount );
         if ( overrun )
         {
            bufferOverrunWarningCount++;
            if ( bufferOverrunWarningCount < 3 )
            {
               logger.log(Level.WARNING, "Buffer Overrun: the monitor notification service implementation discarded the oldest data in the notification buffer.");
            }
            else if ( bufferOverrunWarningCount == 3 )
            {
               logger.log(Level.WARNING, "Buffer Overrun: no further warnings will be issued for this monitor.");
            }
         }

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
      context.unregisterResponseRequest( this );
      channel.unregisterResponseRequest( this );

      // THE FOLLOWING IS FROM THE ORIGINAL IMPLEMENTATION:
      // NOTE: this does not wait until all events in the ring buffer are processed
      // but we do not want to block by calling shutdown()
      // disruptor.halt();

      // TODO: decide whether this implementation is appropriate.
      // TODO: Where slow consumers are still processing the call below can block !
      // monitorNotificationService.close();
   }

   public void resubscribe( Transport transport )
   {
      final int dataCount = channel.getElementsToRead(typeSupport);
      Messages.createSubscriptionMessage ( transport, typeSupport.getDataType (), dataCount, channel.getSID (), ioid, mask );
      transport.flush ();
   }

   @Override
   public void exception( int errorCode, String errorMessage )
   {
      Status status = Status.forStatusCode (errorCode);
      if ( status == null )
      {
         logger.log( Level.WARNING ,"Unknown CA status code received for monitor, code: " + errorCode + ", message: " + errorMessage);
         return;
      }

      // shutdown disruptor and remove subscription on channel destroy only
      if ( status == Status.CHANDESTROY )
      {
         cancel ();
      }
      else if ( status == Status.DISCONN )
      {
         logger.finest("Channel disconnected." );
         // The old Disruptor-based implementation pushes out a null here,
         // but only if there was room in the buffer. If there was no room
         // in the buffer the event was quietly dropped. Since this feature
         // was not documented and this behaviour is somewhat unintuitive
         // for the moment the feature has been dropped.
         // monitorNotificationService.publish( null );
      }
      else
      {
         logger.log( Level.WARNING, "Exception with CA status " + status + " received for monitor, message: " + ((errorMessage != null) ? errorMessage : status.getMessage ()));
      }
   }

   @Override
   public void close()
   {
      if ( closed.getAndSet (true) )
         return;

      cancel ();

      Transport transport = channel.getTcpTransport();
      if ( transport == null )
      {
         return;
      }

      final int dataCount = channel.getElementsToRead(typeSupport);

      try
      {
         Messages.cancelSubscriptionMessage ( transport, typeSupport.getDataType (), dataCount, channel.getSID (), ioid );
         transport.flush ();
      }
      catch ( Throwable th )
      {
         logger.log( Level.FINER, "Failed to send 'cancel subscription' message.", th);
      }
   }


}
