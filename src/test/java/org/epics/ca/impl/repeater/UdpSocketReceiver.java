/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/

import net.jcip.annotations.ThreadSafe;
import org.epics.ca.util.logging.LibraryLogManager;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Test helper class which listens on a UDP socket and returns a future which 
 * completes  when the specified datagram is received.
 */
@ThreadSafe
class UdpSocketReceiver
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( UdpSocketReceiver.class );
   
   // The size of the receive buffer needs to be larger than the maximum size of
   // any message that this receiver is intended to capture. The CA Repeater is
   // a general-purpose port forwarder that may need to forward packets up
   // to the maximum IPv4 UDP datagram size of 65508 bytes.
   private static final int BUFSIZE = 65_508;
   
   // Hold a reference to the last socket instance created by this class.
   private static final AtomicReference<DatagramSocket> socketReference = new AtomicReference<>();

      
/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
   
   private UdpSocketReceiver() {}
      
/*- Public methods -----------------------------------------------------------*/

   /**
    * Returns a new instance which will listen for broadcasts on the 
    * specified port. 
    * 
    * @param listeningPort the port to listen for broadcasts on.
    * @return the new instance.
    */
   static Future<DatagramPacket> create( int listeningPort )
   {
      UdpSocketReceiver udpSocketReceiver = new UdpSocketReceiver();
      return udpSocketReceiver.getFuturePacket( listeningPort );
   }

   /**
    * Cancels the receive operation associated with the last created
    * instance and closes any associated resources.
    * 
    * If no instance has been created this method silently returns.
    */
   static void cancel()
   {
      final DatagramSocket socket;
      if ( ( socket = socketReference.getAndSet( null ) ) != null )
      {
         socket.close();
      }   
   }   
   
/*- Private methods ----------------------------------------------------------*/
   
   /**
    * Configures this UDP Socket Receiver instance to receive broadcasts on the
    * specified port, returning a future which will complete only when the
    * port has received a single datagram.
    *
    * @param port the port to listen for broadcasts on.
    * @return the future.
    * @throws RuntimeException if an error occurs when setting up the listening socket.
    */
   private Future<DatagramPacket> getFuturePacket( int port )
   {
      logger.info( "Arming UDP Receiver..." );

      // The countdown latch below is used to ensure that this
      // method remains blocked until a Datagram Socket has been
      // created => the underlying OS is actively buffering
      // incoming data.
      final CountDownLatch countDownLatch = new CountDownLatch(1 );

      // Create an executor whose reference we will hold on to so that
      // it can be cleanly shutdown at the end.
      final ExecutorService executor = Executors.newSingleThreadExecutor();

      // Submit a task to the executor that will wait for an incoming datagram
      // and terminate when it has arrived.
      final Future<DatagramPacket> future = executor.submit( () -> {
         final DatagramPacket receivePacket = new DatagramPacket( new byte[ BUFSIZE], BUFSIZE );
         logger.finest( "Receive thread started." );
         
         try ( final DatagramSocket listeningSocket = UdpSocketUtilities.createBroadcastAwareListeningSocket( port, true ) )
         {
            // Record a reference to the socket that has been created. This will support the
            // possibility of closing the socket via another thread and terminating any blocked
            // receive operations.
            socketReference.set( listeningSocket );
            try
            {
               logger.info( "Listening on socket: " + listeningSocket.getLocalSocketAddress() );

               // Note: there is a potential race condition here: the countdown latch should only be
               // set AFTER the socket receive operation has begun.
               countDownLatch.countDown();
               listeningSocket.receive( receivePacket );
               logger.info( "Received new packet from: " + receivePacket.getSocketAddress() );
            }
            catch ( IOException ex )
            {
               logger.warning( "IOException thrown when attempting to receive datagram ! Perhaps the operation was cancelled ?" );
            }
            catch ( Exception ex )
            {
               logger.warning( "Exception thrown when attempting to receive datagram. Details: '" + ex.getMessage() + "'." );
            }
            logger.finest( "Receive thread completed." );
         }
         catch ( Exception ex )
         {
            // Even if the socket could not be created then it is ok to allow the calling thread to proceed.
            // In this case the method will immediately return an empty datagram packet.
            countDownLatch.countDown();
            logger.log(Level.WARNING, "Exception thrown when creating listening socket. Details: ", ex.getMessage() );
         }

         // Always shutdown the executor.
         logger.finest( "Shutting down executor..." );
         executor.shutdown();
         logger.finest( "Executor shutdown requested." );

         // And always return the receive packet (whether or not it has any data in it).
         return receivePacket;
      });

      // Only allow the calling thread to proceed once the listening socket is created
      // (= armed and buffering incoming data).
      try
      {
         countDownLatch.await(1, TimeUnit.MILLISECONDS );
      }
      catch ( InterruptedException e )
      {
         final String message = "Unexpected delay when creating listening socket";
         throw new RuntimeException( message );
      }
      logger.info( "Receiver has been armed." );

      // Return the future which can be polled, waited on, or used to extract the
      // received datagram when it eventually arrives and the future completes.
      return future;
   }
}

/*- Nested Classes -----------------------------------------------------------*/
   
   

