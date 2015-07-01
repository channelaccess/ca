package org.epics.ca.impl.requests;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import org.epics.ca.CompletionException;
import org.epics.ca.Status;
import org.epics.ca.impl.ChannelImpl;
import org.epics.ca.impl.ContextImpl;
import org.epics.ca.impl.Messages;
import org.epics.ca.impl.NotifyResponseRequest;
import org.epics.ca.impl.Transport;
import org.epics.ca.impl.TypeSupports.TypeSupport;

/**
 * CA write notify.
 */
public class WriteNotifyRequest<T> extends CompletableFuture<Status> implements NotifyResponseRequest {

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
	 */
	public WriteNotifyRequest(ChannelImpl<?> channel, Transport transport, int sid, TypeSupport<T> typeSupport,
			T value, int count) {

		this.channel = channel;
		this.sid = sid;
		
		context = transport.getContext();
		ioid = context.registerResponseRequest(this);
		channel.registerResponseRequest(this);

		Messages.writeNotifyMessage(transport, sid, ioid, typeSupport, value, count);
		transport.flush();
	}

	@Override
	public int getIOID() {
		return ioid;
	}

	@Override
	public void response(
		int status,
		short dataType,
		int dataCount,
		ByteBuffer dataPayloadBuffer) {

		try
		{			
			
			Status caStatus = Status.forStatusCode(status);
			complete(caStatus);
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
		
		Status status = Status.forStatusCode(errorCode);
		if (status == null)
		    status = Status.PUTFAIL;
		
		completeExceptionally(new CompletionException(status, errorMessage));
	}
	
}
