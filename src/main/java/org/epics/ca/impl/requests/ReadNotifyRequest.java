package org.epics.ca.impl.requests;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import org.epics.ca.Status;
import org.epics.ca.impl.ChannelImpl;
import org.epics.ca.impl.ContextImpl;
import org.epics.ca.impl.Messages;
import org.epics.ca.impl.NotifyResponseRequest;
import org.epics.ca.impl.Transport;
import org.epics.ca.impl.TypeSupports.TypeSupport;

/**
 * CA read notify.
 */
public class ReadNotifyRequest<T> extends CompletableFuture<T> implements NotifyResponseRequest {

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
	 * Requested data type.
	 */
	protected final int requestedDataType;
	
	/**
	 * Requested data count.
	 */
	protected final int requestedDataCount;

	/**
	 */
	public ReadNotifyRequest(ChannelImpl<?> channel, Transport transport, int sid, int dataType, int dataCount) {

		this.channel = channel;
		this.sid = sid;
		this.requestedDataType = dataType;
		
		// TODO not the nicest way
		if (dataCount == 0 && channel.getTransport().getMinorRevision() < 13)
			dataCount = (Integer)channel.getProperties().get("nativeElementCount");

		this.requestedDataCount = dataCount;
		
		context = transport.getContext();
		ioid = context.registerResponseRequest(this);
		channel.registerResponseRequest(this);

		Messages.readNotifyMessage(transport, dataType, dataCount, sid, ioid);
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

		try
		{			
			
			Status caStatus = Status.forStatusCode(status);
			if (caStatus == Status.NORMAL)
			{
				TypeSupport typeSupport = channel.getTypeSupport();
				
				T value = null;	// TODO reuse option
				value = (T)typeSupport.deserialize(dataPayloadBuffer, value);

				complete(value);
			}
			else
			{
				exception(status, caStatus.getMessage());
			}
			
		}
		finally
		{
			// always cancel request
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

}
