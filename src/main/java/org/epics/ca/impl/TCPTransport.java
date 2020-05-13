package org.epics.ca.impl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.epics.ca.Constants;
import org.epics.ca.impl.ResponseHandlers.ResponseHandler;
import org.epics.ca.impl.reactor.ReactorHandler;
import org.epics.ca.util.ResettableLatch;
import org.epics.ca.util.logging.LibraryLogManager;

/**
 * CA transport implementation.
 */
public class TCPTransport implements Transport, ReactorHandler, Runnable
{

   private static final Logger logger = LibraryLogManager.getLogger(TCPTransport.class );

   /**
    * Connection status.
    */
   private final AtomicBoolean closed = new AtomicBoolean ();

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
   private ByteBuffer receiveBuffer;

   /**
    * Flow control "buffer full" count limit.
    */
   private final static int FLOW_CONTROL_BUFFER_FULL_COUNT_LIMIT = 4;

   /**
    * Current flow control state.
    */
   private final AtomicBoolean flowControlState = new AtomicBoolean ();

   /**
    * Flow control change request (null - no change, TRUE - enable, FALSE - disable).
    */
   private final AtomicReference<Boolean> flowControlChangeRequest = new AtomicReference<> ();

   /**
    * Remote side transport revision.
    */
   private final short remoteTransportRevision;

   /**
    * Owners (users) of the transport.
    */
   private final Set<TransportClient> owners = new HashSet<> ();

   /**
    * Initial receive buffer size.
    * It must be 64k to allow efficient operation w/ several event subscriptions.
    */
   private static final int INITIAL_RX_BUFFER_SIZE = 64000;

   /**
    * Initial send buffer size.
    */
   private static final int INITIAL_TX_BUFFER_SIZE = 1024;

   /**
    * CA header structure.
    */
   private final Header header = new Header ();

   private final Lock sendBufferLock = new ReentrantLock ();
   private ByteBuffer sendBuffer;
   private int lastSendBufferPosition = 0;

   private final ScheduledFuture<?> echoTimer;

   /**
    * Constructs a new object for managing the TCP connection with a remote server.
    *
    * @param context the CA context in which the communication takes place.
    * @param responseHandler reference to an object which will manage the handling of the server responses.
    * @param client the client.
    * @param channel socket through which communication will flow.
    * @param remoteTransportRevision the CA transport revision (a constant) to be used in communication.
    * @param priority the CA message priority.
    */
   public TCPTransport( ContextImpl context, TransportClient client, ResponseHandler responseHandler,
                        SocketChannel channel, short remoteTransportRevision, int priority
   )
   {
      this.context = context;
      this.responseHandler = responseHandler;
      this.channel = channel;
      this.remoteTransportRevision = remoteTransportRevision;
      this.priority = priority;

      socketAddress = (InetSocketAddress) channel.socket ().getRemoteSocketAddress ();

      // initialize buffers
      receiveBuffer = ByteBuffer.allocateDirect (INITIAL_RX_BUFFER_SIZE);
      sendBuffer = ByteBuffer.allocateDirect (INITIAL_TX_BUFFER_SIZE);

      // acquire transport
      acquire (client);

      // read echo period and start timer (watchdog)
      long echoPeriod = (long) (context.getConnectionTimeout () * 1000);
      if ( echoPeriod >= 0 )
      {
         echoTimer = context.getScheduledExecutor ().scheduleWithFixedDelay (
               this,
               0,
               echoPeriod,
               TimeUnit.MILLISECONDS);
      }
      else
         echoTimer = null;

      // add to registry
      context.getTransportRegistry ().put (socketAddress, this);
   }

   /**
    * Close connection.
    *
    * @param remotelyClosed flag indicating weather the socket has already been remotely closed
    */
   // NOTE: do not call this methods with lock on transport/channels - high deadlock risk possibility!
   public void close( boolean remotelyClosed )
   {

      if ( closed.getAndSet (true) )
         return;

      // cancel echo timer
      if ( echoTimer != null )
         echoTimer.cancel (false);

      // remove from registry
      context.getTransportRegistry ().remove (socketAddress, priority);

      // flush first
      if ( !remotelyClosed )
         flush ();

      closedNotifyClients ();

      logger.log ( Level.FINER, "Connection to " + socketAddress + " closed.");

      context.getReactor ().unregisterAndClose (channel);
   }

