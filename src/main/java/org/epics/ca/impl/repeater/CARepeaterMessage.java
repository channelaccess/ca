/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/
/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

import net.jcip.annotations.Immutable;
import org.apache.commons.lang3.Validate;
import org.epics.ca.Constants;
import org.epics.ca.util.net.InetAddressUtil;

import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;

import static org.epics.ca.impl.repeater.CARepeaterMessage.CaCommandCodes.*;

/**
 * Provides support for creating datagrams for each of the CA message types
 * originated by the CA Repeater.
 */
class CARepeaterMessage
{

/*- Public attributes --------------------------------------------------------*/

   // Need to pull across a couple of the more general definitions from
   // the constants defined in the rest of the CA library.
   static final int CA_MESSAGE_HEADER_SIZE = Constants.CA_MESSAGE_HEADER_SIZE;
   static final short CA_MINOR_PROTOCOL_REVISION = Constants.CA_MINOR_PROTOCOL_REVISION;

   /**
    * Defines the command codes for the different messages used in the CA Repeater.
    */
   enum CaCommandCodes
   {
      CA_PROTO_VERSION( 0 ),
      CA_PROTO_SEARCH( 6 ),
      CA_REPEATER_REGISTER( 24 ),
      CA_REPEATER_CONFIRM( 17 ),
      CA_PROTO_RSRV_IS_UP( 13 );

      final short value;
      CaCommandCodes( int value )
      {
         this.value = (short) value;
      }

      static Optional<CaCommandCodes> valueOf( short input )
      {
         return Arrays.stream( CaCommandCodes.values() ).filter ( e -> e.value == input ).findFirst();
      }
   }

   /**
    * Defines the structure of a standard CA Header, that's to say the first 16 bytes of every CA messages.
    */
   enum CaHeaderOffsets
   {
      CA_HDR_SHORT_COMMAND_OFFSET( 0 ),
      CA_HDR_SHORT_PAYLOAD_SIZE_OFFSET( 2 ),
      CA_HDR_SHORT_DATA_TYPE_OFFSET( 4),
      CA_HDR_SHORT_DATA_COUNT_OFFSET( 6 ),
      CA_HDR_INT_PARAM1_OFFSET( 8 ),
      CA_HDR_INT_PARAM2_OFFSET( 12 );

      final int value;
      CaHeaderOffsets( int value )
      {
         this.value = value;
      }
   }

   /**
    * Defines the structure of a CA Search Request Message 'CA_PROTO_SEARCH'.
    */
   enum CaSearchRequestMessageOffsets
   {
      CA_HDR_SHORT_SEARCH_REQUEST_MSG_COMMAND_OFFSET(0 ),
      CA_HDR_SHORT_SEARCH_REQUEST_MSG_PAYLOAD_SIZE_OFFSET(2 ),
      CA_HDR_SHORT_SEARCH_REQUEST_MSG_UNUSED_TCP_REPLY_FLAG_OFFSET(4 ),
      CA_HDR_SHORT_SEARCH_REQUEST_MSG_PROTOCOL_MINOR_VERSION_OFFSET(6 ),
      CA_HDR_INT_SEARCH_REQUEST_MSG_SEARCHID1_OFFSET(8 ),
      CA_HDR_INT_SEARCH_REQUEST_MSG_SEARCHID2_OFFSET(12 );

      final int value;
      CaSearchRequestMessageOffsets( int value )
      {
         this.value = value;
      }
   }

   /**
    * Defines the structure of a CA Search Request Message 'CA_PROTO_SEARCH'.
    */
   enum CaSearchResponseMessageOffsets
   {
      CA_HDR_SHORT_SEARCH_RESPONSE_MSG_COMMAND_OFFSET( 0 ),
      CA_HDR_SHORT_SEARCH_RESPONSE_MSG_PAYLOAD_SIZE_OFFSET( 2 ),
      CA_HDR_SHORT_SEARCH_RESPONSE_MSG_SERVER_TCP_PORT_OFFSET(4 ),
      CA_HDR_SHORT_SEARCH_RESPONSE_MSG_DATA_COUNT_OFFSET(6 ),
      CA_HDR_INT_SEARCH_RESPONSE_MSG_SID_OR_SERVER_ADDR_OFFSET(8 ),
      CA_HDR_INT_SEARCH_RESPONSE_MSG_SEARCHID_OFFSET(12 );

