package org.epics.ca.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.epics.ca.Constants;
import org.epics.ca.impl.reactor.ReactorHandler;

/**
 * CA UDP transport implementation.
 * It receives datagrams from <code>BroadcastConnector</code> registered
 * repeater and sends broadcasts datagrams to given addresses.  
 */
// TODO buffer out of memory bug (on massive connect)?! 
public class BroadcastTransport implements ReactorHandler, Transport {

	// Get Logger
	private static final Logger logger = Logger.getLogger(BroadcastTransport.class.getName());
	
	/**
	 * Context instance.
	 */
	private final ContextImpl context;

	/**
	 * Corresponding channel.
	 */
	private final DatagramChannel channel;

	/**
	 * Cached socket address.
	 */
	private final InetSocketAddress socketAddress;

	/**
	 * Connect address.
	 */
	private InetSocketAddress connectAddress;

	/**
	 * Broadcast addresses.
	 */
	private final InetSocketAddress[] broadcastAddresses;

	/**
	 * Receive buffer.
	 */
	private final ByteBuffer receiveBuffer;

	/**
	 * Receive buffer.
	 */
	private final ByteBuffer[] receiveBufferArray;

	/**
	 * Response handler.
	 */
	protected final ResponseHandler responseHandler;

	/**
	 * @param context
	 */
	public BroadcastTransport(ContextImpl context, ResponseHandler responseHandler,
			DatagramChannel channel, InetSocketAddress connectAddress, InetSocketAddress[] broadcastAddresses) {
		this.context = context;
		this.responseHandler = responseHandler;
		this.channel = channel;
		this.connectAddress = connectAddress;
		this.broadcastAddresses = broadcastAddresses;

		socketAddress = (InetSocketAddress)channel.socket().getRemoteSocketAddress();

		// allocate receive buffer
		receiveBuffer = ByteBuffer.allocate(Constants.MAX_UDP_RECV);
		receiveBufferArray = new ByteBuffer[] { receiveBuffer };
	}

	/**
	 * Close transport.
	 */
	public void close()
	{
		if (connectAddress != null)
			logger.finer(() -> "UDP connection to " + connectAddress + " closed.");
		context.getReactor().unregisterAndClose(channel);
	}

	/**
	 * Handle IO event.
	 */
	@Override
	public void handleEvent(SelectionKey key) {
		if (key.isValid() && key.isReadable())
			processRead();
			
		if (key.isValid() && key.isWritable())
			processWrite();
	}

	/**
	 * Process input (read) IO event.
	 */
	protected void processRead() {

		try
		{
			while (true)
			{
				
				// reset header buffer
				receiveBuffer.clear();

				// read to buffer
				// NOTE: If there are fewer bytes remaining in the buffer
				// than are required to hold the datagram then the remainder
				// of the datagram is silently discarded.
				InetSocketAddress fromAddress = (InetSocketAddress)channel.receive(receiveBuffer);
				
				// check if datagram not available
				// NOTE: If this channel is in non-blocking mode and a datagram is not
				// immediately available then this method immediately returns <tt>null</tt>.
				if (fromAddress == null)
				 break;

				//logger.finest(() ->"Received " + receiveBuffer.position() + " bytes from " + fromAddress + ".");

				// prepare buffer for reading
				receiveBuffer.flip();

				// handle response				
				while (receiveBuffer.limit() - receiveBuffer.position() >= Constants.CA_MESSAGE_HEADER_SIZE)
					responseHandler.handleResponse(fromAddress, this, receiveBufferArray);
 
			}
			
		} catch (IOException ioex) {
			logger.log(Level.SEVERE, "Failed to process UDP packet.", ioex);
		}
	}

	/**
	 * Process output (write) IO event.
	 */
	protected void processWrite() {
		// noop (not used for datagrams)
	}

	/**
	 * Send a buffer through the transport.
	 * @param buffer	buffer to send. 
	 */
	public void send(ByteBuffer buffer) 
	{
		if (broadcastAddresses == null)
			return;
		
		for (int i = 0; i < broadcastAddresses.length; i++)
		{
			try
			{
				// prepare buffer
				buffer.flip();

				channel.send(buffer, broadcastAddresses[i]);
			}
			catch (Throwable ioex) 
			{
				logger.log(Level.WARNING, "Failed to sent a datagram to:" + broadcastAddresses[i], ioex);
			}
		}
	}
	
	/**
	 * Send a buffer through the transport immediately. 
	 * @param buffer	buffer to send. 
	 * @param address	send address. 
	 * @throws IOException
	 */
	public void send(ByteBuffer buffer, InetSocketAddress address) throws IOException
	{
		buffer.flip();
		channel.send(buffer, address);
	}

	@Override
	public InetSocketAddress getRemoteAddress() {
		return socketAddress;
	}

	@Override
	public ContextImpl getContext() {
		return context;
	}

	@Override
	public short getMinorRevision() {
		return Constants.CA_MINOR_PROTOCOL_REVISION;
	}
   
}