   /**
    * Notifies clients about disconnect.
    */
   private void closedNotifyClients()
   {
      TransportClient[] clients;
      synchronized ( owners )
      {
         // check if still acquired
         int refs = owners.size ();
         if ( refs == 0 )
            return;

         logger.log(Level.FINE,"Transport to " + socketAddress + " still has " + refs + " client(s) active and closing...");
         clients = new TransportClient[ refs ];
         owners.toArray (clients);
         owners.clear ();
      }

      // NOTE: not perfect, but holding a lock on owners
      // and calling external method leads to deadlocks
      for ( TransportClient client : clients )
      {
         try
         {
            client.transportClosed();
         }
         catch ( Throwable th )
         {
            logger.log(Level.SEVERE, "Unexpected exception caught while calling TransportClient.transportClosed().", th);
         }
      }
   }

   /**
    * Acquires transport.
    *
    * @param client client (channel) acquiring the transport
    * @return <code>true</code> if transport was granted, <code>false</code> otherwise.
    */
   public boolean acquire( TransportClient client )
   {

      if ( closed.get () )
         return false;

      logger.log( Level.FINER,"Acquiring transport to " + socketAddress + ".");

      synchronized ( owners )
      {
         if ( closed.get () )
            return false;

         owners.add (client);
      }

      return true;
   }

   /**
    * Releases transport.
    *
    * @param client client (channel) releasing the transport
    */
   public void release( TransportClient client )
   {

      if ( closed.get () )
         return;

      logger.log( Level.FINER, "Releasing transport to " + socketAddress + ".");

      synchronized ( owners )
      {
         owners.remove (client);

         // not used anymore
         if ( owners.size () == 0 )
            close (false);
      }
   }

   @Override
   public short getMinorRevision()
   {
      return remoteTransportRevision;
   }

   /**
    * Handle IO event.
    */
   @Override
   public void handleEvent( SelectionKey key )
   {
      if ( key.isValid () && key.isReadable () )
         processRead ();

      if ( key.isValid () && key.isWritable () )
         processWrite ();
   }

   /**
    * Process input obtained via channel (read) IO event.
    * Also handles subscription flow control.
    */
   protected void processRead()
   {
      try
      {

         // position must be set (what's before position stays intact)
         receiveBuffer.limit (receiveBuffer.capacity ());

         int bufferFullCount = 0;

         while ( !closed.get () )
         {
            // attempt to read from the channel as many bytes as available
            // in the supplied receive buffer. Store the data at successive
            // locations starting from the current position.
            logger.log( Level.FINEST,"About to read into buffer starting at pos: " + receiveBuffer.position());

            int bytesRead = channel.read (receiveBuffer);
            logger.log( Level.FINEST,"Read #bytes from channel: " + bytesRead);

            if ( bytesRead < 0 )
            {
               // error (disconnect, end-of-stream) detected
               logger.log( Level.FINEST, "End of stream ");
               close (true);
               return;
            }
            else if ( bytesRead == 0 )
            {
               // no more data, disable flow control... hopefully this will allow
               // more data to be read pretty soon.
               // Note: flow control only works with monitors !
               logger.log( Level.FINEST, "Disabling flow control...");
               disableFlowControl ();
               break;
            }

            logger.log(Level.FINEST,"Received " + bytesRead + " bytes from " + socketAddress + ".");

            // flow control check
            if ( receiveBuffer.hasRemaining () )
            {
               // buffer not full, disable flow control
               bufferFullCount = 0;
               logger.log (Level.FINEST, "Disabling flow control...");
               disableFlowControl ();
            }
            else
            {
               // buffer full, too many times?
               if ( bufferFullCount >= FLOW_CONTROL_BUFFER_FULL_COUNT_LIMIT )
               {
                  // enable flow control
                  logger.log (Level.FINEST, "Enabling flow control...");
                  enableFlowControl ();
               }
               else
                  bufferFullCount++;
            }

            // Prepare the buffer for reading out. Sets the limit to the current position
            // and sets the position back to zero again.
            logger.log (Level.FINEST, "Flipping buffer.");
            receiveBuffer.flip ();
            logger.log (Level.FINEST, "ReceiveBuffer now has #bytes: " + receiveBuffer.remaining());

            // Now go ahead and try to process whatever data we have obtained
            logger.log (Level.FINEST, "CA: processing new data...");
            processReadBuffer ();
         }

      }
      catch ( IOException ioex )
      {
         logger.log (Level.FINEST, "CA: socket exception. Closing connection.");
         // close connection
         close (true);
      }
   }