      final int value;
      CaSearchResponseMessageOffsets( int value )
      {
         this.value = value;
      }
   }

   /**
    * Defines the structure of a CA Beacon Message 'CA_PROTO_RSRV_IS_UP'.
    */
   enum CaBeaconMessageOffsets
   {
      CA_HDR_SHORT_BEACON_MSG_COMMAND_OFFSET( 0 ),
      CA_HDR_SHORT_BEACON_MSG_UNUSED_OFFSET( 2 ),
      CA_HDR_SHORT_BEACON_MSG_SERVER_PROTOCOL_MINOR_VERSION_OFFSET( 4 ),
      CA_HDR_SHORT_BEACON_MSG_SERVER_TCP_LISTENING_PORT_OFFSET(6 ),
      CA_HDR_INT_BEACON_MSG_SERVER_BEACON_ID_OFFSET( 8 ),
      CA_HDR_INT_BEACON_MSG_SERVER_ADDR_OFFSET( 12 );

      final int value;
      CaBeaconMessageOffsets( int value )
      {
         this.value = value;
      }
   }

   /**
    * Defines the structure of a 'CA_PROTO_VERSION' message.
    */
   enum CaVersionMessageOffsets
   {
      CA_HDR_SHORT_VERSION_MSG_COMMAND_OFFSET( 0 ),
      CA_HDR_SHORT_VERSION_MSG_UNUSED1_OFFSET( 2 ),
      CA_HDR_SHORT_VERSION_MSG_PRIORITY_OFFSET( 4 ),
      CA_HDR_SHORT_VERSION_MSG_UNUSED_TCP_MINOR_VERSION_OFFSET(6 ),
      CA_HDR_INT_VERSION_MSG_UNUSED2_OFFSET( 8 ),
      CA_HDR_INT_VERSION_MSG_UNUSED3_OFFSET( 12 );

      final int value;
      CaVersionMessageOffsets( int value )
      {
         this.value = value;
      }
   }

   /**
    * Defines the structure of a CA Repeater Registration Message 'CA_REPEATER_REGISTER'.
    */
   enum CaRepeaterRegisterMessageOffsets
   {
      CA_HDR_SHORT_REPEATER_REGISTER_MSG_COMMAND_OFFSET( 0 ),
      CA_HDR_SHORT_REPEATER_REGISTER_MSG_UNUSED1_OFFSET( 2 ),
      CA_HDR_SHORT_REPEATER_REGISTER_MSG_UNUSED2_OFFSET( 4 ),
      CA_HDR_SHORT_REPEATER_REGISTER_MSG_UNUSED3_OFFSET( 6 ),
      CA_HDR_INT_REPEATER_REGISTER_MSG_UNUSED4_OFFSET( 8 ),
      CA_HDR_INT_REPEATER_REGISTER_MSG_REPEATER_ADDR_OFFSET( 12 );

      final int value;
      CaRepeaterRegisterMessageOffsets( int value )
      {
         this.value = value;
      }
   }

   /**
    * Defines the structure of a 'CA_REPEATER_CONFIRM' message.
    */
   enum CaRepeaterConfirmMessageOffsets
   {
      CA_HDR_SHORT_REPEATER_CONFIRM_MSG_COMMAND_OFFSET( 0 ),
      CA_HDR_SHORT_REPEATER_CONFIRM_MSG_UNUSED1_OFFSET( 2 ),
      CA_HDR_SHORT_REPEATER_CONFIRM_MSG_UNUSED2_OFFSET( 4 ),
      CA_HDR_SHORT_REPEATER_CONFIRM_MSG_UNUSED3_OFFSET( 6 ),
      CA_HDR_INT_REPEATER_CONFIRM_MSG_UNUSED4_OFFSET( 8 ),
      CA_HDR_INT_REPEATER_CONFIRM_MSG_REPEATER_ADDR_OFFSET( 12 );

