/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.epics.ca.ThreadWatcher;
import org.epics.ca.impl.JavaProcessManager;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class CARepeaterStatusCheckerTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final int TEST_PORT = 45783;
   private static final Logger logger = LibraryLogManager.getLogger( CARepeaterStatusCheckerTest.class );
   private ThreadWatcher threadWatcher;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/

   @BeforeAll
   static void beforeAll()
   {
      // This is a guard condition. There is no point in checking the behaviour
      // of this class if the network stack is not appropriately configured for
      // channel access.
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
      assertThat( CARepeaterStatusChecker.isRepeaterRunning( TEST_PORT ), is( false ) );
   }

   @AfterEach
   void afterEach()
   {
      assertThat( CARepeaterStatusChecker.verifyRepeaterStops( TEST_PORT ), is( true ) );
      assertDoesNotThrow( () -> threadWatcher.verify(), "Thread leak detected !" );
   }

   @Test
   void testNothingShouldBeRunningOnRepeaterPort()
   {
      // checked in beforeEach
   }

   @Test
   void testIsRepeaterRunning_detectsShareableSocketWhenReservedInSameJvm() throws Throwable
   {
      try ( DatagramSocket ignored = UdpSocketUtilities.createBroadcastAwareListeningSocket( TEST_PORT, true ) )
      {
         assertThat( CARepeaterStatusChecker.isRepeaterRunning( TEST_PORT), is( true  ) );
      }
      assertThat( CARepeaterStatusChecker.isRepeaterRunning( TEST_PORT), is( false ) );
   }

   @Test
   void testIsRepeaterRunning_detectsNonShareableSocketWhenReservedInSameJvm() throws Throwable
   {
      try ( DatagramSocket ignored = UdpSocketUtilities.createBroadcastAwareListeningSocket( TEST_PORT, true ) )
      {
         assertThat( CARepeaterStatusChecker.isRepeaterRunning( TEST_PORT ), is( true  ) );
      }
      assertThat( CARepeaterStatusChecker.isRepeaterRunning( TEST_PORT), is( false ) );
   }

   @MethodSource( "getArgumentsForTestIsRepeaterRunning_detectsSocketReservedInDifferentJvmOnDifferentLocalAddresses" )
   @ParameterizedTest
   void testIsRepeaterRunning_detectsSocketReservedInDifferentJvmOnDifferentLocalAddress( String localAddress ) throws Throwable
   {
      Validate.notNull( localAddress, "The localAddress was not provided." );
      logger.info( "Checking whether repeater instance detected on local address: '" + localAddress + "'" );

      // Spawn an external process to reserve a socket on port 5065 for 3000 milliseconds
      final int portToReserve = 5065;
      final int reservationTimeInMillis = 3000;
      final JavaProcessManager processManager = UdpSocketReserver.start( localAddress, portToReserve, reservationTimeInMillis );

      // Allow time for the process to reserve the socket and check that the isRepeaterRunning
      // method detects that the port is no longer available.
      Thread.sleep( 1500 );
      assertThat( processManager.isAlive(), is( true ) );
      assertThat( CARepeaterStatusChecker.isRepeaterRunning( 5065 ), is( true ) );

      // Wait for the process to finish and check that the socket has become available again.
      processManager.waitFor( 5000, TimeUnit.MILLISECONDS );
      assertThat( processManager.isAlive(), is( false ) );
      assertThat( CARepeaterStatusChecker.isRepeaterRunning( 5065 ), is( false ) );
   }

   @Test
   void testIsRepeaterRunning_startRepeaterInCurrentJvmProcess() throws Throwable
   {
      final CARepeater repeater = new CARepeater( TEST_PORT );
      repeater.start();
      assertThat( CARepeaterStatusChecker.verifyRepeaterStarts( TEST_PORT ), is( true ) );
      repeater.shutdown();
      assertThat( CARepeaterStatusChecker.verifyRepeaterStops( TEST_PORT ), is( true ) );
   }

/*- Private methods ----------------------------------------------------------*/
   
   private static Stream<Arguments> getArgumentsForTestIsRepeaterRunning_detectsSocketReservedInDifferentJvmOnDifferentLocalAddresses()
   {
      final List<Inet4Address> localAddressList = NetworkUtilities.getLocalNetworkInterfaceAddresses();
      final List<String> localAddresses = localAddressList.stream().map( Inet4Address::getHostAddress).collect(Collectors.toList());
      return localAddresses.stream().map( Arguments::of );
   }

/*- Nested Classes -----------------------------------------------------------*/

}
