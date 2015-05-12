package org.epics.ca.impl.requests;

import java.nio.ByteBuffer;
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
public class MonitorRequest<T> implements Monitor<T>, NotifyResponseRequest {

	// Get Logger
	private static final Logger logger = Logger.getLogger(MonitorRequest.class.getName());

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
	protected final TypeSupport typeSupport;

	/**
	 * Monitor mask.
	 */
	protected final int mask;

	/**
	 * Disruptor (event dispatcher).
	 */
	protected final Disruptor<Holder<T>> disruptor;

	/**
	 */
	public MonitorRequest(ChannelImpl<?> channel, Transport transport, TypeSupport typeSupport, int mask,
			Disruptor<Holder<T>> disruptor) {

		this.channel = channel;
		this.typeSupport = typeSupport;
		this.mask = mask;
		this.disruptor = disruptor;


		context = transport.getContext();
		ioid = context.registerResponseRequest(this);
		channel.registerResponseRequest(this);

		resubscribe(transport);
	}

	@Override
	public int getIOID() {
		return ioid;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void response(
		int status,
		short dataType,
		int dataCount,
		ByteBuffer dataPayloadBuffer) {

		Status caStatus = Status.forStatusCode(status);
		if (caStatus == Status.NORMAL)
		{
			RingBuffer<Holder<T>> ringBuffer = disruptor.getRingBuffer();
			// this is OK only for single producer
			if (ringBuffer.hasAvailableCapacity(1))
			{
	        	long next = ringBuffer.next();
	        	try {
	            	Holder<T> holder = ringBuffer.get(next);
	    			holder.value = (T)typeSupport.deserialize(dataPayloadBuffer, holder.value, dataCount);
	        	}
	        	finally {
	            	ringBuffer.publish(next);
	        	}
			}
			else
			{
				// TODO
        		System.out.println("monitor queue full, monitor lost");
        	}
		}
		else
		{
			cancel();
		}
	}

	@Override	
	public void cancel() {
		// unregister response request
		context.unregisterResponseRequest(this);
		channel.unregisterResponseRequest(this);
	}
	
	public void resubscribe(Transport transport)
	{
		int dataCount = typeSupport.getForcedElementCount();
		
		if (dataCount == 0 && channel.getTransport().getMinorRevision() < 13)
			dataCount = channel.getNativeElementCount();

		Messages.createSubscriptionMessage(
				transport, typeSupport.getDataType(),
				dataCount, channel.getSID(), ioid, mask);
		transport.flush();		// TODO auto-flush
	}
	
	@Override
	public void exception(int errorCode, String errorMessage)
	{
		Status status = Status.forStatusCode(errorCode);
		if (status == null)
		{
			logger.warning(() -> "Unknown CA status code received for monitor, code: " + errorCode + ", message: " + errorMessage);
			return;
		}
		
		// remove subscription on channel destroy only
		if (status == Status.CHANDESTROY)
			cancel();
		else if (status == Status.DISCONN)
		{
			RingBuffer<Holder<T>> ringBuffer = disruptor.getRingBuffer();
			// this is OK only for single producer
			if (ringBuffer.hasAvailableCapacity(1))
			{
	        	long next = ringBuffer.next();
	        	try {
	            	Holder<T> holder = ringBuffer.get(next);
	            	// holder.value will be restored by deserialize method
	    			holder.value = null;
	        	}
	        	finally {
	            	ringBuffer.publish(next);
	        	}
			}
		}
		else
		{
			logger.warning(() -> "Exception with CA status " + status + " received for monitor, message: " + ((errorMessage != null) ? errorMessage : status.getMessage()));
		}
	}

	@Override
	public Disruptor<Holder<T>> getDisruptor() {
		return disruptor;
	}

	@Override
	public void close() {
		disruptor.shutdown();
		cancel();

		Transport transport = channel.getTransport();
		if (transport == null)
			return;

		int dataCount = typeSupport.getForcedElementCount();
		
		if (dataCount == 0 && channel.getTransport().getMinorRevision() < 13)
			dataCount = channel.getNativeElementCount();

		Messages.cancelSubscriptionMessage(
				transport, typeSupport.getDataType(), dataCount,
				channel.getSID(), ioid);
		transport.flush();	// TODO auto flush
		// TODO should this be exception guarded
	}
	
	

}