      final int value;
      CaRepeaterConfirmMessageOffsets( int value )
      {
         this.value = value;
      }
   }

/*- Private attributes -------------------------------------------------------*/
/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/

   /**
    * Creates a new CA Version Message 'CA_PROTO_VERSION'.
    *
    * The socket address of the returned datagram packet is not configured.
    *
    * @return the datagram packet containing the message.
    */
   static DatagramPacket createVersionMessage()
   {
      return new CaVersionMessage().getMessageAsDatagram();
   }

   /**
    * Creates a new CA Beacon Message 'CA_PROTO_RSRV_IS_UP'.
    *
    * The socket address of the returned datagram packet is not configured.
    *
    * @param protocolMinorVersion the CA protocol version that the server is supporting.
    * @param serverTcpListeningPort the TCP port on which the server is listening (must be greater than or equal to zero)
    * @param serverBeaconId the server's sequential Beacon ID.
    * @param serverAddress the internet address of the CA server. For messages received by the CA
    *    repeater this may sometimes be zero, but for messages originated by the CA server
    *    the field should always be set.
    * @return the datagram packet containing the message.
    */
   static DatagramPacket createBeaconMessage( short protocolMinorVersion, short serverTcpListeningPort, int serverBeaconId, InetAddress serverAddress )
   {
      return new CaBeaconMessage( protocolMinorVersion, serverTcpListeningPort, serverBeaconId, serverAddress ).getMessageAsDatagram();
   }

   /**
    * Creates a new CA Repeater Registration Message 'CA_REPEATER_REGISTER'.
    *
    * The socket address of the returned datagram packet is not configured.
    *
    * @param clientIpAddress the IP address on which the client advertises
    *   is listening.
    * @return the datagram packet containing the message.
    */
   static DatagramPacket createRepeaterRegisterMessage( InetAddress clientIpAddress )
   {
      return new CaRepeaterRegisterMessage( clientIpAddress ).getMessageAsDatagram();
   }

   /**
    * Creates a new CA Repeater Registration Acknowledgment Message 'CA_REPEATER_CONFIRM'.
    *
    * The socket address of the returned datagram packet is not configured.
    *
    * @param repeaterAddress the address with which the registration succeeded.
    *
    * @return the datagram packet containing the message.
    */
   static DatagramPacket createRepeaterConfirmMessage( InetAddress repeaterAddress )
   {
      return new CaRepeaterConfirmMessage( repeaterAddress ).getMessageAsDatagram();
   }

