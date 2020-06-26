/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/

import net.jcip.annotations.ThreadSafe;
import org.epics.ca.util.logging.LibraryLogManager;

import java.io.IOException;
import java.net.*;
import java.util.logging.Logger;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Test helper class which listens on a UDP socket and returns received
 * datagram packets
 */
@ThreadSafe
class UdpSocketReceiver implements AutoCloseable
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( UdpSocketReceiver.class );

   // The size of the receive buffer needs to be larger than the maximum size of
   // any message that this receiver is intended to capture. The CA Repeater is
   // a general-purpose port forwarder that may need to forward packets up
   // to the maximum IPv4 UDP datagram size of 65508 bytes.
   private static final int BUFSIZE = 65_508;

   private final DatagramSocket listeningSocket;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Creates a new instance that will listen on the specified broadcast-aware
    * listening port.
    *
    * @param listeningPort the port to listen on
    * @throws RuntimeException if the receiver could not be created for any reason.
    */
   private UdpSocketReceiver( int listeningPort )
   {
      try
      {
         listeningSocket = UdpSocketUtilities.createBroadcastAwareListeningSocket( listeningPort, true );
      }
      catch ( SocketException ex )
      {
         final String message = "Exception thrown when creating listening socket. Details: " + ex.getMessage();
         logger.warning(message);
         throw new RuntimeException(ex);
      }
   }

/*- Public methods -----------------------------------------------------------*/

   /**
    * Returns a new instance which will listen for broadcasts on the
    * specified port.
    *
    * @param listeningPort the port to listen for broadcasts on.
    * @return the new instance.
    */
   public static UdpSocketReceiver startListening( int listeningPort )
   {
      return new UdpSocketReceiver( listeningPort );
   }

   /**
    * Closes this instance and releases all resources.
    */
   @Override
   public void close()
   {
      listeningSocket.close();
   }


   /**
    * Returns the oldest datagram packet from the datagram buffer associated with the listening
    * socket. If there is no datagram in the buffer, blocks until one becomes available.
    *
    * @return the packet.
    */
   public DatagramPacket getDatagram()
   {
      logger.info("Receiving datagram...");
      final DatagramPacket receivePacket = new DatagramPacket( new byte[ BUFSIZE ], BUFSIZE );

      try
      {
         logger.info( "Listening on socket: " + listeningSocket.getLocalSocketAddress() );
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
      logger.finest( "Receive thread completed.");

      return receivePacket;
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}

