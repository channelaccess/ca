package org.epics.ca.impl.requests;

import java.nio.ByteBuffer;

import org.epics.ca.Monitor;
import org.epics.ca.Status;
import org.epics.ca.impl.ChannelImpl;
import org.epics.ca.impl.ContextImpl;
import org.epics.ca.impl.Messages;
import org.epics.ca.impl.NotifyResponseRequest;
import org.epics.ca.impl.Transport;
import org.epics.ca.impl.TypeSupports.TypeSupport;

import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;

/**
 * CA monitor.
 */
public class MonitorRequest<T> implements Monitor<T>, NotifyResponseRequest {

	/**
	 * Context.
	 */
	protected final ContextImpl context;

	/**
	 * I/O ID given by the context when registered.
	 */
	protected final int ioid;

	/**
	 * Channel server ID.
	 */
	protected final int sid;

	/**
	 * Channel.
	 */
	protected final ChannelImpl<?> channel;

	/**
	 * Type support.
	 */
	protected final TypeSupport typeSupport;

	/**
	 * Disruptor (event dispatcher).
	 */
	protected final Disruptor<T> disruptor;

	/**
	 */
	public MonitorRequest(ChannelImpl<?> channel, Transport transport, int sid, TypeSupport typeSupport, int mask,
			Disruptor<T> disruptor) {

		this.channel = channel;
		this.sid = sid;
		this.typeSupport = typeSupport;
		this.disruptor = disruptor;
		
		int dataCount = typeSupport.getForcedElementCount();
		
		// TODO not the nicest way
		if (dataCount == 0 && channel.getTransport().getMinorRevision() < 13)
			dataCount = (Integer)channel.getProperties().get("nativeElementCount");

		context = transport.getContext();
		ioid = context.registerResponseRequest(this);
		channel.registerResponseRequest(this);

		Messages.createSubscriptionMessage(transport, typeSupport.getDataType(), dataCount, sid, ioid, mask);
		transport.flush();		// TODO auto-flush
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
			RingBuffer<T> ringBuffer = disruptor.getRingBuffer();
        	try
        	{
	        	long next = ringBuffer.tryNext();
	        	try {
	            	T value = ringBuffer.get(next);
	            	// TODO it is required that value instance is changed, so T must be mutable!!!
	    			value = (T)typeSupport.deserialize(dataPayloadBuffer, value, dataCount);
	            	// TODO for test only
	    			System.out.println("received: " + value);
	        	}
	        	finally {
	            	ringBuffer.publish(next);
	        	}
        	} catch (InsufficientCapacityException ice) {
        		// TODO can we avoid exception
        		System.out.println("skipping...");
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
	
	@Override
	public void exception(int errorCode, String errorMessage)
	{
		cancel();
		
		// TODO notify !!!
		//Status status = Status.forStatusCode(errorCode);
		//if (status == null)
		//    status = Status.GETFAIL;
	}

	@Override
	public Disruptor<T> getDisruptor() {
		return disruptor;
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		
	}
	
	

}
