package org.epics.ca.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.epics.ca.Constants;
import org.epics.ca.impl.ResponseHandlers.ResponseHandler;
import org.epics.ca.impl.reactor.ReactorHandler;
import org.epics.ca.util.ResettableLatch;

/**
 * CA transport implementation.
 */
public class TCPTransport implements Transport, ReactorHandler /*, Timer.TimerRunnable */ {

	// Get Logger
	private static final Logger logger = Logger.getLogger(TCPTransport.class.getName());
	
	/**
	 * Connection status.
	 */
	private final AtomicBoolean closed = new AtomicBoolean();

	/**
	 * Context instance.
	 */
	private final ContextImpl context;

	/**
	 * CA reponse handler.
	 */
	private final ResponseHandler responseHandler;

	/**
	 * Corresponding channel.
	 */
	private final SocketChannel channel;

	/**
	 * Cached socket address.
	 */
	private final InetSocketAddress socketAddress;

	/**
	 * Transport priority.
	 */
	private final int priority;
	
	/**
	 * Receive buffer.
	 */
	private final ByteBuffer receiveBuffer;

	/**
	 * Flow control "buffer full" count limit.
	 */
	private final static int FLOW_CONTROL_BUFFER_FULL_COUNT_LIMIT = 4;

	/**
	 * Flow control status.
	 */
	private boolean flowControlActive = false;
	
	/**
	 * Remote side transport revision.
	 */
	private final short remoteTransportRevision;

	/**
	 * Owners (users) of the transport.
	 */
	private final Set<TransportClient> owners = new HashSet<TransportClient>();
	
	/**
	 * Initial receive buffer size.
	 */
	//private static final int INITIAL_RX_BUFFER_SIZE = 1024;

	/**
	 * Initial send buffer size.
	 */
	//private static final int INITIAL_TX_BUFFER_SIZE = 1024;

	/**
	 * CA header structure.
	 */
	private final Header header = new Header();

	private final Lock sendBufferLock = new ReentrantLock();
	private ByteBuffer sendBuffer;
	private int lastSendBufferPosition = 0;
	
	/**
	 * @param context
	 * @param responseHandler
	 * @param client
	 * @param channel
	 * @param remoteTransportRevision
	 * @param priority
	 */
	public TCPTransport(ContextImpl context, TransportClient client, ResponseHandler responseHandler,
					   SocketChannel channel, short remoteTransportRevision, int priority) {
		this.context = context;
		this.responseHandler = responseHandler;
		this.channel = channel;
		this.remoteTransportRevision = remoteTransportRevision;
		this.priority = priority;

		socketAddress = (InetSocketAddress)channel.socket().getRemoteSocketAddress();
		
		// initialize buffers
		// TODO INITIAL_RX_BUFFER_SIZE
		receiveBuffer = ByteBuffer.allocateDirect(context.getMaxArrayBytes());

		// acquire transport
		acquire(client);

		// TODO INITIAL_TX_BUFFER_SIZE
		sendBuffer = ByteBuffer.allocateDirect(context.getMaxArrayBytes());
		
		// TODO beacons
		// read beacon timeout and start timer (watchdog)
		//connectionTimeout = (long)(context.getConnectionTimeout() * 1000);
		//taskID = context.getTimer().executeAfterDelay(connectionTimeout, this);
		
		// add to registry
		context.getTransportRegistry().put(socketAddress, this);
	}
	
	/** 
	 * Close connection.
	 * @param remotelyClosed	flag indicating weather the socket has already been remotely closed
	 */
	// NOTE: do not call this methods with lock on transport/channels - high deadlock risk possibility!
	public void close(boolean remotelyClosed) {

		if (closed.getAndSet(true))
			return;
		
		//Timer.cancel(taskID);
		
		// remove from registry
		context.getTransportRegistry().remove(socketAddress, priority);

		// flush first
		if (!remotelyClosed)
			flush();

		closedNotifyClients();
		
		logger.finer(() -> "Connection to " + socketAddress + " closed.");

		context.getReactor().unregisterAndClose(channel);
	}