   /**
    * Process input from receive buffer
    */
   protected void processReadBuffer()
   {
      int lastMessageStartPosition = 0;
      int lastMessageBytesAvailable;

      logger.log (Level.FINEST, "\n\nProcessing READ buffer from thread: " + Thread.currentThread ());
      // Read and process as many messages as may be available...
      while ( !closed.get () )
      {
         // mark new start
         lastMessageStartPosition = receiveBuffer.position ();
         lastMessageBytesAvailable = receiveBuffer.remaining ();

         logger.log (Level.FINEST, "Processing NEXT loop iteration...");
         logger.log (Level.FINEST, "- lastMessagePosition = " + lastMessageStartPosition);
         logger.log (Level.FINEST, "- lastMessageBytesAvailable = " + lastMessageBytesAvailable);

         // Definitely not full header yet so break (nothing has been read from the byte buffer)
         if ( lastMessageBytesAvailable < Constants.CA_MESSAGE_HEADER_SIZE )
         {
            logger.log (Level.FINEST, "Not enough bytes for normal header - breaking from loop.");
            break;
         }
         // Try to read header - if not enough data to read the extended header break (at this point
         // 16 bytes have been read from the byte buffer)
         if ( !header.read (receiveBuffer) )
         {
            logger.log (Level.FINEST, "Not enough bytes for extended header - breaking from loop.");
            break;
         }

         // If there is not yet enough data in the buffer to read the expected payload...
         if ( receiveBuffer.remaining () < header.payloadSize )
         {
            logger.log (Level.FINEST, "Not enough bytes for payload: " + header.payloadSize);
            // If the buffer itself is not big enough to contain the expected payload
            // then we need to allocate a new buffer, transfer the existing information
            // to it, then bail out of this function to wait for more data.
            if ( header.payloadSize > (receiveBuffer.capacity () - Constants.CA_EXTENDED_MESSAGE_HEADER_SIZE) )
            {
               // we need to dynamically resize the receive buffer
               logger.log (Level.FINEST, "Not enough room to read payload: need to resize buffer!");
               // Comment: Why was 4096 chosen ?  Was this to match the initial size of the
               // receive buffer which was historically chosen to be 4096 ?(Simon Rees, PSI) ?
               final int PAGE_SIZE = 4096;
               int newSize = ((header.payloadSize + Constants.CA_EXTENDED_MESSAGE_HEADER_SIZE) & ~(PAGE_SIZE - 1)) + PAGE_SIZE;

               final int maxBufferSize = context.getMaxArrayBytes ();
               if ( maxBufferSize > 0 && newSize > maxBufferSize )
               {
                  // we drop connection
                  logger.log (Level.SEVERE,
                              "Received payload size (" + header.payloadSize +
                                    ") is larger than configured maximum array size (" +
                                    maxBufferSize + "), disconnecting from " + socketAddress + ".");
                  close (true);
                  return;
               }

               ByteBuffer newBuffer = ByteBuffer.allocateDirect (newSize);

               // copy remaining
               receiveBuffer.position (lastMessageStartPosition);
               newBuffer.put (receiveBuffer);
               receiveBuffer = newBuffer;
               return;
            }
            // If we get here then the buffer is big enough for the expected so we didn't need to allocate a
            // new one. But since there is still not enough information we must bail out and wait some more
            break;
         }

         // If we get here then we have enough room to read the payload and the data is already present :-)
         // in the buffer. We now have all the information needed to process the current message so go and
         // do it.

         // Record the position of the new message so that we are ready to process it
         // when we are done with this one.
         int endOfMessage = receiveBuffer.position () + header.payloadSize;

         try
         {
            logger.log (Level.FINEST, "Processing message starting at position:" + receiveBuffer.position());
            logger.log (Level.FINEST, "Payload size is: " + header.payloadSize);
            // Note: the first character to be read in the receiveBuffer is the first byte of the payload.
            responseHandler.handleResponse (socketAddress, this, header, receiveBuffer);
         }
         catch ( Throwable th )
         {
            logger.log (Level.WARNING, th, () -> "Unexpected exception caught while processing CA message over TCP from " + socketAddress);
         }
         finally
         {
            // Whatever the outcome of the last message handling always adjust the pointers in the receiveBuffer
            // to point to the next message.
            receiveBuffer.position (endOfMessage);
         }

      }

      // Execution will reach here if:
      // - there is not yet enough data to read the header.
      // - there is not yet enough data to read the extended header.
      // - there is not yet enough data to read the payload.
      //
      // At this point the buffer is in some intermediate state where a number of messages may have
      // already been successfully processed.
      //
      // The goal of the code below is to block move any unprocessed data back to the beginning
      // of the receive buffer and to exit the method with the buffer's position pointer ready
      // to receive new data.

      logger.log (Level.FINEST, "Checking for any remaining bytes.");
      int unprocessedBytes = receiveBuffer.limit () - lastMessageStartPosition;
      if ( unprocessedBytes > 0 )
      {
         // copy remaining buffer, lastMessageBytesAvailable bytes from lastMessagePosition,
         // to the start of receiveBuffer
         logger.log (Level.FINEST, "- moving remaining bytes to start of buffer. Unprocessed bytes = " + unprocessedBytes);
         if ( unprocessedBytes < 1024 )
         {
            logger.log (Level.FINEST, "- using copy algorithm 1");
            for ( int i = 0; i < unprocessedBytes; i++ )
               receiveBuffer.put (i, receiveBuffer.get (lastMessageStartPosition++));
            receiveBuffer.position (unprocessedBytes);
         }
         else
         {
            logger.log (Level.FINEST, "- using copy algorithm 2");
            receiveBuffer.position (lastMessageStartPosition);
            ByteBuffer remainingBuffer = receiveBuffer.slice ();
            receiveBuffer.position (0);
            receiveBuffer.put (remainingBuffer);
         }
      }
      // If there were no bytes remaining the block move is unnecessary.
      else
      {
         logger.log (Level.FINEST, "No remaining bytes to copy.");
         receiveBuffer.position (0);
      }
      // Post processing conditions for the receiveBuffer are as follows:
      // - any unprocessed bytes are available starting at the beginning of the buffer.
      // - the position is set to the last unprocessed byte
      // - the limit is set to the buffer's capacity.
      receiveBuffer.limit (receiveBuffer.capacity ());
      logger.log (Level.FINEST, "Done with read processing for now. Buffer Position is: " + receiveBuffer.position());
   }


