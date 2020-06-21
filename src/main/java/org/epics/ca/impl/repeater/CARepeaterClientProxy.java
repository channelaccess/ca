/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.repeater;


/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.epics.ca.util.logging.LibraryLogManager;

import java.net.*;
import java.util.logging.Level;
import java.util.logging.Logger;


/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Acts as a proxy for a CA Repeater client, offering the capability to
 * send it UDP messages and/or to check whether it is currently listening
 * on the socket that was specified during class construction.
 */
class CARepeaterClientProxy implements AutoCloseable
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( CARepeaterClientProxy.class );
   private final InetSocketAddress clientListeningSocketAddress;
   private final DatagramSocket senderSocket;

   // Used internally to determine whether the proxy binds immediately
   // at construction time, or only later on the first attempt to send
   // a message.
   private static final boolean SEND_SOCKET_LAZY_BIND = true;


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Constructs a new instance ready for immediate use. That's to say that is
    * immediately available to communicate with the CA Repeater Client that
    * the proxy instance represents.
    *
    * During construction it is NOT required that the CA client is already
    * listening on the stipulated socket. The current state of liveness can
    * be checked after construction using the <code>isClientDead</code>
    * method.
    *
    * @param clientListeningSocketAddress specifies the socket on which the
    *    CA Repeater client is expected to be listening. Since the CA Repeater
    *    is intended for local clients only this should correspond to a socket
    *    on the local machine.
    *
    * @throws NullPointerException if the 'clientListeningSocketAddress'
    *    argument was null.
    * @throws IllegalArgumentException if the 'clientListeningSocketAddress' is not associated
    *    with an internet address on the local machine
    * @throws SocketException if the proxy was unable to create the local socket which which it
    *     needs to communicate with the client.
    * @throws SecurityException if the proxy could not open the operation was not allowed for any reason.
    */
   CARepeaterClientProxy( InetSocketAddress clientListeningSocketAddress ) throws SocketException
   {
      Validate.notNull( clientListeningSocketAddress, "The 'clientListeningSocketAddress' argument was null." );
      final InetAddress inetAddress = clientListeningSocketAddress.getAddress();
      Validate.isTrue( NetworkUtilities.isThisMyIpAddress( inetAddress ), "The 'clientListeningSocketAddress' specified a socket which was not local ('" + inetAddress + "')."  );

      logger.finest( "Creating new CA Repeater Client Proxy for client socket with listening address: '" + clientListeningSocketAddress + "'..." );
      this.clientListeningSocketAddress = clientListeningSocketAddress;

      // SocketException -->
      // SecurityException -->
      if ( SEND_SOCKET_LAZY_BIND )
      {
         logger.finest( "The sending socket will be bound later." );
         this.senderSocket = UdpSocketUtilities.createUnboundSendSocket();
      }
      else
      {
         logger.finest( "The sending socket will be bound by the OS to an ephemeral port." );
         this.senderSocket = UdpSocketUtilities.createEphemeralSendSocket(false );
      }

      // Note:
      //
      // [1] The 'connect' below does not imply any communication with the client's
      //     listening socket. Only that the default destination of the internal
      //     send socket is configured to send packets to the specified socket
      //     when the send method is eventually invoked.
      //
      // [2] The 'connect' operation below also enables the OS to lookup the specified
      //     IP address and to cache the information, avoiding the potential cost of a
      //     DNS lookup on each send message operation in the future.
      //
      // SocketException -->
      // SecurityException -->
      logger.finest( "The sending socket will be connected to the client listening at address: " + clientListeningSocketAddress );
      senderSocket.connect( this.clientListeningSocketAddress );
   }