   /**
    * Extracts (= consumes) the first message in the supplied datagram packet
    * and retuirns its string representation.
    *
    * @param datagramPacket input packet.
    * @return the representation.
    */
   static String extractMessageAsString( DatagramPacket datagramPacket )
   {
      final int packetLength = datagramPacket.getLength();
      final String contents = Arrays.toString( Arrays.copyOfRange( datagramPacket.getData(),  0, packetLength ) );

      if ( packetLength == 0 )
      {
         return "EmptyMessage{}";
      }

      if ( packetLength < CARepeaterMessage.CA_MESSAGE_HEADER_SIZE )
      {
         removeFirstMessageFromDatagram(datagramPacket, datagramPacket.getLength() );
         return "UnknownMessage{ len=" + packetLength + ", data=" + contents + "}";
      }

      final ByteBuffer buffer = ByteBuffer.wrap( datagramPacket.getData() );
      final short command = buffer.getShort( CaHeaderOffsets.CA_HDR_SHORT_COMMAND_OFFSET.value );
      final Optional<CaCommandCodes> code = CaCommandCodes.valueOf( command );

      if ( code.isPresent() )
      {
         switch ( code.get() )
         {
            case CA_PROTO_SEARCH:
               final int searchId1 = buffer.getInt( CaSearchRequestMessageOffsets.CA_HDR_INT_SEARCH_REQUEST_MSG_SEARCHID1_OFFSET.value );
               final int searchId2 = buffer.getInt( CaSearchRequestMessageOffsets.CA_HDR_INT_SEARCH_REQUEST_MSG_SEARCHID2_OFFSET.value );
               if ( buffer.getShort( CaSearchResponseMessageOffsets.CA_HDR_SHORT_SEARCH_RESPONSE_MSG_PAYLOAD_SIZE_OFFSET.value ) == 8 )
               {
                  final short serverTcpPort = buffer.getShort( CaSearchResponseMessageOffsets.CA_HDR_SHORT_SEARCH_RESPONSE_MSG_SERVER_TCP_PORT_OFFSET.value );
                  final int sidOrServerAddress = buffer.getInt( CaSearchResponseMessageOffsets.CA_HDR_INT_SEARCH_RESPONSE_MSG_SID_OR_SERVER_ADDR_OFFSET.value );
                  final int searchId = buffer.getInt( CaSearchResponseMessageOffsets.CA_HDR_INT_SEARCH_RESPONSE_MSG_SEARCHID_OFFSET.value );
                  removeFirstMessageFromDatagram(datagramPacket, CARepeaterMessage.CA_MESSAGE_HEADER_SIZE + 8 );
                  return new CaSearchResponseMessage( serverTcpPort, sidOrServerAddress, searchId ).toString();
               }
               else if ( searchId1 == searchId2 )
               {
                  final short payloadSize = buffer.getShort(CaSearchRequestMessageOffsets.CA_HDR_SHORT_SEARCH_REQUEST_MSG_PAYLOAD_SIZE_OFFSET.value);
                  final byte[] payloadAsByteArray = Arrays.copyOfRange( datagramPacket.getData(), CA_MESSAGE_HEADER_SIZE, CA_MESSAGE_HEADER_SIZE + payloadSize);
                  final String payloadAsString = new String(payloadAsByteArray);
                  removeFirstMessageFromDatagram(datagramPacket, CARepeaterMessage.CA_MESSAGE_HEADER_SIZE + payloadSize );
                  return new CaSearchRequestMessage(payloadAsString, searchId1 ).toString();
               }

            case CA_PROTO_VERSION:
               removeFirstMessageFromDatagram(datagramPacket, CARepeaterMessage.CA_MESSAGE_HEADER_SIZE );
               return new CARepeaterMessage.CaVersionMessage().toString();

            case CA_REPEATER_CONFIRM:
               final int repeaterAddress = buffer.getInt( CARepeaterMessage.CaRepeaterConfirmMessageOffsets.CA_HDR_INT_REPEATER_CONFIRM_MSG_REPEATER_ADDR_OFFSET.value );
               final InetAddress repeaterInetAddress = InetAddressUtil.intToIPv4Address(repeaterAddress);
               removeFirstMessageFromDatagram(datagramPacket, CARepeaterMessage.CA_MESSAGE_HEADER_SIZE );
               return new CARepeaterMessage.CaRepeaterConfirmMessage( repeaterInetAddress).toString();

            case CA_REPEATER_REGISTER:
               final int clientIpAddress = buffer.getInt( CARepeaterMessage.CaRepeaterRegisterMessageOffsets.CA_HDR_INT_REPEATER_REGISTER_MSG_REPEATER_ADDR_OFFSET.value );
               final InetAddress clientIpInetAddress = InetAddressUtil.intToIPv4Address(clientIpAddress);
               removeFirstMessageFromDatagram(datagramPacket, CARepeaterMessage.CA_MESSAGE_HEADER_SIZE );
               return new CARepeaterMessage.CaRepeaterRegisterMessage( clientIpInetAddress ).toString();

            case CA_PROTO_RSRV_IS_UP:
               final short minorProtocolVersion = buffer.getShort( CARepeaterMessage.CaBeaconMessageOffsets.CA_HDR_SHORT_BEACON_MSG_SERVER_PROTOCOL_MINOR_VERSION_OFFSET.value );
               final short serverTcpListeningPort = buffer.getShort( CARepeaterMessage.CaBeaconMessageOffsets.CA_HDR_SHORT_BEACON_MSG_SERVER_TCP_LISTENING_PORT_OFFSET.value );
               final int serverBeaconId = buffer.getInt( CARepeaterMessage.CaBeaconMessageOffsets.CA_HDR_INT_BEACON_MSG_SERVER_BEACON_ID_OFFSET.value );
               final int serverAddress = buffer.getInt( CARepeaterMessage.CaBeaconMessageOffsets.CA_HDR_INT_BEACON_MSG_SERVER_ADDR_OFFSET.value );
               final InetAddress serverInetAddress = InetAddressUtil.intToIPv4Address(serverAddress);
               removeFirstMessageFromDatagram(datagramPacket, CARepeaterMessage.CA_MESSAGE_HEADER_SIZE );
               return new CARepeaterMessage.CaBeaconMessage( minorProtocolVersion, serverTcpListeningPort, serverBeaconId, serverInetAddress).toString();
         }
      }

      removeFirstMessageFromDatagram(datagramPacket, datagramPacket.getLength() );
      return "UnknownMessage{ len=" + packetLength + ", data=" + contents + "}";
   }

