/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/

import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.jcip.annotations.ThreadSafe;
import org.apache.commons.lang3.Validate;
import org.epics.ca.Constants;
import org.epics.ca.util.logging.LibraryLogManager;
import org.epics.ca.util.net.InetAddressUtil;


/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * CA repeater.
 */
@ThreadSafe
class CARepeater
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( CARepeater.class );

   private final byte[] buffer;
   private final ByteBuffer data;

   private final ExecutorService executorService;
   private final AtomicBoolean shutdownRequest;
   private final CARepeaterClientManager clientProxyManager;
   private final DatagramSocket listeningSocket;


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Creates a new instance which will listen on the specified port.
    *
    * @param repeaterPort specifies the port to listen on in the range 1-65535.
    * @throws IllegalArgumentException if the repeaterPort parameter was not within bounds.
    * @throws CaRepeaterStartupException if the CA Repeater could not be started for any reason.

    */
   CARepeater( int repeaterPort ) throws CaRepeaterStartupException
   {
      Validate.inclusiveBetween(1, 65535, repeaterPort, "The port must be in the range 1-65535." );

      logger.info( "Creating CA repeater instance which will bind to the wildcard address on port " + repeaterPort + "." ) ;

      this.buffer = new byte[ Constants.MAX_UDP_RECV ];
      this.data = ByteBuffer.wrap( buffer );
      this.shutdownRequest = new AtomicBoolean( false );
      this.executorService = Executors.newSingleThreadExecutor();

      try
      {
         logger.finest( "Creating broadcast-aware listening socket on port: " + repeaterPort );
         this.listeningSocket = UdpSocketUtilities.createBroadcastAwareListeningSocket( repeaterPort, false );
         logger.finest( "The listening socket was created ok." );
      }
      catch ( SocketException ex )
      {
         final String msg = "An unexpected exception has prevented the CA Repeater from starting.";
         logger.log( Level.WARNING, msg, ex );
         throw new CaRepeaterStartupException( msg, ex );
      }

      final InetSocketAddress repeaterListeningSocketAddress = (InetSocketAddress)  listeningSocket.getLocalSocketAddress();
      logger.finest( "The repeater will advertise its availability on the socket with address : '" + repeaterListeningSocketAddress ) ;
      clientProxyManager = new CARepeaterClientManager( repeaterListeningSocketAddress );
   }

/*- Public methods -----------------------------------------------------------*/

   /**
    * Returns immediately after starting a task which runs this CA Repeater
    * instance in a separate thread.
    *
    * @throws IllegalStateException if a shutdown request has already been
    *    made on this CA Repeater instance.
    */
   void start()
   {
      if ( executorService.isShutdown() )
      {
         throw new IllegalStateException( "This CA Repeater instance has been shutdown and cannot be restarted." );
      }

      // Create and run a task which processes UDP packets  until the
      // processing loop detects a shutdown request.
      final Future<?> task = executorService.submit(() -> {
         try
         {
            processUdpDatagramPackets();
         }
         catch ( Exception ex )
         {
            logger.info( "An unrecoverable exception has occurred which means this CA repeater will terminate.");
         }
         logger.info( "The CA Repeater has terminated.");
      } );
   }

   /**
    * Returns immediately after registering a request to shutdown the CA
    * repeater (which may or may not have been previously started) and
    * freeing up any resources.
    *
    * @throws IllegalStateException if a shutdown request has already been
    *    made on this CA Repeater instance.
    */
   void shutdown()
   {
      if ( executorService.isShutdown() )
      {
         throw new IllegalStateException( "This CA Repeater instance has already been shutdown." );
      }
      shutdownRequest.set( true );
      listeningSocket.close();
   }