	/**
	 * Notifies clients about disconnect.
	 */
	private void closedNotifyClients() {
		TransportClient[] clients;
		synchronized (owners)
		{
			// check if still acquired
			int refs = owners.size();
			if (refs == 0)
				return;
			
			logger.fine(() -> "Transport to " + socketAddress + " still has " + refs + " client(s) active and closing...");
			clients = new TransportClient[refs];
			owners.toArray(clients);
			owners.clear();
		}

		// NOTE: not perfect, but holding a lock on owners
		// and calling external method leads to deadlocks
		for (int i = 0; i < clients.length; i++)
		{
			try
			{
				clients[i].transportClosed();
			}
			catch (Throwable th)
			{
				logger.log(Level.SEVERE, "Unexpected exception caight while calling TransportClient.transportClosed().", th);
			}
		}
	}

	/** 
	 * Acquires transport.
	 * @param client client (channel) acquiring the transport
	 * @return <code>true</code> if transport was granted, <code>false</code> otherwise.
	 */
	public boolean acquire(TransportClient client) {

		if (closed.get())
			return false;
			
		logger.finer(() -> "Acquiring transport to " + socketAddress + ".");

		synchronized (owners)
		{
			if (closed.get())
				return false;
				
			owners.add(client);
		}
		
		return true;
	}

	/** 
	 * Releases transport.
	 * @param client client (channel) releasing the transport
	 */
	public void release(TransportClient client) {

		if (closed.get())
			return;
			
		logger.finer(() -> "Releasing transport to " + socketAddress + ".");

		synchronized (owners)
		{
			owners.remove(client);

			// not used anymore
			if (owners.size() == 0)
				close(false);
		}
	}