   /**
    * Returns a datagram packet shortened from the beginning by the specified number of bytes.
    *
    * @param datagramPacket the datagram packet to process.
    * @param messageToRemoveLength the number of bytes in the message to be removed.
    */
   static void removeFirstMessageFromDatagram( DatagramPacket datagramPacket, int messageToRemoveLength )
   {
      Validate.notNull( datagramPacket );
      Validate.isTrue( messageToRemoveLength <= datagramPacket.getLength() );

      final int newLength = datagramPacket.getLength() - messageToRemoveLength;
      final byte[] newPayload = Arrays.copyOfRange( datagramPacket.getData(), messageToRemoveLength, datagramPacket.getLength() );
      datagramPacket.setData( newPayload, 0, newLength );
   }

/*- Protected methods --------------------------------------------------------*/
/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

   /**
    * Provides CA Repeater support for originating a 'CA_PROTO_SEARCH' request message.
    */
   @Immutable
   private static class CaSearchRequestMessage
   {
      private final String channelName;
      private final int searchId;
      /**
       * Creates a new instance.
       */
      CaSearchRequestMessage( String channelName, int searchId )
      {
         this.channelName = channelName;
         this.searchId = searchId;
      }

      DatagramPacket getMessageAsDatagram( String channelName, int searchId )
      {
         final byte[] channelNameAsByteArray = channelName.getBytes();
         final short payloadSize = (short) channelNameAsByteArray.length;

         final ByteBuffer buffer = ByteBuffer.wrap( new byte[ CARepeaterMessage.CA_MESSAGE_HEADER_SIZE ] );
         buffer.putShort(CaSearchRequestMessageOffsets.CA_HDR_SHORT_SEARCH_REQUEST_MSG_COMMAND_OFFSET.value, CaCommandCodes.CA_PROTO_SEARCH.value );
         buffer.putShort(CaSearchRequestMessageOffsets.CA_HDR_SHORT_SEARCH_REQUEST_MSG_PAYLOAD_SIZE_OFFSET.value, payloadSize );
         buffer.putShort(CaSearchRequestMessageOffsets.CA_HDR_SHORT_SEARCH_REQUEST_MSG_UNUSED_TCP_REPLY_FLAG_OFFSET.value, (short) 0 );
         buffer.putShort(CaSearchRequestMessageOffsets.CA_HDR_SHORT_SEARCH_REQUEST_MSG_PROTOCOL_MINOR_VERSION_OFFSET.value, (short) 0  );
         buffer.putInt(CaSearchRequestMessageOffsets.CA_HDR_INT_SEARCH_REQUEST_MSG_SEARCHID1_OFFSET.value, searchId );
         buffer.putInt(CaSearchRequestMessageOffsets.CA_HDR_INT_SEARCH_REQUEST_MSG_SEARCHID2_OFFSET.value, searchId );
         buffer.put( channelNameAsByteArray );
         return new DatagramPacket( buffer.array(), buffer.array().length );
      }

