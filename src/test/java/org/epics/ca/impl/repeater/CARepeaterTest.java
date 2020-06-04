/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/

import org.epics.ca.Constants;

import org.epics.ca.util.logging.LibraryLogManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import static org.junit.jupiter.api.Assertions.*;


/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

class CARepeaterTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( CARepeaterTest.class );
   private CARepeater caRepeater;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/

   @BeforeAll
   static void beforeAll()
   {
      // This is a guard condition. There is no point in checking the behaviour
      // of the CARepeaterTest class if the network stack is not appropriately
      // configured for channel access.
      assertThat( NetworkUtilities.verifyTargetPlatformNetworkStackIsChannelAccessCompatible(), is( true ) );

      // Currently (2020-05-22) this test is not supported when the VPN connection is active on the local machine.
      if ( NetworkUtilities.isVpnActive() )
      {
         fail( "This test is not supported when a VPN connection is active on the local network interface." );
      }

   }

   @BeforeEach
   void beforeEach() throws CARepeater.CaRepeaterStartupException
   {
      logger.info( "Starting CA Repeater integration tests..." );
      logger.info( "Creating CA Repeater which will listen for broadcast messages on Port 43721." );
      caRepeater = new CARepeater( 43721 );
      logger.info( "Starting CA Repeater on port 43721." );
      caRepeater.start();
      logger.info( "Repeater has been started." );
   }

   @AfterEach
   void afterEach()
   {
      if ( caRepeater == null )
      {
         logger.info( "CA Repeater did not start correctly so no need to shut it down..." );
      }
      else
      {
         logger.info( "Shutting down CA Repeater..." );
         caRepeater.shutdown();
         logger.info( "Repeater has been shutdown." );
      }
      logger.info( "CARepeater test completed ok." );
   }


   @Test
   void testRunInternalCaRepeaterForTwoSeconds() throws InterruptedException
   {
      logger.info( "Sleeping for 2 seconds..." );
      Thread.sleep( 2000 );
      logger.info( "Sleeping time is over." );
   }

   /**
    * In this test we send a request to the CA Repeater asking it to register
    * a new client. We then flip the socket into receive mode and check that
    * the socket receives back a repeater confirm message.
    *
    * @param targetInetAddress the address of the CA Repeater to be targeted
    *    by this test.
    * @param targetPort the port on which the CA Repeater is expected to be
    *    listening.
    * @param useBroadcast whether to send the request using broadcast mode
    *    or not. Typically, CA Repeater clients request registration using
    *    broadcasts, but this is not always supported by the operating
    *    system.
    *
    * @throws IOException if the test fails to execute for some reason.
    */
   @MethodSource( "getArgumentsForIntegrationTestCaRepeater_registerNewClient" )
   @ParameterizedTest
   void integrationTestCaRepeater_registerNewClient( String targetInetAddress,
                                                     int targetPort,
                                                     boolean useBroadcast,
                                                     String advertisedListeningAddress ) throws IOException
   {

      logger.info( String.format( "Register new client test with params: %s %d %s %s", targetInetAddress, targetPort, useBroadcast, advertisedListeningAddress ) );

      // In this test we send a request to the CA Repeater asking it to register
      //  a new client. We then flip the socket into receive mode and check that
      // the socket receives a repeater confirm message.
      // SocketException -->
      try ( DatagramSocket testSocket = UdpSocketUtilities.createEphemeralSendSocket(true ) )
      {
         // Create a datagram packet containing a REPEATER_REGISTER message
         // UnknownHostException -->
         final DatagramPacket requestPacket = CARepeaterMessage.createRepeaterRegisterMessage( InetAddress.getByName( advertisedListeningAddress ) );

         // Send the datagram to the CA Repeater at the specified target socket
         // UnknownHostException -->
         requestPacket.setAddress( InetAddress.getByName( targetInetAddress ) );
         requestPacket.setPort( targetPort );

         // Create a datagram packet to receive any reply
         final byte[] replyMessage = new byte[ Constants.CA_MESSAGE_HEADER_SIZE ];
         final DatagramPacket replyPacket = new DatagramPacket( replyMessage, replyMessage.length );

         // Send the request and wait a limited time for any reply
         // IOException -->
         testSocket.send( requestPacket );
         assertTimeoutPreemptively( Duration.of( 1, SECONDS ), () -> testSocket.receive( replyPacket ) );

         // Check that the reply was a REPEATER_CONFIRM message
         assertThat( replyPacket.getLength(), is(16 ) );
         final ByteBuffer replyBuffer = ByteBuffer.wrap( replyMessage );
         final short commandReceived = replyBuffer.getShort( CARepeaterMessage.CaHeaderOffsets.CA_HDR_SHORT_COMMAND_OFFSET.value );
         final short commandExpected = CARepeaterMessage.CaCommandCodes.CA_REPEATER_CONFIRM.value;
         assertThat( commandReceived, is( commandExpected ) );
      }
   }

   @ValueSource( ints = {43721} )
   @ParameterizedTest
   void integrationTestCaRepeater_zeroLengthDatagram_registersClientWhoSentIt( int port) throws IOException
   {
      // Run this test in try-with-resources to ensure that resources get cleaned up
      try ( DatagramSocket caRepeaterClientSocket = UdpSocketUtilities.createEphemeralSendSocket(true ) )
      {
         // Send zero length datagram to CA Repeater Listening Port. This should
         // cause the CA Repeater to register a new client with the socket address
         // of the sender.
         final DatagramPacket requestPacket = new DatagramPacket( new byte[] {}, 0);
         requestPacket.setAddress( InetAddress.getLoopbackAddress() );
         requestPacket.setPort( port );
         caRepeaterClientSocket.send( requestPacket );

         // Wait for CA Repeater to reply.
         final byte[] replyMessage = new byte[ 1024 ];
         final DatagramPacket replyPacket = new DatagramPacket( replyMessage, replyMessage.length );
         final ByteBuffer replyBuffer = ByteBuffer.wrap( replyMessage );
         caRepeaterClientSocket.receive( replyPacket );

         // Verify that the reply was a CA_REPEATER_CONFIRM message.
         assertThat( replyPacket.getLength(), is(16 ) );
         final short commandReceived = replyBuffer.getShort( CARepeaterMessage.CaHeaderOffsets.CA_HDR_SHORT_COMMAND_OFFSET.value );
         final short commandExpected = CARepeaterMessage.CaCommandCodes.CA_REPEATER_CONFIRM.value;
         assertThat( commandReceived, is( commandExpected ) );
      }
   }

   @ValueSource( ints = {43721} )
   @ParameterizedTest
   void integrationTestCaRepeater_afterRegistration_allReceivedDatagrams_getPublishedToAllClients( int port ) throws IOException
   {
      // Run this test in try-with-resources to ensure that resources get cleaned up
      try( DatagramSocket caRepeaterClientSocketA = UdpSocketUtilities.createEphemeralSendSocket(true );
           DatagramSocket caRepeaterClientSocketB = UdpSocketUtilities.createEphemeralSendSocket(true );
           DatagramSocket caRepeaterClientSocketC = UdpSocketUtilities.createEphemeralSendSocket(true );
           DatagramSocket caRepeaterSenderSocket = UdpSocketUtilities.createEphemeralSendSocket(true ) )
      {
         // Client A: Send zero length datagram to the CA Repeater force registration of the socket as a CA Repeater client
         final DatagramPacket registrationRequestPacket = new DatagramPacket( new byte[] { (byte) 0 }, 0 );
         registrationRequestPacket.setAddress( InetAddress.getLoopbackAddress() );
         registrationRequestPacket.setPort( port );
         logger.info( "Client A: sending: registrationRequestPacket: " + Arrays.toString( registrationRequestPacket.getData() )  + " to: " + registrationRequestPacket.getSocketAddress()  + " from: " + caRepeaterClientSocketA.getLocalSocketAddress() );
         caRepeaterClientSocketA.send( registrationRequestPacket );
         logger.info( "Client B: sending: registrationRequestPacket: " + Arrays.toString( registrationRequestPacket.getData() )  + " to: " + registrationRequestPacket.getSocketAddress()  + " from: " + caRepeaterClientSocketB.getLocalSocketAddress() );
         caRepeaterClientSocketB.send( registrationRequestPacket );
         logger.info( "Client C: sending: registrationRequestPacket: " + Arrays.toString( registrationRequestPacket.getData() )  + " to: " + registrationRequestPacket.getSocketAddress()  + " from: " + caRepeaterClientSocketC.getLocalSocketAddress() );
         caRepeaterClientSocketC.send( registrationRequestPacket );

         // Client A: Wait for CA Repeater to reply and verify that the reply was a CA_REPEATER_CONFIRM message.
         logger.info( "Client A: waiting for CA repeater registration confirmation." );
         final byte[] replyMessageA = new byte[ CARepeaterMessage.CA_MESSAGE_HEADER_SIZE ];
         final DatagramPacket replyPacketA = new DatagramPacket( replyMessageA, replyMessageA.length );
         final ByteBuffer replyBufferA = ByteBuffer.wrap( replyMessageA );
         caRepeaterClientSocketA.receive( replyPacketA );
         logger.info( "Received: replyPacketA: " + Arrays.toString( replyPacketA.getData() )  + " from: " + replyPacketA.getSocketAddress() );
         assertThat(replyPacketA.getLength(), is(16 ) );
         final short commandReceivedA = replyBufferA.getShort( CARepeaterMessage.CaHeaderOffsets.CA_HDR_SHORT_COMMAND_OFFSET.value );
         final short commandExpectedA = CARepeaterMessage.CaCommandCodes.CA_REPEATER_CONFIRM.value;
         assertThat( commandReceivedA, is( commandExpectedA ) );
         logger.info( "Client A was registered." );

         // Client B: Wait for CA Repeater to reply and verify that the reply was a CA_REPEATER_CONFIRM message.
         logger.info( "Client B: waiting for CA repeater registration confirmation." );
         final byte[] replyMessageB = new byte[ CARepeaterMessage.CA_MESSAGE_HEADER_SIZE ];
         final DatagramPacket replyPacketB = new DatagramPacket( replyMessageB, replyMessageB.length );
         final ByteBuffer replyBufferB = ByteBuffer.wrap( replyMessageB );
         caRepeaterClientSocketB.receive( replyPacketB );
         logger.info( "Received: replyPacketB: " + Arrays.toString( replyPacketB.getData() )  + " from: " + replyPacketB.getSocketAddress() );
         assertThat(replyPacketB.getLength(), is(16 ) );

         final short commandReceivedB = replyBufferA.getShort( CARepeaterMessage.CaHeaderOffsets.CA_HDR_SHORT_COMMAND_OFFSET.value );
         final short commandExpectedB = CARepeaterMessage.CaCommandCodes.CA_REPEATER_CONFIRM.value;
         assertThat( commandReceivedB, is( commandExpectedA ) );
         logger.info( "Client B was registered." );

         // Client C: Wait for CA Repeater to reply and verify that the reply was a CA_REPEATER_CONFIRM message.
         logger.info( "Client C: waiting for CA repeater registration confirmation." );
         final byte[] replyMessageC = new byte[ CARepeaterMessage.CA_MESSAGE_HEADER_SIZE ];
         final DatagramPacket replyPacketC = new DatagramPacket( replyMessageC, replyMessageC.length );
         final ByteBuffer replyBufferC = ByteBuffer.wrap( replyMessageC );
         caRepeaterClientSocketC.receive( replyPacketC );
         logger.info( "Received: replyPacketC: " + Arrays.toString( replyPacketC.getData() )  + " from: " + replyPacketC.getSocketAddress() );
         assertThat(replyPacketC.getLength(), is(16 ) );
         final short commandReceivedC = replyBufferA.getShort( CARepeaterMessage.CaHeaderOffsets.CA_HDR_SHORT_COMMAND_OFFSET.value );
         final short commandExpectedC = CARepeaterMessage.CaCommandCodes.CA_REPEATER_CONFIRM.value;
         assertThat( commandReceivedC, is( commandExpectedA ) );
         logger.info( "Client C was registered." );

         // Send some "random" packet to the CA Repeater
         logger.info( "Sending UDP datagram to CA Repeater..." );
         final byte[] someRandomTxMessage = new byte[] { 1, 2, 3, 4, 5 };
         final DatagramPacket randomTxPacket = new DatagramPacket( someRandomTxMessage, someRandomTxMessage.length );
         randomTxPacket.setAddress( InetAddress.getLoopbackAddress() );
         randomTxPacket.setPort( port );
         caRepeaterSenderSocket.send( randomTxPacket );

         // Wait for CA Repeater ClientA to receive THREE datagrams
         final byte[] rxMessageA = new byte[ 32 ];
         final DatagramPacket rxPacketA = new DatagramPacket( rxMessageA, 32 );
         final ByteBuffer rxBufferA = ByteBuffer.wrap( rxMessageA );
         caRepeaterClientSocketA.receive( rxPacketA );
         logger.info( "Received: rxPacketA: " + Arrays.toString( rxPacketA.getData() ) + " from: " + rxPacketA.getSocketAddress() );
         caRepeaterClientSocketA.receive( rxPacketA );
         logger.info( "Received: rxPacketA: " + Arrays.toString( rxPacketA.getData() ) + " from: " + rxPacketA.getSocketAddress() );
         caRepeaterClientSocketA.receive( rxPacketA );
         logger.info( "Received: rxPacketA: " + Arrays.toString( rxPacketA.getData() ) + " from: " + rxPacketA.getSocketAddress() );
         assertThat( rxPacketA.getLength(), is(5 ) );
         assertThat( Arrays.copyOfRange( rxBufferA.array(), 0, someRandomTxMessage.length), is( someRandomTxMessage ) );

         // Wait for CA Repeater Client B to receive TWO datagrams
         final byte[] rxMessageB = new byte[ 32 ];
         final DatagramPacket rxPacketB = new DatagramPacket( rxMessageB, 32 );
         final ByteBuffer rxBufferB = ByteBuffer.wrap( rxMessageB );
         caRepeaterClientSocketB.receive( rxPacketB );
         logger.info( "Received: rxPacketB: " + Arrays.toString( rxPacketB.getData() )  + " from: " + rxPacketB.getSocketAddress() );
         caRepeaterClientSocketB.receive( rxPacketB );
         logger.info( "Received: rxPacketB: " + Arrays.toString( rxPacketB.getData() )  + " from: " + rxPacketB.getSocketAddress() );
         assertThat( rxPacketB.getLength(), is(5 ) );
         assertThat( Arrays.copyOfRange( rxBufferB.array(), 0, someRandomTxMessage.length), is( someRandomTxMessage ) );

         // Wait for CA Repeater Client C to receive ONE datagrams
         final byte[] rxMessageC = new byte[ 32 ];
         final DatagramPacket rxPacketC = new DatagramPacket( rxMessageC, 32 );
         final ByteBuffer rxBufferC = ByteBuffer.wrap( rxMessageC );
         caRepeaterClientSocketC.receive( rxPacketC );
         logger.info( "Received: rxPacketC: " + Arrays.toString( rxPacketC.getData() )  + " from: " + rxPacketC.getSocketAddress() );
         assertThat( rxPacketC.getLength(), is(5 ) );
         assertThat( Arrays.copyOfRange( rxBufferC.array(), 0, someRandomTxMessage.length), is( someRandomTxMessage ) );
      }
   }

/*- Private methods ----------------------------------------------------------*/

   private static Stream<Arguments> getArgumentsForIntegrationTestCaRepeater_registerNewClient()
   {
      final List<Inet4Address> broadcastAddresses = NetworkUtilities.getLocalBroadcastAddresses();
      final List<Arguments> args = broadcastAddresses.stream().map( addr -> {
         final String stringAddr = addr.getHostAddress();
         return Arguments.of( stringAddr, 43721, true, stringAddr );
      } ).collect(Collectors.toList() );

      args.add( Arguments.of( "127.0.0.1", 43721, false, "127.0.0.1" ) );

      return args.stream();
   }

/*- Nested Classes -----------------------------------------------------------*/

}
