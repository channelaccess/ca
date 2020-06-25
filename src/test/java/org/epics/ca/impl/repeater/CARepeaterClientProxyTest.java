/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/

import org.epics.ca.ThreadWatcher;
import org.epics.ca.util.logging.LibraryLogManager;
import org.epics.ca.util.net.InetAddressUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.*;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.*;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

class CARepeaterClientProxyTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( CARepeaterClientProxyTest.class );
   private ThreadWatcher threadWatcher;
   
/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/

   // -------------------------------------------------------------------------
   // 1.0 Unit Tests
   // -------------------------------------------------------------------------

   @BeforeAll
   static void beforeAll()
   {
      // This is a guard condition. There is no point in checking the behaviour
      // of the CARepeaterClientProxyTest class if the network stack is not appropriately
      // configured for channel access.
      assertThat( NetworkUtilities.verifyTargetPlatformNetworkStackIsChannelAccessCompatible(), is( true ) );

      // Currently (2020-05-22) this test is not supported when the VPN connection is active on the local machine.
      if ( NetworkUtilities.isVpnActive() )
      {
         fail( "This test is not supported when a VPN connection is active on the local network interface." );
      }
   }

   @BeforeEach
   void beforeEach()
   {
      threadWatcher = ThreadWatcher.start();
   }
   
   @AfterEach
   void afterEach()
   {
      threadWatcher.verify();
   }  
   
   @Test
   void testConstructor_nullArgument_throwsNullPointerException()
   {
      final Throwable throwable = assertThrows( NullPointerException.class, () -> new CARepeaterClientProxy( null) );
      assertThat( throwable.getMessage(), containsString( "The 'clientListeningSocketAddress' argument was null." ) );
   }

   @Test
   void testConstructor_localAddressDoesNotThrowException()
   {
      final InetSocketAddress nonLocalAddress = new InetSocketAddress( "localhost", 1234 );
      assertDoesNotThrow( () -> new CARepeaterClientProxy( nonLocalAddress) );
   }

   @Test
   void testConstructor_loopbackIpDoesNotThrowException()
   {
      final InetSocketAddress nonLocalAddress = new InetSocketAddress( "127.0.0.1", 1234 );
      assertDoesNotThrow( () -> new CARepeaterClientProxy( nonLocalAddress ).close() );
   }

   @Test
   void testConstructor_wildcardAddressDoesNotThrowException()
   {
      final InetSocketAddress wildcardAddress = new InetSocketAddress(1234 );
      assertDoesNotThrow( () -> new CARepeaterClientProxy( wildcardAddress ).close() );
   }

   @Test
   void testConstructor_ephemeralPortDoesNotThrowException()
   {
      final InetSocketAddress nonLocalAddress = new InetSocketAddress( 0 );
      assertDoesNotThrow( () -> new CARepeaterClientProxy( nonLocalAddress ).close() );
   }

   @Test
   void testSendUdpPacket_throwsIllegalArgumentException_whenDatagramPortIsConfigured() throws SocketException
   {
      try ( CARepeaterClientProxy proxy = new CARepeaterClientProxy( new InetSocketAddress(0 ) ) )
      {
         final DatagramPacket datagramPacketWithShouldNotBeConfiguredPort = new DatagramPacket( new byte[] {}, 0 );
         datagramPacketWithShouldNotBeConfiguredPort.setPort( 1234 );
         final Throwable throwable = assertThrows( IllegalArgumentException.class, () -> proxy.sendDatagram( datagramPacketWithShouldNotBeConfiguredPort ) );
         assertThat( throwable.getMessage(), is( "The datagram port should not be configured." ) );
      }
   }

   @Test
   void testSendUdpPacket_throwsIllegalArgumentException_whenDatagramInetAddressIsConfigured() throws SocketException
   {
      try ( CARepeaterClientProxy proxy = new CARepeaterClientProxy( new InetSocketAddress( 0 ) ) )
      {
         final DatagramPacket datagramPacketWithShouldNotBeConfiguredAddress = new DatagramPacket( new byte[] {}, 0 );
         datagramPacketWithShouldNotBeConfiguredAddress.setAddress( InetAddress.getLoopbackAddress() );
         final Throwable throwable = assertThrows( IllegalArgumentException.class, () -> proxy.sendDatagram( datagramPacketWithShouldNotBeConfiguredAddress ) );
         assertThat( throwable.getMessage(), is( "The datagram inet address should not be configured." ) );
      }
   }

   @Test
   void testSendCaBeaconMessage_throwsNullPointerException_whenServerAddressNull() throws SocketException,  UnknownHostException
   {
      try ( CARepeaterClientProxy proxy = new CARepeaterClientProxy( new InetSocketAddress( InetAddress.getLocalHost(), 3333 ) ) )
      {
         final Throwable throwable = assertThrows( NullPointerException.class, () -> proxy.sendCaServerBeaconMessage( (short) 99, (short) 4444,0x12345678, null ) );
         assertThat( throwable.getMessage(), is( "The validated object is null" ) );
      }
   }

   // -------------------------------------------------------------------------
   // 2.0 Integration Tests
   // -------------------------------------------------------------------------

   @Test
   void integrationTestSendCaVersionMessage() throws SocketException
   {
      // Use try with resources to ensure UDP Receiver listening socket gets closed
      // regardless of test outcome.
      try ( final UdpSocketReceiver receiver = UdpSocketReceiver.startListening( 31245 ) )
      {
         try ( CARepeaterClientProxy proxy = new CARepeaterClientProxy(new InetSocketAddress(InetAddress.getLoopbackAddress(), 31245)) )
         {
            logger.info( "About to send..." );
            proxy.sendCaVersionMessage();
            logger.info( "Sent." );
         }

         final DatagramPacket receivePacket = receiver.getDatagram();

         // Check the received length is exactly the size of the header.
         assertThat(receivePacket.getLength(), is(CARepeaterMessage.CA_MESSAGE_HEADER_SIZE));

         // Now verify that the contents of the message is as expected for a CA_REPEATER_CONFIRM
         final ByteBuffer byteBuffer = ByteBuffer.wrap(receivePacket.getData());
         byteBuffer.limit(receivePacket.getLength());
         assertThat(byteBuffer.getShort(CARepeaterMessage.CaHeaderOffsets.CA_HDR_SHORT_COMMAND_OFFSET.value), is(CARepeaterMessage.CaCommandCodes.CA_PROTO_VERSION.value));
         assertThat(byteBuffer.getShort(CARepeaterMessage.CaVersionMessageOffsets.CA_HDR_SHORT_VERSION_MSG_UNUSED1_OFFSET.value), is((short) 0));
         assertThat(byteBuffer.getShort(CARepeaterMessage.CaVersionMessageOffsets.CA_HDR_SHORT_VERSION_MSG_PRIORITY_OFFSET.value), is((short) 0));
         assertThat(byteBuffer.getShort(CARepeaterMessage.CaVersionMessageOffsets.CA_HDR_SHORT_VERSION_MSG_UNUSED_TCP_MINOR_VERSION_OFFSET.value), is((short) 0));
         assertThat(byteBuffer.getInt(CARepeaterMessage.CaVersionMessageOffsets.CA_HDR_INT_VERSION_MSG_UNUSED2_OFFSET.value), is(0));
         assertThat(byteBuffer.getInt(CARepeaterMessage.CaVersionMessageOffsets.CA_HDR_INT_VERSION_MSG_UNUSED3_OFFSET.value), is(0));
      }
   }

   @Test
   void integrationTestSendCaBeaconMessage() throws SocketException, UnknownHostException
   {
      // Use try with resources to ensure UDP Receiver listening socket gets closed
      // regardless of test outcome.
      try ( final UdpSocketReceiver receiver = UdpSocketReceiver.startListening(31246) )
      {
         try ( CARepeaterClientProxy proxy = new CARepeaterClientProxy(new InetSocketAddress(InetAddress.getLoopbackAddress(), 31246)) )
         {
            logger.info( "About to send...") ;
            proxy.sendCaServerBeaconMessage((short) 99, (short) 4444, 0x12345678, InetAddress.getByAddress(new byte[] { (byte) 0Xaa, (byte) 0Xbb, (byte) 0Xcc, (byte) 0Xdd }));
            logger.info( "Sent !" );
         }

         final DatagramPacket receivePacket = receiver.getDatagram();

         // Check the received length is exactly the size of the header.
         assertThat(receivePacket.getLength(), is(CARepeaterMessage.CA_MESSAGE_HEADER_SIZE));

         // Now verify that the contents of the message is as expected for a CA_REPEATER_CONFIRM
         final ByteBuffer byteBuffer = ByteBuffer.wrap(receivePacket.getData());
         byteBuffer.limit(receivePacket.getLength());
         assertThat(byteBuffer.getShort(CARepeaterMessage.CaHeaderOffsets.CA_HDR_SHORT_COMMAND_OFFSET.value), is(CARepeaterMessage.CaCommandCodes.CA_PROTO_RSRV_IS_UP.value));
         assertThat(byteBuffer.getShort(CARepeaterMessage.CaBeaconMessageOffsets.CA_HDR_SHORT_BEACON_MSG_UNUSED_OFFSET.value), is((short) 0));
         assertThat(byteBuffer.getShort(CARepeaterMessage.CaBeaconMessageOffsets.CA_HDR_SHORT_BEACON_MSG_SERVER_PROTOCOL_MINOR_VERSION_OFFSET.value), is((short) 99));
         assertThat(byteBuffer.getShort(CARepeaterMessage.CaBeaconMessageOffsets.CA_HDR_SHORT_BEACON_MSG_SERVER_TCP_LISTENING_PORT_OFFSET.value), is((short) 4444));
         assertThat(byteBuffer.getInt(CARepeaterMessage.CaBeaconMessageOffsets.CA_HDR_INT_BEACON_MSG_SERVER_BEACON_ID_OFFSET.value), is(0x12345678));
         assertThat(byteBuffer.getInt(CARepeaterMessage.CaBeaconMessageOffsets.CA_HDR_INT_BEACON_MSG_SERVER_ADDR_OFFSET.value), is(0xAABBCCDD));
      }
   }

   @Test
   void integrationTestSendRepeaterConfirmMessage() throws SocketException
   {
      // Use try with resources to ensure UDP Receiver listening socket gets closed
      // regardless of test outcome.
      try ( final UdpSocketReceiver receiver = UdpSocketReceiver.startListening(31247) )
      {
         try ( CARepeaterClientProxy proxy = new CARepeaterClientProxy(new InetSocketAddress( InetAddress.getLoopbackAddress(), 31247)) )
         {
            logger.info( "About to send..." );
            proxy.sendCaRepeaterConfirmMessage(InetAddress.getLoopbackAddress());
            logger.info( "Sent." );
         }

         final DatagramPacket receivePacket = receiver.getDatagram();

         // Check the received length is exactly the size of the header.
         assertThat(receivePacket.getLength(), is(CARepeaterMessage.CA_MESSAGE_HEADER_SIZE));

         // Now verify that the contents of the message is as expected for a CA_REPEATER_CONFIRM
         final ByteBuffer byteBuffer = ByteBuffer.wrap(receivePacket.getData());
         byteBuffer.limit(receivePacket.getLength());
         assertThat(byteBuffer.getShort(CARepeaterMessage.CaRepeaterConfirmMessageOffsets.CA_HDR_SHORT_REPEATER_CONFIRM_MSG_COMMAND_OFFSET.value), is(CARepeaterMessage.CaCommandCodes.CA_REPEATER_CONFIRM.value));
         assertThat(byteBuffer.getShort(CARepeaterMessage.CaRepeaterConfirmMessageOffsets.CA_HDR_SHORT_REPEATER_CONFIRM_MSG_UNUSED1_OFFSET.value), is((short) 0));
         assertThat(byteBuffer.getShort(CARepeaterMessage.CaRepeaterConfirmMessageOffsets.CA_HDR_SHORT_REPEATER_CONFIRM_MSG_UNUSED2_OFFSET.value), is((short) 0));
         assertThat(byteBuffer.getShort(CARepeaterMessage.CaRepeaterConfirmMessageOffsets.CA_HDR_SHORT_REPEATER_CONFIRM_MSG_UNUSED3_OFFSET.value), is((short) 0));
         assertThat(byteBuffer.getInt(CARepeaterMessage.CaRepeaterConfirmMessageOffsets.CA_HDR_INT_REPEATER_CONFIRM_MSG_UNUSED4_OFFSET.value), is(0));

         final InetAddress caRepeaterInetAddressAsInteger = InetAddressUtil.intToIPv4Address(byteBuffer.getInt(CARepeaterMessage.CaRepeaterConfirmMessageOffsets.CA_HDR_INT_REPEATER_CONFIRM_MSG_REPEATER_ADDR_OFFSET.value));
         assertThat(NetworkUtilities.isThisMyIpAddress(caRepeaterInetAddressAsInteger), is(true));
      }
   }

   @Test
   void testIsClientDead() throws SocketException
   {
      try ( DatagramSocket caRepeaterClientListener = UdpSocketUtilities.createBroadcastAwareListeningSocket(1111, false );
            CARepeaterClientProxy proxyToClientWhichIsAlive = new CARepeaterClientProxy( new InetSocketAddress( 1111 ) );
            CARepeaterClientProxy proxyToClientWhichIsDead = new CARepeaterClientProxy( new InetSocketAddress( 1112 ) ) )
      {
         assertThat( proxyToClientWhichIsAlive.isClientDead(), is( false ) );
         assertThat( proxyToClientWhichIsDead.isClientDead(), is( true ) );
      }

      try ( CARepeaterClientProxy proxyToGoneAwayClient = new CARepeaterClientProxy( new InetSocketAddress( 1111 ) ) )
      {
         assertThat( proxyToGoneAwayClient.isClientDead(), is( true ) );
      }
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}