	@Override
	public short getMinorRevision() {
		return remoteTransportRevision;
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
	 * Also handles subscription flow control.
	 */
	protected void processRead() {
		try
		{
			
			// position must be set (what's before position stays intact) 
			receiveBuffer.limit(receiveBuffer.capacity());
			
			int bufferFullCount = 0;
			
			while (!closed.get())
			{
				// read
				int bytesRead = channel.read(receiveBuffer);
				if (bytesRead < 0)
				{
					// error (disconnect, end-of-stream) detected
					close (true);
					return; 
				}
				else if (bytesRead == 0)
				{
					// no more data, disable flow control
					bufferFullCount = 0;
					if (flowControlActive)
						disableFlowControl();
					break;
				}
				
				//logger.finest(() -> "Received " + bytesRead + " bytes from " + socketAddress + ".");
				
				// flow control check
				if (receiveBuffer.hasRemaining())
				{
					// buffer not full, disable flow control
					bufferFullCount = 0;
					if (flowControlActive)
						disableFlowControl();
				}
				else
				{
					// buffer full, too many times?
					if (bufferFullCount >= FLOW_CONTROL_BUFFER_FULL_COUNT_LIMIT)
					{
						// enable flow control
						if (!flowControlActive)
							enableFlowControl();
					}
					else
						bufferFullCount++;
				}
				
				// prepare for reading
				receiveBuffer.flip();
				
				// read from buffer
				processRead(receiveBuffer);
			}
			
		} catch (IOException ioex) {
			// close connection
			close(true);
		}
	}
	
	/**
	 * Process input.
	 */
	protected void processRead(ByteBuffer buffer)
	{
	    int lastMessagePosition = 0;
	    int lastMessageBytesAvailable = 0;
		while (!closed.get())
		{
	        // mark new start
			lastMessagePosition = buffer.position();
			lastMessageBytesAvailable = buffer.remaining();
	        
	        // not full header, break
	        if (lastMessageBytesAvailable < Constants.CA_MESSAGE_HEADER_SIZE)
	            break;

	        // read header
	        if (!header.read(buffer))
	            break;
	        
	        // we need whole payload
	        if (buffer.remaining() < header.payloadSize)
	        {
	        	if (header.payloadSize > (buffer.capacity() - Constants.CA_EXTENDED_MESSAGE_HEADER_SIZE))
	        	{
					// for now we drop connection
					// TODO implement skip message logic
					logger.log(Level.SEVERE,
							"Received payload size (" + header.payloadSize + 
							") is larger than configured maximum array size (" +
							context.getMaxArrayBytes() + "), disconnecting from " + socketAddress + ".");
					close(true);
					return;
	        	}
	            break;
	        }
	        
	        // TODO what is message is not valid (command, ...)
	        
			int endOfMessage = receiveBuffer.position() + header.payloadSize;

			try {
				responseHandler.handleResponse(socketAddress, this, header, receiveBuffer);
			} catch (Throwable th) {
				logger.log(Level.WARNING, th, () -> "Unexpected exception caught while processing CA message over TCP from " + socketAddress);
			} finally {
				receiveBuffer.position(endOfMessage);
			}
	        
		}
	    
		int unprocessedBytes = lastMessagePosition + lastMessageBytesAvailable - buffer.position();
		if (unprocessedBytes > 0)
		{
			// copy remaining buffer, lastMessageBytesAvailable bytes from lastMessagePosition,
			// to the start of receiveBuffer
			if (unprocessedBytes < 1024)
			{
				for (int i = 0; i < unprocessedBytes; i++)
					receiveBuffer.put(i, receiveBuffer.get(lastMessagePosition++));
				receiveBuffer.position(unprocessedBytes);
			}
			else
			{
				receiveBuffer.position(lastMessagePosition);
				ByteBuffer remainingBuffer = receiveBuffer.slice();
				receiveBuffer.position(0);
				receiveBuffer.put(remainingBuffer);
			}
		}
		else
			receiveBuffer.position(0);
		
		receiveBuffer.limit(receiveBuffer.capacity());
	}

			
	/**
	 * Process output (write) IO event.
	 */
	protected void processWrite() {
		// TODO            
	}

	/**
	 * Sends client username message to the server.
	 * User name is taken from System property "user.name".
	 */
	public void updateUserName()
	{
		// TODO
	}

	/**
	 * Disable flow control (enables events).
	 */
	protected void disableFlowControl()
	{
		// TODO
		flowControlActive = false;
	}
	
	/**
	 * Enable flow control (disables events).
	 */
	protected void enableFlowControl()
	{
		// TODO
		flowControlActive = true;
	}
	
	/**
	 * Send a buffer through the transport.
	 * NOTE: TCP sent buffer/sending has to be synchronized. 
	 * @param buffer	buffer to be sent
	 * @throws IOException 
	 */
	private void noSyncSend(ByteBuffer buffer) throws IOException
	{
		try
		{
			// prepare buffer
			buffer.flip();

			final int SEND_BUFFER_LIMIT = 64000;
			int bufferLimit = buffer.limit();

			logger.finest(() -> "Sending " + bufferLimit + " bytes to " + socketAddress + ".");

			// limit sending large buffers, split the into parts
			int parts = (buffer.limit()-1) / SEND_BUFFER_LIMIT + 1;
			for (int part = 1; part <= parts; part++)
			{
				if (parts > 1)
				{
					buffer.limit(Math.min(part * SEND_BUFFER_LIMIT, bufferLimit));
					if (logger.isLoggable(Level.FINEST))
						logger.finest("[Parted] Sending (part " + part + "/" + parts + ") " + (buffer.limit()-buffer.position()) + " bytes to " + socketAddress + ".");
				}
				
				for (int tries = 0; ; tries++)
				{
					
					// send
					int bytesSent = channel.write(buffer);
					if (bytesSent < 0)
						throw new IOException("bytesSent < 0");
					
					// bytesSend == buffer.position(), so there is no need for flip()
					if (buffer.position() != buffer.limit())
					{
						if (closed.get())
							throw new IOException("transport closed on the client side");
						
						final int WARNING_MESSAGE_TRIES = 10;
						if (tries >= WARNING_MESSAGE_TRIES)
						{
							logger.warning(() -> "Failed to send message to " + socketAddress + " - buffer full, will retry.");

							//if (tries >= 2*TRIES)
							//	throw new IOException("TCP send buffer persistently full, disconnecting!");
							
						}
						
						// flush & wait for a while...
						logger.finest(() -> "Send buffer full for " + socketAddress + ", waiting...");

						try {
							Thread.sleep(Math.min(15000,10+tries*100));
						} catch (InterruptedException e) {
							// noop
						}
						continue;
					}
					else
						break;
				}
			
			}
		}
		catch (IOException ioex) 
		{
			// close connection
			close(true);
			throw ioex;
		}
	}


	@Override
	public ContextImpl getContext() {
		return context;
	}
	
	@Override
	public ByteBuffer acquireSendBuffer(int requiredSize) {
		
		if (closed.get())
			throw new RuntimeException("transport closed");
		
		sendBufferLock.lock();
		
		lastSendBufferPosition = sendBuffer.position();
		
		// enough of space
		if (sendBuffer.remaining() >= requiredSize)
			return sendBuffer;
		
		// sanity check
		if (sendBuffer.capacity() < requiredSize)
			throw new RuntimeException("sendBuffer.capacity() < requiredSize");
		
		// flush and wait until buffer is actually sent
		// TODO wait w/ timeout?
		flush(true);
		
		// failed to flush check
		if (sendBuffer.remaining() <= requiredSize)
			throw new RuntimeException("Failed to acquire buffer - flush failed.");
		
		return acquireSendBuffer(requiredSize);
	}

	@Override
	public void releaseSendBuffer(boolean ignore, boolean flush) {
        try
        {
            if (ignore)
            {
                sendBuffer.position(lastSendBufferPosition);
            }
            else if (flush)
            {
                flush();
            }
        } 
        finally
        {
        	sendBufferLock.unlock();
        }
		
	}

	@Override
	public void flush() {
		flush(false);
	}

	@SuppressWarnings("unused")
	private final ResettableLatch sendCompletedLatch = new ResettableLatch(1);
	

	protected void flush(boolean wait)
	{
		// do no reset if flush is in progress !!!
		
//		sendCompletedLatch.reset(1);
		
		// TODO do the flush
		
		// TODO do not send in this thread, use LF pool
		sendBufferLock.lock();
		try {
			noSyncSend(sendBuffer);
			sendBuffer.clear();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} finally {
			sendBufferLock.unlock();
		}
/*		
		try {
			sendCompletedLatch.await();
		} catch (InterruptedException e) {
			// noop
		}
		*/
	}
	
	@Override
	public InetSocketAddress getRemoteAddress() {
		return socketAddress;
	}

	@Override
	public int getPriority() {
		return priority;
	}

	/* ********************* [ Beacons ] ************************ */
	
	/*
	 * Probe response (state-of-health message) sent and waiting for response.
	 *
	private boolean probeResponsePending = false;
	
	/**
	 * Probe response (state-of-health message) did not respond - timeout.
	 *
	private boolean probeTimeoutDetected = false;
			
	/**
	 * Probe lock.
	 *
	private Object probeLock = new Object();

	/**
	 * Beacon anomaly flag.
	 *
	private long connectionTimeout;

	/**
	 * Unresponsive transport flag.
	 *
	private boolean unresponsiveTransport = false;

	/**
	 * Timer task node.
	 *
	private Object taskID;

	/**
	 * Beacon arrival.
	 *
	public void beaconArrivalNotify()
	{
		if (!probeResponsePending)
			rescheduleTimer(connectionTimeout);
	}
*/
	/*
	 * Message arrival (every message send to transport). 
	 *//* this is called to oftnen (e.g. first this and then immediately beaconArrivalNotify)
	private void messageArrivalNotify()
	{
		synchronized(probeLock)
		{
			if (!probeResponsePending) 
				rescheduleTimer(connectionTimeout);
		}
	}*/

	/*
	 * Rechedule timer for timeout ms.
	 * @param timeout	timeout in ms.
	 *
	private void rescheduleTimer(long timeout)
	{
		Timer.cancel(taskID);
		if (!closed.get())
			taskID = context.getTimer().executeAfterDelay(timeout, this);
	}
	
	/**
	 * Beacon timer.
	 * @see com.cosylab.epics.caj.util.Timer.TimerRunnable#timeout(long)
	 *
	public void timeout(long timeToRun)
	{
		synchronized(probeLock)
		{
			if (probeResponsePending)
			{
				probeTimeoutDetected = true;
				//unresponsiveTransport();
			}
			else
			{
				sendEcho();
			}
		}
	}

	/**
	 * Called when echo request (state-of-health message) was responed.
	 *
	public void echoNotify()
	{
		synchronized(probeLock)
		{
			if (probeResponsePending)
			{
				if (probeTimeoutDetected)
				{
					// try again
					sendEcho();
				}
				else
				{
					// transport is responsive
					probeTimeoutDetected = false;
					probeResponsePending = false;
					//responsiveTransport();
					rescheduleTimer(connectionTimeout);
				}
			}
		}
	}
	
	/**
	 * Sends echo (state-of-health message)
	 *
	private void sendEcho() {
		synchronized(probeLock)
		{
			probeTimeoutDetected = false;
			probeResponsePending = remoteTransportRevision >= 3;
			try
			{
				new EchoRequest(this).submit();
			}
			catch (IOException ex)
			{
				probeResponsePending = false;
			}
			rescheduleTimer(Constants.CA_ECHO_TIMEOUT);
		}
	}
*/
}