      @Override
      public String toString()
      {
         return "CaSearchRequestMessage{" +
               "channelName='" + channelName + '\'' +
               ", searchId=" + searchId +
               '}';
      }
   }

   /**
    * Provides CA Repeater support for originating a 'CA_PROTO_SEARCH' request message.
    */
   @Immutable
   private static class CaSearchResponseMessage
   {
      private final short serverTcpPort;
      private final int sidOrServerAddress;
      private final int searchId;

      /**
       * Creates a new instance.
       */
      CaSearchResponseMessage( short serverTcpPort, int sidOrServerAddress, int searchId )
      {
         this.serverTcpPort = serverTcpPort;
         this.sidOrServerAddress = sidOrServerAddress;
         this.searchId = searchId;
      }

      DatagramPacket getMessageAsDatagram( String channelName, int searchId )
      {
         final ByteBuffer buffer = ByteBuffer.wrap( new byte[ CARepeaterMessage.CA_MESSAGE_HEADER_SIZE ] );
         buffer.putShort( CaSearchResponseMessageOffsets.CA_HDR_SHORT_SEARCH_RESPONSE_MSG_COMMAND_OFFSET.value, CaCommandCodes.CA_PROTO_SEARCH.value );
         buffer.putShort( CaSearchResponseMessageOffsets.CA_HDR_SHORT_SEARCH_RESPONSE_MSG_PAYLOAD_SIZE_OFFSET.value, (short) 8 );
         buffer.putShort( CaSearchResponseMessageOffsets.CA_HDR_SHORT_SEARCH_RESPONSE_MSG_SERVER_TCP_PORT_OFFSET.value, serverTcpPort );
         buffer.putShort( CaSearchResponseMessageOffsets.CA_HDR_SHORT_SEARCH_RESPONSE_MSG_DATA_COUNT_OFFSET.value, (short) 0  );
         buffer.putInt( CaSearchResponseMessageOffsets.CA_HDR_INT_SEARCH_RESPONSE_MSG_SID_OR_SERVER_ADDR_OFFSET.value, sidOrServerAddress );
         buffer.putInt( CaSearchResponseMessageOffsets.CA_HDR_INT_SEARCH_RESPONSE_MSG_SEARCHID_OFFSET.value, searchId );
         return new DatagramPacket( buffer.array(), buffer.array().length );
      }

      @Override
      public String toString()
      {
         return "CaSearchResponseMessage{" +
               "serverTcpPort=" + serverTcpPort +
               ", sidOrServerAddress=" + sidOrServerAddress +
               ", searchId=" + searchId +
               '}';
      }
   }
   /**
    * Provides CA Repeater support for originating a 'CA_PROTO_VERSION' message.
    */
   @Immutable
   private static class CaVersionMessage
   {
      /**
       * Creates a new instance.
       */
      CaVersionMessage() {}

      DatagramPacket getMessageAsDatagram()
      {
         final ByteBuffer buffer = ByteBuffer.wrap( new byte[ CARepeaterMessage.CA_MESSAGE_HEADER_SIZE ] );
         buffer.putShort( CaVersionMessageOffsets.CA_HDR_SHORT_VERSION_MSG_COMMAND_OFFSET.value, CaCommandCodes.CA_PROTO_VERSION.value );
         buffer.putShort( CaVersionMessageOffsets.CA_HDR_SHORT_VERSION_MSG_UNUSED1_OFFSET.value, (short) 0 );
         buffer.putShort( CaVersionMessageOffsets.CA_HDR_SHORT_VERSION_MSG_PRIORITY_OFFSET.value, (short) 0 );
         buffer.putShort( CaVersionMessageOffsets.CA_HDR_SHORT_VERSION_MSG_UNUSED_TCP_MINOR_VERSION_OFFSET.value, (short) 0  );
         buffer.putInt( CaVersionMessageOffsets.CA_HDR_INT_VERSION_MSG_UNUSED2_OFFSET.value, 0 );
         buffer.putInt( CaVersionMessageOffsets.CA_HDR_INT_VERSION_MSG_UNUSED3_OFFSET.value, 0 );
         return new DatagramPacket( buffer.array(), buffer.array().length );
      }

