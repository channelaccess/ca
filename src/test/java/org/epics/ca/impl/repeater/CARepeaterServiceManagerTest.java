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

import java.net.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.fail;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class CARepeaterServiceManagerTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( CARepeaterServiceManagerTest.class );
   private ThreadWatcher threadWatcher;
   
   private static final int testPort = 5065;
   private static final Level caRepeaterDebugLevel = Level.ALL;
   private static final boolean caRepeaterOutputCaptureEnable = false;

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
      
      final InetSocketAddress wildcardAddress = new InetSocketAddress( 5065 );
      logger.info( "Checking Test Precondition: CA Repeater should NOT be running... on: " + wildcardAddress );

      if ( CARepeaterServiceManager.isRepeaterRunning( testPort ) )
      {
         logger.info( "Test Precondition FAILED: the CA Repeater was detected to be already running." );
         fail();
      }
      else
      {
         logger.info( "Test Precondition OK: the CA Repeater was not detected to be already running." );
      }
      logger.info( "Starting Test." );
   }

   @AfterEach
   void afterEach()
   {
      threadWatcher.verify();
   }

   @Test
   void testNothingShouldBeRunningOnRepeaterPort()
   {
      // checked in beforeEach
   }

   @Test
   void testCARepeaterServiceManager_requestAndCancelService()
   {
      CARepeaterServiceManager.requestServiceOnPort( 2 );
      assertThat( CARepeaterServiceManager.getServiceInstances(), is( 1 ) );

      CARepeaterServiceManager.cancelServiceRequestOnPort( 2 );
      assertThat( CARepeaterServiceManager.getServiceInstances(), is( 0 ) );
   }

   @Test
   void testCARepeaterServiceManager_requestAndCancelServiceTwiceOnSamePort()
   {
      assertThat( CARepeaterServiceManager.getServiceInstances(), is( 0 ) );

      CARepeaterServiceManager.requestServiceOnPort( 2 );
      assertThat( CARepeaterServiceManager.getServiceInstances(), is( 1 ) );

      CARepeaterServiceManager.requestServiceOnPort( 2 );
      assertThat( CARepeaterServiceManager.getServiceInstances(), is( 1 ) );

      CARepeaterServiceManager.cancelServiceRequestOnPort( 2 );
      assertThat( CARepeaterServiceManager.getServiceInstances(), is( 1 ) );

      CARepeaterServiceManager.cancelServiceRequestOnPort( 2 );
      assertThat( CARepeaterServiceManager.getServiceInstances(), is( 0 ) );
   }

   @Test
   void testCARepeaterServiceManager_requestAndCancelServiceOnDifferentPorts()
   {
      assertThat( CARepeaterServiceManager.getServiceInstances(), is( 0 ) );

      CARepeaterServiceManager.requestServiceOnPort( 22 );
      assertThat( CARepeaterServiceManager.getServiceInstances(), is( 1 ) );

      CARepeaterServiceManager.requestServiceOnPort( 33 );
      assertThat( CARepeaterServiceManager.getServiceInstances(), is( 2 ) );

      CARepeaterServiceManager.cancelServiceRequestOnPort( 999 );
      assertThat( CARepeaterServiceManager.getServiceInstances(), is( 2 ) );

      CARepeaterServiceManager.cancelServiceRequestOnPort( 22 );
      assertThat( CARepeaterServiceManager.getServiceInstances(), is( 1 ) );

      CARepeaterServiceManager.cancelServiceRequestOnPort( 33 );
      assertThat( CARepeaterServiceManager.getServiceInstances(), is( 0 ) );
   }

   @Test
   void testIsRepeaterRunning_detectsShareableSocketWhenReservedInSameJvm() throws Throwable
   {
      try ( DatagramSocket ignored = UdpSocketUtilities.createBroadcastAwareListeningSocket( testPort, true ) )
      {
         assertThat( CARepeaterServiceManager.isRepeaterRunning( testPort), is( true  ) );
      }
      assertThat( CARepeaterServiceManager.isRepeaterRunning( testPort), is( false ) );
   }

   @Test
   void testIsRepeaterRunning_detectsNonShareableSocketWhenReservedInSameJvm() throws Throwable
   {
      try ( DatagramSocket ignored = UdpSocketUtilities.createBroadcastAwareListeningSocket(testPort, false ) )
      {
         assertThat( CARepeaterServiceManager.isRepeaterRunning( testPort), is( true  ) );
      }
      assertThat( CARepeaterServiceManager.isRepeaterRunning( testPort), is( false ) );
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
      assertThat( "The isRepeaterRunning method failed to detect that the socket was reserved.",
                  CARepeaterServiceManager.isRepeaterRunning( testPort ), is( true ) );

      // Wait for the process to finish and check that the socket has become available again.
      processManager.waitFor( 5000, TimeUnit.MILLISECONDS );
      assertThat( processManager.isAlive(), is( false ) );
      assertThat( "The isRepeaterRunning method failed to detect that the socket is now available.",
                  CARepeaterServiceManager.isRepeaterRunning( testPort ), is( false ) );

     logger.info( "The test PASSED." );
   }

   @Test
   void testIsRepeaterRunning_startRepeaterInCurrentJvmProcess() throws Throwable
   {
      final CARepeater repeater = new CARepeater( testPort );
      repeater.start();
      Thread.sleep( 1500 );
      assertThat( CARepeaterServiceManager.isRepeaterRunning( testPort ), is( true ) );
      repeater.shutdown();
      Thread.sleep( 1500 );
      assertThat( CARepeaterServiceManager.isRepeaterRunning( testPort ), is( false ) );
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
