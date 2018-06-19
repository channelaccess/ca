package org.epics.ca.impl.requests;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.epics.ca.Monitor;
import org.epics.ca.Status;
import org.epics.ca.impl.ChannelImpl;
import org.epics.ca.impl.ContextImpl;
import org.epics.ca.impl.Messages;
import org.epics.ca.impl.NotifyResponseRequest;
import org.epics.ca.impl.Transport;
import org.epics.ca.impl.TypeSupports.TypeSupport;
import org.epics.ca.util.Holder;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;

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
    * Disruptor (event dispatcher).
    */
   protected final Disruptor<Holder<T>> disruptor;

   /**
    * Closed flag.
    */
   protected final AtomicBoolean closed = new AtomicBoolean ();

   protected T overrunValue;
   protected Holder<T> lastValue;

   /**
    */
   public MonitorRequest(
         ChannelImpl<?> channel, Transport transport, TypeSupport<T> typeSupport, int mask,
         Disruptor<Holder<T>> disruptor
   )
   {

      this.channel = channel;
      this.typeSupport = typeSupport;
      this.mask = mask;
      this.disruptor = disruptor;

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
   public void response(
         int status,
         short dataType,
         int dataCount,
         ByteBuffer dataPayloadBuffer
   )
   {

      Status caStatus = Status.forStatusCode (status);
      if ( caStatus == Status.NORMAL )
      {
         RingBuffer<Holder<T>> ringBuffer = disruptor.getRingBuffer ();
         // this is OK only for single producer
         if ( ringBuffer.hasAvailableCapacity (1) )
         {
            long next = ringBuffer.next ();
            try
            {
               lastValue = ringBuffer.get (next);
               lastValue.value = typeSupport.deserialize (dataPayloadBuffer, lastValue.value, dataCount);
            }
            finally
            {
               ringBuffer.publish (next);
            }
         }
         else
         {
            overrunValue = typeSupport.deserialize (dataPayloadBuffer, overrunValue, dataCount);

            // nasty trick, swap the reference of the last value with overrunValue
            T tmp = lastValue.value;
            lastValue.value = overrunValue;
            overrunValue = tmp;
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
      context.unregisterResponseRequest (this);
      channel.unregisterResponseRequest (this);

      // NOTE: this does not wait until all events in the ring buffer are processed
      // but we do not want to block by calling shutdown()
      disruptor.halt ();
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
         RingBuffer<Holder<T>> ringBuffer = disruptor.getRingBuffer ();
         // this is OK only for single producer
         if ( ringBuffer.hasAvailableCapacity (1) )
         {
            long next = ringBuffer.next ();
            try
            {
               Holder<T> holder = ringBuffer.get (next);
               // holder.value will be restored by deserialize method
               holder.value = null;
            }
            finally
            {
               ringBuffer.publish (next);
            }
         }
      }
      else
      {
         logger.warning (() -> "Exception with CA status " + status + " received for monitor, message: " + ((errorMessage != null) ? errorMessage : status.getMessage ()));
      }
   }

   @Override
   public Disruptor<Holder<T>> getDisruptor()
   {
      return disruptor;
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