      @Override
      public String toString()
      {
         return "CaVersionMessage{}";
      }
   }

   /**
    * Provides CA Repeater support for originating a 'CA_PROTO_RSRV_IS_UP' beacon message.
    */
   @Immutable
   static class CaBeaconMessage
   {
      private final short serverProtocolMinorVersion;
      private final short serverTcpListeningPort;
      private final int serverBeaconId;
      private final InetAddress serverAddress;

      /**
       * Creates a new instance.
       *
       * @param serverProtocolMinorVersion the CA protocol version that the server is supporting.
       * @param serverTcpListeningPort the TCP port on which the server is listening.
       * @param serverBeaconId the server's sequential Beacon ID.
       * @param serverAddress the IP Address of the server. For messages received by the CA repeater
       *    this may sometimes be zero, but for messages originated by the CA server the field should
       *    always be set.
       */
      CaBeaconMessage( short serverProtocolMinorVersion, short serverTcpListeningPort, int serverBeaconId, InetAddress serverAddress )
      {
         Validate.notNull( serverAddress);
         Validate.isTrue( serverAddress instanceof Inet4Address );

         this.serverProtocolMinorVersion = serverProtocolMinorVersion;
         this.serverTcpListeningPort = serverTcpListeningPort;
         this.serverBeaconId = serverBeaconId;
         this.serverAddress = serverAddress;
      }

      DatagramPacket getMessageAsDatagram()
      {
         final ByteBuffer buffer = ByteBuffer.wrap( new byte[ CARepeaterMessage.CA_MESSAGE_HEADER_SIZE ] );
         buffer.putShort( CaBeaconMessageOffsets.CA_HDR_SHORT_BEACON_MSG_COMMAND_OFFSET.value, CA_PROTO_RSRV_IS_UP.value );
         buffer.putShort( CaBeaconMessageOffsets.CA_HDR_SHORT_BEACON_MSG_UNUSED_OFFSET.value, (short) 0 );
         buffer.putShort( CaBeaconMessageOffsets.CA_HDR_SHORT_BEACON_MSG_SERVER_PROTOCOL_MINOR_VERSION_OFFSET.value, serverProtocolMinorVersion );
         buffer.putShort(CaBeaconMessageOffsets.CA_HDR_SHORT_BEACON_MSG_SERVER_TCP_LISTENING_PORT_OFFSET.value, serverTcpListeningPort );
         buffer.putInt( CaBeaconMessageOffsets.CA_HDR_INT_BEACON_MSG_SERVER_BEACON_ID_OFFSET.value, serverBeaconId );
         buffer.putInt( CaBeaconMessageOffsets.CA_HDR_INT_BEACON_MSG_SERVER_ADDR_OFFSET.value, InetAddressUtil.ipv4AddressToInt( serverAddress ) );
         return new DatagramPacket( buffer.array(), buffer.array().length );
      }

      @Override
      public String toString()
      {
         return "CaBeaconMessage{" +
               "serverProtocolMinorVersion=" + serverProtocolMinorVersion +
               ", serverTcpListeningPort=" + serverTcpListeningPort +
               ", serverBeaconId=" + serverBeaconId +
               ", serverHostname=" + serverAddress.getHostName() +
               ", serverAddress=" + serverAddress +
               '}';
      }
   }

   /**
    * Provides CA Repeater support for originating a 'CA_REPEATER_REGISTER' message.
    */
   @Immutable
   private static class CaRepeaterRegisterMessage
   {
      private final InetAddress clientAddress;

      /**
       * Creates a new instance.
       *
       * @param clientAddress the IP address on which the client is listening.
       */
      CaRepeaterRegisterMessage( InetAddress clientAddress )
      {
         Validate.notNull(clientAddress);
         Validate.isTrue(clientAddress instanceof Inet4Address);
         this.clientAddress = clientAddress;
      }