/*- Public methods -----------------------------------------------------------*/

   /**
    * Disposes the resources (open sockets) that this client is holding.
    *
    * @implNote
    * This method is forced to be public to satisfy the needs of the interface.
    */
   @Override
   public void close()
   {
      logger.finest( "Closing CA Repeater client sending socket: " + clientListeningSocketAddress);
      senderSocket.close();
   }

   /**
    * Returns the client socket address associated with this proxy.
    *
    * @return client address.
    */
   InetSocketAddress getClientListeningSocketAddress()
   {
      return clientListeningSocketAddress;
   }

   /**
    * Sends a UDP packet containing a CA_REPEATER_CONFIRM message to the CA repeater
    * client associated with this class instance.
    *
    * The result code indicates whether the message was transmitted ok. For UDP this
    * offers no guarantee of whether the packet was actually received at the end
    * destination.
    *
    * In the case of transmission failure the logfile will provide more concrete
    * details of the underlying cause.
    *
    * @return result indicator, set true when the packet was sent ok.
    */
   boolean sendCaRepeaterConfirmMessage( InetAddress repeaterAddress )
   {
      Validate.notNull( repeaterAddress );
      logger.finest( "Sending CA_REPEATER_CONFIRM to client listening at socket address: " + clientListeningSocketAddress );

      // Send message
      final DatagramPacket packet = CARepeaterMessage.createRepeaterConfirmMessage( repeaterAddress );
      return sendDatagram( packet );
   }

   /**
    * Sends a UDP packet containing a CA_PROTO_RSRV_IS_UP (= so-called CA Beacon)
    * message to the CA repeater client associated with this class instance.
    *
    * The result code indicates whether the message was transmitted ok. For UDP this
    * offers no guarantee of whether the packet was actually received at the end
    * destination.
    *
    * In the case of transmission failure the logfile will provide more concrete
    * details of the underlying cause.
    *
    * @param serverProtocolMinorVersion the version number of the CA protocol running on the CA Server.
    * @param serverTcpListeningPort the TCP port on which the CA Server is listening.
    * @param serverBeaconId the CA Server's sequential Beacon ID.
    * @param serverAddress the internet address of the CA server.
    * @return result indicator, set true when the packet was sent ok.
    * @throws NullPointerException if the serverAddresss was null.
    */
   boolean sendCaServerBeaconMessage( short serverProtocolMinorVersion, short serverTcpListeningPort, int serverBeaconId, InetAddress serverAddress )
   {
      Validate.notNull( serverAddress );

      logger.finest( "Sending CA_RSRV_IS_UP (Beacon message) to client listening at socket address: " + clientListeningSocketAddress );

      // Send message
      final DatagramPacket packet = CARepeaterMessage.createBeaconMessage( serverProtocolMinorVersion, serverTcpListeningPort, serverBeaconId, serverAddress );
      return sendDatagram(packet );
   }

   /**
    * Sends a datagram packet containing a CA_PROTO_VERSION (NOP) message
    * message to the CA repeater client associated with this class instance.
    *
    * The result code indicates whether the message was transmitted ok. For UDP this
    * offers no guarantee of whether the packet was actually received at the end
    * destination.
    *
    * In the case of transmission failure the logfile will provide more concrete
    * details of the underlying cause.
    *
    * @return result indicator, set true when the packet was sent ok.
    */
   boolean sendCaVersionMessage()
   {
      logger.finest( "Sending CA_PROTO_VERSION to client listening at socket address: " + clientListeningSocketAddress );

      final DatagramPacket packet = CARepeaterMessage.createVersionMessage();
      return sendDatagram(packet );
   }

   /**
    * Sends a datagram packet to the CA repeater client associated with this class
    * instance.
    *
    * The datagram packet must not explicitly set the destination internet address
    * since this is determined by the CA Repeater client listening address which was
    * set during class construction.
    *
    * The result code indicates whether the message was transmitted ok. For UDP this
    * offers no guarantee of whether the packet was actually received at the end
    * destination.
    *
    * In the case of transmission failure the logfile will provide more concrete
    * details of the underlying cause.
    *
    * @param packet the datagram packet to send.
    * @return result indicator, set true when the packet was sent ok.
    *
    * @throws NullPointerException if the packet argument was null.
    * @throws IllegalArgumentException if the packet's port was specified.
    * @throws IllegalArgumentException if the packet's internet address was specified.
    */
   boolean sendDatagram( DatagramPacket packet )
   {
      Validate.notNull( packet, "The 'packet' argument was null." );
      Validate.isTrue( packet.getPort() == -1, "The datagram port should not be configured." );
      Validate.isTrue( packet.getAddress() == null, "The datagram inet address should not be configured." );

      // Attempt to send the datagram. Catch any errors and translate to return code.
      try
      {
         logger.finest( "Setting datagram destination socket address to " + clientListeningSocketAddress );

         // Make a copy here to avoid side-effects on the datagramPacket argument (as it
         // would otherwise get bound during the send operation).
         final DatagramPacket sendPacket = new DatagramPacket( packet.getData(), packet.getLength(), clientListeningSocketAddress );

         logger.finest( "Sending datagram packet to: " + clientListeningSocketAddress);

         // Checked (= potentially recoverable) Exceptions: IOException, PortUnreachableException -->
         // Unchecked = unrecoverable exceptions) Exceptions: SecurityException, IllegalBlockingModeException, IllegalArgumentException -->
         senderSocket.send( sendPacket );
      }
      catch ( Exception ex )
      {
         // Failed to send. Depending on the platform attempts to send to non-existent
         // addresses may result in an exception (implemented via the ICMP mechanism)
         logger.log( Level.WARNING, "Failed to send datagram packet to: " + clientListeningSocketAddress, ex );
         return false;
      }
      return true;
   }

   /**
    * Checks whether the CA repeater client associated with this class instance is
    * still listening on the configured socket. The method for doing this is
    * described in the <i>Repeater Operation</i> section of the CA Protocol
    * Specification:
    *
    * &quot;The repeater should test to see if its clients exist by periodically
    * attempting to bind to the client's port. If unsuccessful when attempting
    * to bind to the clients port then the repeater concludes that the client
    * no longer exists.&quot;
    *
    * @return true if it is.
    */
   boolean isClientDead()
   {
      logger.finest( "Checking whether client is alive at address: " + clientListeningSocketAddress);
      return isClientDead( clientListeningSocketAddress );
   }

   /**
    * Checks whether the CA repeater client associated with the specified address
    * is listening on the configured socket. The method for doing this is described
    * in the <i>Repeater Operation</i> section of the CA Protocol Specification:
    *
    * &quot;The repeater should test to see if its clients exist by periodically
    * attempting to bind to the client's port. If unsuccessful when attempting
    * to bind to the clients port then the repeater concludes that the client
    * no longer exists.&quot;
    *
    * @return true if it is.
    */
   static boolean isClientDead( InetSocketAddress socketAddress )
   {
      logger.finest( "Checking whether the CA Repeater client at address '" + socketAddress + " is alive." );

      // The current implementation determines liveness by trying to open a
      // listening socket on the same socket as the remote client. If the
      // port is available => repeater client is dead !

      logger.finest( "Checking socket availability for address: " + socketAddress + "."  );

      if ( UdpSocketUtilities.isSocketAvailable(socketAddress ) )
      {
         logger.finest( "The socket is available => the client appears to be DEAD !" );
         return true;
      }
      else
      {
         logger.finest( "The socket is NOT available => the client appears to be ALIVE !" );
         return false;
      }
   }

/*- Package-level methods ----------------------------------------------------*/
/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