/*- Private methods ----------------------------------------------------------*/

    /*
     * Process UDP datagram packets from both CA clients and servers, forever,
     * or until some unexpected fault condition arises.
    */
   private void processUdpDatagramPackets()
   {
      logger.finest( "Processing incoming UDP datagrams..." );
      while ( !shutdownRequest.get() )
      {
         try
         {
            // Wait for a Datagram Packet to arrive.
            logger.finest( "Waiting for next datagram." );
            final DatagramPacket inputPacket = waitForDatagram();
            if ( shutdownRequest.get() )
            {
               logger.finest( "The wait for the next datagram has terminated." );
               logger.finest( "The CA repeater has been shutdown. Will not process any more messages." );
               return;
            }
            logger.finest( "A new UDP datagram packet has been received. " );

            // Process all the data in the datagram packet which may consist of one or several CA messages.
            boolean unprocessedMessages = true;
            DatagramPacket packetToProcess = inputPacket;
            while ( unprocessedMessages )
            {
               logger.finest( "Consuming next message in UDP datagram packet." );
               logger.finest( "The length of the UDP datagram is: " + packetToProcess.getLength() + " bytes."  );
               final DatagramPacket residualMessagePacket = processOneMessage( packetToProcess,
                                                                               // Zero Length message consumer
                                                                               this::handleClientRegistrationRequest,
                                                                               // CA Register message consumer
                                                                               this::handleClientRegistrationRequest,
                                                                               // CA Beacon message consumer
                                                                               this::handleBeaconMessage,
                                                                               // All other message consumer
                                                                               this::handleAllOtherMessages );

               logger.finest("After processing the length of the UDP datagram is: " + residualMessagePacket.getLength() + " bytes."  );
               unprocessedMessages = residualMessagePacket.getLength() > 0;
               packetToProcess = residualMessagePacket;
            }
         }
         catch( Exception ex)
         {
            logger.log( Level.WARNING, "An exception was thrown whilst waiting for a datagram.", ex );
         }
      }
      executorService.shutdown();
   }

   /**
    * Processes a single message from the supplied datagram packet and returns
    * a shortened datagram packet containing any unprocessed messages.
    *
    * Datagrams with zero bytes payloads are processed by invoking the
    * zeroLengthMessageHandler.
    *
    * Datagrams with at least 16 bytes payload are scanned and matched against
    * the expected patterns for CA Client Registration request messages and/or
    * CA Server Beacon messages. If a match is found the message is processed
    * by invoking the relevant consumer.
    *
    * If none of the conditions above match the message is processed by
    * invoking the defaultMessageHandler.
    *
    * @param inputPacket the datagram packet containing the message to consume.
    * @param zeroLengthMessageHandler reference to a handler that will be
    *    invoked for datagrams of zero length.
    * @param clientRegistrationMessageHandler reference to a handler that will
    *    be invoked for datagrams containing CA client registration request
    *    messages.
    * @param beaconMessageHandler reference to a handler that will be invoked
    *   for datgrams containg server beacon messages.
    * @param defaultMessageHandler reference to a handler that will be invoked
    *   for all other messages.
    * @return a datagram that is shortened to include any unprocessed messages
    *    from the original datagram and additionally the socket address details
    *    from the original.
    * @throws NullPointerException if any of the message handlers were set to null.
    */
   private DatagramPacket processOneMessage( DatagramPacket inputPacket,
                                             Consumer<DatagramPacket> zeroLengthMessageHandler,
                                             Consumer<DatagramPacket> clientRegistrationMessageHandler,
                                             Consumer<DatagramPacket> beaconMessageHandler,
                                             Consumer<DatagramPacket> defaultMessageHandler )
   {
      Validate.notNull( zeroLengthMessageHandler );
      Validate.notNull( clientRegistrationMessageHandler );
      Validate.notNull( zeroLengthMessageHandler );
      Validate.notNull( defaultMessageHandler );

      logger.finest( "Consuming one message." );

      final int bytesReceived = inputPacket.getLength();
      logger.finest( "The length of the UDP datagram packet is " + bytesReceived + " bytes." );

      final InetSocketAddress senderSocketAddress = (InetSocketAddress) inputPacket.getSocketAddress();
      logger.finest( "The message was sent from socket '" + senderSocketAddress  + "'" );

      if ( bytesReceived == 0 )
      {
         logger.finest( "Calling ZERO LENGTH MESSAGE consumer." );
         zeroLengthMessageHandler.accept( inputPacket );
         return inputPacket;
      }
      else if ( bytesReceived >= CARepeaterMessage.CA_MESSAGE_HEADER_SIZE )
      {
         final ByteBuffer buffer = ByteBuffer.wrap( inputPacket.getData() );
         final short commandCode = buffer.getShort( CARepeaterMessage.CaHeaderOffsets.CA_HDR_SHORT_COMMAND_OFFSET.value );
         if ( commandCode == CARepeaterMessage.CaCommandCodes.CA_REPEATER_REGISTER.value )
         {
            logger.finest( "Calling CLIENT REGISTRATION MESSAGE consumer." );
            clientRegistrationMessageHandler.accept( inputPacket );

            logger.finest( "Removing processed CLIENT REGISTRATION MESSAGE." );
            return removeProcessedMessage( inputPacket, Constants.CA_MESSAGE_HEADER_SIZE );

         }
         else if( commandCode == CARepeaterMessage.CaCommandCodes.CA_PROTO_RSRV_IS_UP.value  )
         {
            logger.finest( "Calling BEACON MESSAGE consumer." );
            beaconMessageHandler.accept( inputPacket );

            logger.finest("Removing processed BEACON MESSAGE." );
            return removeProcessedMessage( inputPacket, Constants.CA_MESSAGE_HEADER_SIZE );
         }
      }

      logger.finest( "Calling DEFAULT MESSAGE consumer." );
      defaultMessageHandler.accept( inputPacket );

      logger.finest( "Removing processed DEFAULT MESSAGE of length " + inputPacket.getLength() + " bytes." );
      return removeProcessedMessage( inputPacket, inputPacket.getLength() );
   }

   /**
    * Processes an incoming CA Repeater Client Registration Request Message.
    *
    * @param packet the datagram packet containing the messsage.
    */
   private void handleClientRegistrationRequest( DatagramPacket packet )
   {
      Validate.notNull( packet );
      Validate.isTrue( packet.getLength() == 0 || packet.getLength() >= Constants.CA_MESSAGE_HEADER_SIZE );

      logger.finest( "Handling CA Client Registration Message sent from socket '" + packet.getSocketAddress() + "'" );

      final ByteBuffer buffer = ByteBuffer.wrap( packet.getData() );
      final InetAddress serverInetAddress = InetAddressUtil.intToIPv4Address(buffer.getInt( CARepeaterMessage.CaHeaderOffsets.CA_HDR_INT_PARAM2_OFFSET.value ) );
      final InetSocketAddress serverSocketAddress = new InetSocketAddress( serverInetAddress, packet.getPort() );
      logger.finest( "The server address encoded in the datagram was: '" + serverInetAddress + "'" );

      // Reject registration requests that do not come from a local machine address.
      if ( ! NetworkUtilities.isThisMyIpAddress( packet.getAddress() ) )
      {
         logger.warning( "The internet address associated with the request datagram (" + packet.getAddress() + ") was not a local address.'" );
         logger.warning( "The CA repeater can only register clients on one of the local machine interfaces." );
         return;
      }

      // Reject registration requests from clients that do not seem to be listening on the reported socket.
      if ( CARepeaterClientProxy.isClientDead( (InetSocketAddress) packet.getSocketAddress() ) )
      {
         logger.warning( "The CA repeater client (" + packet.getAddress() + ") reports that it is dead." );
         logger.warning( "The CA repeater only register clients that are alive." );
         return;
      }

      // Reject registration requests from clients that are already registered.
     if ( clientProxyManager.isListeningPortAlreadyAssigned( packet.getPort() ) )
      {
         logger.warning( "The internet address associated with the request datagram (" + packet.getSocketAddress() + ") is already a registered client." );
         logger.warning( "Nothing further to do." );
         return;
      }

      // Send the request to the Client Proxy Manager
      final InetSocketAddress clientListeningSocket = new InetSocketAddress( packet.getAddress(), packet.getPort() );
      clientProxyManager.registerNewClient( clientListeningSocket );
   }

   /**
    * Processes an incoming CA Beacon Message from a CA Server.
    *
    * Incoming messages are sent to all registered CA Repeater clients with the
    * possible exception of the client that originated the message.
    *
    * @param packet the datagram packet containing the message sender socket and payload.
    */
   private void handleBeaconMessage( DatagramPacket packet )
   {
      Validate.notNull( packet );
      Validate.isTrue( packet.getLength() >= Constants.CA_MESSAGE_HEADER_SIZE );

      logger.finest( "Handling CA Beacon Message sent from socket '" + packet.getSocketAddress()  + "'." );

      final ByteBuffer buffer = ByteBuffer.wrap( packet.getData() );
      final short caServerVersionNumber = buffer.getShort( CARepeaterMessage.CaBeaconMessageOffsets.CA_HDR_SHORT_BEACON_MSG_SERVER_PROTOCOL_MINOR_VERSION_OFFSET.value );
      logger.finest( "The CA Beacon Message indicates the server's protocol minor version number is: '" + caServerVersionNumber  + "'." );

      final short serverListeningPort = buffer.getShort( CARepeaterMessage.CaBeaconMessageOffsets.CA_HDR_SHORT_BEACON_MSG_SERVER_TCP_LISTENING_PORT_OFFSET.value );
      logger.finest( "The CA Beacon Message indicates the server's TCP listening port is: '" + serverListeningPort  + "'" );

      final int serverBeaconId = buffer.getInt( CARepeaterMessage.CaBeaconMessageOffsets.CA_HDR_INT_BEACON_MSG_SERVER_BEACON_ID_OFFSET.value );
      logger.finest( "The CA Beacon Message has the following Beacon ID '" + serverBeaconId  + "'." );

      // Extract the socket address of the message sender. This will be excluded from the list of
      // CA Repeater clients that the message will be forwarded to.
      final InetSocketAddress excludeForwardingToSelfSocketAddress = (InetSocketAddress) packet.getSocketAddress();

      // According to Channel Access Specification (see the docs section of this project) the server beacon message
      // may or may not provide the address of the server which sent the message. In the case that this is not
      // available then the specification suggests that the repeater substitutes it using the address information
      // provided in the received Datagram Packet.
      final int serverAddressEncodedInMessage = buffer.getInt( CARepeaterMessage.CaBeaconMessageOffsets.CA_HDR_INT_BEACON_MSG_SERVER_ADDR_OFFSET.value );
      final String serverAddressEncodedInMessageAsString = InetAddressUtil.intToIPv4Address( serverAddressEncodedInMessage ).toString();
      logger.finest( "The CA Beacon Message advertised the server's IP address as being: '" + serverAddressEncodedInMessageAsString  + "'." );

      if ( serverAddressEncodedInMessage == 0 )
      {
         logger.finest( "Using IP address from datagram sending socket (" + packet.getAddress() + ")." );
         logger.finest( "Forwarding Beacon Message...");

         final InetAddress serverAddressEncodedInDatagram = packet.getAddress();
         clientProxyManager.forwardBeacon( caServerVersionNumber,
                                           serverListeningPort,
                                           serverBeaconId,
                                           serverAddressEncodedInDatagram,
                                           excludeForwardingToSelfSocketAddress );
      }
      else
      {
         logger.finest( "Using IP address encoded in message (" + serverAddressEncodedInMessageAsString + ")." );
         logger.finest( "Forwarding Beacon Message...");
         clientProxyManager.forwardBeacon( caServerVersionNumber,
                                           serverListeningPort,
                                           serverBeaconId,
                                           InetAddressUtil.intToIPv4Address( serverAddressEncodedInMessage ),
                                           excludeForwardingToSelfSocketAddress );
      }
   }

   /**
    * Processes all other incoming datagrams. Incoming messages are forwarded to all registered
    * CA Repeater clients with the possible exception of the client that originated the
    * message.
    *
    * @param packet the packet to be processed.
    * @throws NullPointerException if the packet argument was null.
    * @throws IllegalArgumentException if the socket address associated with the supplied
    *    packet is not of type InetSocketAddress.
    */
   private void handleAllOtherMessages( DatagramPacket packet )
   {
      Validate.notNull( packet );
      Validate.isTrue( packet.getSocketAddress() instanceof InetSocketAddress );

      // Extract the socket address of the message sender. This will be excluded from the list of
      // CA Repeater clients that the message will be forwarded to.
      final InetSocketAddress excludeForwardingToSelfSocketAddress = (InetSocketAddress) packet.getSocketAddress();
      clientProxyManager.forwardDatagram( packet, excludeForwardingToSelfSocketAddress );
   }

   /**
    * Block indefinitely until a new datagram has arrived or some unrecoverable
    * exception has occurred.
    *
    * @return the datagram packet.
    * @throws Exception the exception for which there is no reasonable recovery.
    */
   private DatagramPacket waitForDatagram() throws Exception
   {
      // Arm the data byte buffer ready to receive a new datagram.
      data.clear();

      // Create a new datagram packet to receive data into the buffer.
      final DatagramPacket packet = new DatagramPacket( buffer, buffer.length );

      // Wait for new data to arrive.
      try
      {
         listeningSocket.receive( packet );
      }
      // The receive operation can potentially fail due to any of the following exceptions.
      // IOException, SocketTimeoutException, PortUnreachableException, IllegalBlockingModeException -->
      catch( Exception ex )
      {
         if ( shutdownRequest.get() )
         {
            logger.finest( "The receive datagram operation terminated because the CA repeater was shutdown." );
            return new DatagramPacket( new byte[] {}, 0  );
         }
         else
         {
            final String msg = "An unexpected exception has made it impossible to obtain a new datagram.";
            logger.finest( msg );
            Thread.currentThread().interrupt();
            throw new Exception(msg, ex);
         }
      }

      logger.finest( "" );
      logger.finest( "CA Repeater listening socket has received new data. Processing..." );
      return packet;
   }

   /**
    * Returns a copy of the input packet but with the data payload shortened to remove the message of
    * specified length from the beginning.
    *
    * @param inputPacket the input datagram packet.
    * @param messageToRemoveLength the number of bytes in the message to be removed.
    * @return the output datagram packet whose data payload have been reduced by the specified length.
    */
   static DatagramPacket removeProcessedMessage( DatagramPacket inputPacket, int messageToRemoveLength )
   {
      Validate.notNull( inputPacket );
      Validate.isTrue( messageToRemoveLength <= inputPacket.getLength() );

      logger.finest( "Removing message of length " + messageToRemoveLength + " bytes." );

      final int newLength = inputPacket.getLength() - messageToRemoveLength;
      final byte[] newPayload = Arrays.copyOfRange( inputPacket.getData(), messageToRemoveLength, inputPacket.getLength() );
      final SocketAddress newSocketAddress = inputPacket.getSocketAddress();

      final DatagramPacket outputPacket = new DatagramPacket( newPayload, newLength,newSocketAddress );
      logger.finest( "The datagram packet is now of length " + newLength + " bytes." );
      return outputPacket;
   }

/*- Nested Classes -----------------------------------------------------------*/

   public static class CaRepeaterStartupException extends Exception
   {
      public CaRepeaterStartupException( String message, Exception ex )
      {
         super( message, ex );
      }
   }

}