      DatagramPacket getMessageAsDatagram()
      {
         final ByteBuffer buffer = ByteBuffer.wrap( new byte[ CARepeaterMessage.CA_MESSAGE_HEADER_SIZE ] );
         buffer.putShort( CaRepeaterRegisterMessageOffsets.CA_HDR_SHORT_REPEATER_REGISTER_MSG_COMMAND_OFFSET.value, CA_REPEATER_REGISTER.value );
         buffer.putShort( CaRepeaterRegisterMessageOffsets.CA_HDR_SHORT_REPEATER_REGISTER_MSG_UNUSED1_OFFSET.value, (short) 0 );
         buffer.putShort( CaRepeaterRegisterMessageOffsets.CA_HDR_SHORT_REPEATER_REGISTER_MSG_UNUSED2_OFFSET.value, (short) 0 );
         buffer.putShort( CaRepeaterRegisterMessageOffsets.CA_HDR_SHORT_REPEATER_REGISTER_MSG_UNUSED3_OFFSET.value,  (short) 0 );
         buffer.putInt( CaRepeaterRegisterMessageOffsets.CA_HDR_INT_REPEATER_REGISTER_MSG_UNUSED4_OFFSET.value, 0 );
         buffer.putInt(CaRepeaterRegisterMessageOffsets.CA_HDR_INT_REPEATER_REGISTER_MSG_REPEATER_ADDR_OFFSET.value, InetAddressUtil.ipv4AddressToInt( clientAddress ) );
         return new DatagramPacket( buffer.array(), buffer.array().length );
      }

      @Override
      public String toString()
      {
         return "CaRepeaterRegisterMessage{" +
               "clientAddress=" + clientAddress +
               '}';
      }
   }

   /**
    * Provides CA Repeater support for originating a 'CA_REPEATER_CONFIRM' message.
    */
   @Immutable
   private static class CaRepeaterConfirmMessage
   {
      private final InetAddress repeaterAddress;

      /**
       * Creates a new instance.
       *
       * Since the repeater can bind to different local addresses, it's IP is reported by
       * the supplied repeaterAddress. This address will typically be either 0.0.0.0
       * or 127.0.0.1 or the IP assigned to some local network interface.
       *
       * @param repeaterAddress the IP address on which the CA repeater is listening.
       */
      CaRepeaterConfirmMessage( InetAddress repeaterAddress )
      {
         Validate.notNull(repeaterAddress);
         Validate.isTrue(repeaterAddress instanceof Inet4Address);
         this.repeaterAddress = repeaterAddress;
      }

      DatagramPacket getMessageAsDatagram()
      {
         final ByteBuffer buffer = ByteBuffer.wrap( new byte[ CARepeaterMessage.CA_MESSAGE_HEADER_SIZE ] );
         buffer.putShort(CaRepeaterConfirmMessageOffsets.CA_HDR_SHORT_REPEATER_CONFIRM_MSG_COMMAND_OFFSET.value, CA_REPEATER_CONFIRM.value );
         buffer.putShort(CaRepeaterConfirmMessageOffsets.CA_HDR_SHORT_REPEATER_CONFIRM_MSG_UNUSED1_OFFSET.value, (short) 0 );
         buffer.putShort(CaRepeaterConfirmMessageOffsets.CA_HDR_SHORT_REPEATER_CONFIRM_MSG_UNUSED2_OFFSET.value, (short) 0 );
         buffer.putShort(CaRepeaterConfirmMessageOffsets.CA_HDR_SHORT_REPEATER_CONFIRM_MSG_UNUSED3_OFFSET.value, (short) 0 );
         buffer.putInt(CaRepeaterConfirmMessageOffsets.CA_HDR_INT_REPEATER_CONFIRM_MSG_UNUSED4_OFFSET.value, 0 );
         buffer.putInt(CaRepeaterConfirmMessageOffsets.CA_HDR_INT_REPEATER_CONFIRM_MSG_REPEATER_ADDR_OFFSET.value, InetAddressUtil.ipv4AddressToInt(repeaterAddress) );
         return new DatagramPacket( buffer.array(), buffer.array().length );
      }

      @Override
      public String toString()
      {
         return "CaRepeaterConfirmMessage{" +
               "repeaterAddress=" + repeaterAddress +
               '}';
      }
   }

}