   /**
    * Process output (write) IO event.
    */
   protected void processWrite()
   {
      // noop since sending is done from the same thread (hmmm?, can block)
   }

   /**
    * Disable flow control (enables events).
    */
   protected void disableFlowControl()
   {
      if ( flowControlState.getAndSet (false) )
      {
         flowControlChangeRequest.set (Boolean.FALSE);
         // send MUST not be done in this (read) thread
         flush ();
      }
   }

   /**
    * Enable flow control (disables events).
    */
   protected void enableFlowControl()
   {
      if ( !flowControlState.getAndSet (true) )
      {
         flowControlChangeRequest.set (Boolean.TRUE);
         // send MUST not be done in this (read) thread
         flush ();
      }
   }

   /**
    * Send a buffer through the transport.
    * NOTE: TCP sent buffer/sending has to be synchronized.
    *
    * @param buffer buffer to be sent
    * @throws IOException the exception
    */
   private void noSyncSend( ByteBuffer buffer ) throws IOException
   {
      try
      {
         final int SEND_BUFFER_LIMIT = 64000;
         int bufferLimit = buffer.limit ();

         logger.log( Level.FINEST,"Sending " + bufferLimit + " bytes to " + socketAddress + ".");

         // limit sending large buffers, split the into parts
         int parts = (buffer.limit () - 1) / SEND_BUFFER_LIMIT + 1;
         for ( int part = 1; part <= parts; part++ )
         {
            if ( parts > 1 )
            {
               buffer.limit (Math.min (part * SEND_BUFFER_LIMIT, bufferLimit));
               logger.log(Level.FINEST,"[Parted] Sending (part " + part + "/" + parts + ") " + (buffer.limit () - buffer.position ()) + " bytes to " + socketAddress + ".");
            }

            for ( int tries = 0; ; tries++ )
            {

               // send
               int bytesSent = channel.write (buffer);
               if ( bytesSent < 0 )
                  throw new IOException ("bytesSent < 0");

               // bytesSend == buffer.position(), so there is no need for flip()
               if ( buffer.position () != buffer.limit () )
               {
                  if ( closed.get () )
                     throw new IOException ("transport closed on the client side");

                  final int WARNING_MESSAGE_TRIES = 10;
                  if ( tries >= WARNING_MESSAGE_TRIES )
                  {
                     logger.log( Level.WARNING,"Failed to send message to " + socketAddress + " - buffer full, will retry." );

                     //if (tries >= 2*TRIES)
                     //	throw new IOException("TCP send buffer persistently full, disconnecting!");

                  }

                  // flush & wait for a while...
                  logger.log( Level.FINEST,"Send buffer full for " + socketAddress + ", waiting...");

                  try
                  {
                     Thread.sleep (Math.min (15000, 10 + tries * 100));
                  }
                  catch ( InterruptedException e )
                  {
                     // noop
                  }
               }
               else
               {
                  break;
               }
            }

         }
      }
      catch ( IOException ioex )
      {
         // close connection
         close (true);
         throw ioex;
      }
   }


