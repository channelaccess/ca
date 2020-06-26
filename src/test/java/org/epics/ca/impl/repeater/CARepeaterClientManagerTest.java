/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.repeater;


/*- Imported packages --------------------------------------------------------*/

import org.epics.ca.ThreadWatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.*;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class CARepeaterClientManagerTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private ThreadWatcher threadWatcher;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/

   @BeforeAll
   static void beforeAll()
   {
      // This is a guard condition. There is no point in checking the behaviour
      // of the CARepeaterStarterTest class if the network stack is not appropriately
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
   void testConstructor()
   {
      final Throwable throwable = assertThrows( NullPointerException.class, () -> new CARepeaterClientManager( null ) );
      assertThat( throwable.getMessage(), is( "The 'repeaterListeningAddress' argument was null." ) );
   }

   @Test
   void testForwardDatagram_doesNotThrowExceptions()
   {
      final InetSocketAddress repeaterListeningAddress = new InetSocketAddress(1999 );
      final CARepeaterClientManager manager = new CARepeaterClientManager( repeaterListeningAddress );
      final DatagramPacket datagramPacket = new DatagramPacket( new byte[] { (byte) 0xAA, (byte) 0xBB }, 0, 2  );
      final InetSocketAddress excluded = new InetSocketAddress( 333 );
      assertDoesNotThrow( () -> manager.forwardDatagram( datagramPacket, excluded ) );
   }

   @Test
   void testRegister()
   {
      assertTimeoutPreemptively( Duration.ofSeconds( 5 ), () -> {
         final InetSocketAddress repeaterListeningAddress = new InetSocketAddress(2322 );

         final CARepeaterClientManager manager = new CARepeaterClientManager( repeaterListeningAddress );
         final DatagramPacket rxPacket;
         try ( UdpSocketReceiver receiver = UdpSocketReceiver.startListening( 54213 ) )
         {
            manager.registerNewClient( new InetSocketAddress( InetAddress.getLocalHost(), 54213 ) );
            rxPacket = receiver.getDatagram();
         }
         assertThat(rxPacket.getLength(), is(16));
         final ByteBuffer byteBuffer = ByteBuffer.wrap( rxPacket.getData() );
         assertThat(byteBuffer.getShort(CARepeaterMessage.CaHeaderOffsets.CA_HDR_SHORT_COMMAND_OFFSET.value), is(CARepeaterMessage.CaCommandCodes.CA_REPEATER_CONFIRM.value));
      } );
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