   @Override
   public ContextImpl getContext()
   {
      return context;
   }

   @Override
   public ByteBuffer acquireSendBuffer( int requiredSize )
   {

      if ( closed.get () )
         throw new RuntimeException ("transport closed");

      sendBufferLock.lock ();

      lastSendBufferPosition = sendBuffer.position ();

      // enough of space
      if ( sendBuffer.remaining () >= requiredSize )
         return sendBuffer;

      // flush and wait until buffer is actually sent
      try
      {
         flush (true);
      }
      catch ( Throwable th )
      {
         sendBufferLock.unlock ();
         throw th;
      }

      if ( sendBuffer.capacity () < requiredSize )
      {
         // we need to resize
         final int PAGE_SIZE = 4096;
         int newSize = ((requiredSize + Constants.CA_MESSAGE_HEADER_SIZE) & ~(PAGE_SIZE - 1)) + PAGE_SIZE;

         final int maxBufferSize = context.getMaxArrayBytes ();
         if ( maxBufferSize > 0 && newSize > maxBufferSize )
            throw new RuntimeException ("requiredSize > maxArrayBytes");

         try
         {
            sendBuffer = ByteBuffer.allocate (newSize);
            clearSendBuffer ();
         }
         catch ( Throwable th )
         {
            sendBufferLock.unlock ();
            throw th;
         }
      }

      lastSendBufferPosition = sendBuffer.position ();
      return sendBuffer;
   }

   private ByteBuffer acquireSendBufferNoBlocking( int requiredSize, long time, TimeUnit timeUnit )
   {

      if ( closed.get () )
         throw new RuntimeException ("transport closed");

      try
      {
         if ( !sendBufferLock.tryLock (time, timeUnit) )
            return null;
      }
      catch ( InterruptedException e )
      {
         return null;
      }

      lastSendBufferPosition = sendBuffer.position ();

      // enough of space
      if ( sendBuffer.remaining () >= requiredSize )
         return sendBuffer;

      // sanity check
      if ( sendBuffer.capacity () < requiredSize )
         throw new RuntimeException ("sendBuffer.capacity() < requiredSize");

      // we do not wait for free buffer
      return null;
   }

   @Override
   public void releaseSendBuffer( boolean ignore, boolean flush )
   {
      try
      {
         if ( ignore )
         {
            sendBuffer.position (lastSendBufferPosition);
         }
         else if ( flush )
         {
            flush ();
         }
      }
      finally
      {
         sendBufferLock.unlock ();
      }

   }

   @Override
   public void flush()
   {
      flush (false);
   }

   @SuppressWarnings( "unused" )
   private final ResettableLatch sendCompletedLatch = new ResettableLatch (1);


   private int startPosition;

   private void clearSendBuffer()
   {
      sendBuffer.clear ();
      // reserve space for events on/off message
      sendBuffer.position (Constants.CA_MESSAGE_HEADER_SIZE);
      startPosition = sendBuffer.position ();
   }

   protected void flush( boolean wait )
   {
      // do no reset if flush is in progress !!!

//		sendCompletedLatch.reset(1);

      // TODO do not send in this thread (e.g. use LF pool)
      sendBufferLock.lock ();
      try
      {

         Boolean insertFlowControlMessage = flowControlChangeRequest.getAndSet (null);
         if ( insertFlowControlMessage != null )
         {
            long offOn = insertFlowControlMessage ?
                  0x0008000000000000L :         // eventsOff
                  0x0009000000000000L;         // eventsOn
            sendBuffer.putLong (0, offOn);
            sendBuffer.putLong (8, 0);
            startPosition = 0;
         }

         // flip
         sendBuffer.limit (sendBuffer.position ());
         sendBuffer.position (startPosition);

         noSyncSend (sendBuffer);
         clearSendBuffer ();
      }
      catch ( IOException e1 )
      {
         e1.printStackTrace ();
      }
      finally
      {
         sendBufferLock.unlock ();
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
   public InetSocketAddress getRemoteAddress()
   {
      return socketAddress;
   }

   @Override
   public int getPriority()
   {
      return priority;
   }

   /**
    * Echo timer.
    */
   public void run()
   {
      ByteBuffer buffer = acquireSendBufferNoBlocking (
            Constants.CA_MESSAGE_HEADER_SIZE,
            1, TimeUnit.SECONDS);
      if ( buffer != null )
      {
         Messages.generateEchoMessage (this, buffer);
         // should be non-blocking
         releaseSendBuffer (false, true);
      }
   }

}
