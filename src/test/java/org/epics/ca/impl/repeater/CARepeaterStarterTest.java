/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.repeater;


/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.fail;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class CARepeaterStarterTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( CARepeaterStarterTest.class );
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
      final InetSocketAddress wildcardAddress = new InetSocketAddress( 5065 );
      logger.info( "Checking Test Precondition: CA Repeater should NOT be running... on: " + wildcardAddress );

      if ( CARepeaterStarter.isRepeaterRunning( testPort ) )
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
   void afterEach() throws InterruptedException
   {
      logger.info( "Cleaning up after test..." );
      logger.finest( "Collecting final output..." );
      Thread.sleep( 500 );
      logger.finest( "Flushing Streams..." );
      System.out.flush();
      System.err.flush();
      logger.info( "Test finished." );
   }

   @Test
   void testNothingShouldBeRunningOnRepeaterPort()
   {
      // checked in beforeEach
   }

   @Test
   void testStartRepeaterInSeparateJvmProcess() throws Throwable
   {
      logger.info( "Starting CA Repeater in separate process." );
      final Process process = CARepeaterStarter.startRepeaterInSeparateJvmProcess( testPort, caRepeaterDebugLevel, caRepeaterOutputCaptureEnable );
      logger.info( "The CA Repeater process was created." );
      logger.info( "Verifying that the CA Repeater process is reported as being alive..." );
      assertThat( process.isAlive(), is( true ) );
      logger.info( "OK" );
      logger.info( "Waiting a moment to allow the spawned process time to reserve the listening port..." );
      Thread.sleep( 1500 );
      logger.info( "Waiting completed." );
      final boolean alreadyTerminated = process.waitFor(1, TimeUnit.SECONDS );
      assertThat( alreadyTerminated, is( false ) );
      logger.info( "Verifying that the CA Repeater process is reported as being alive..." );
      assertThat( process.isAlive(), is( true ) );
      logger.info( "OK" );
      logger.info( "Letting the CA Repeater run for a few moments..." );
      Thread.sleep( 1500 );
      logger.info( "Verifying that the CA Repeater is detected as running..." );
      assertThat( CARepeaterStarter.isRepeaterRunning( testPort ), is( true ) );
      logger.info( "OK" );
      logger.info( "Killing the CA Repeater process..." );
      process.destroy();
      logger.info( "Killed !" );
      Thread.sleep( 1500 );
      logger.info( "Verifying that the CA Repeater process is no longer reported as being alive..." );
      assertThat( process.isAlive(), is( false ) );
      logger.info( "OK" );
      logger.info( "Verifying that the CA Repeater is no longer detected as running..." );
      assertThat( CARepeaterStarter.isRepeaterRunning( testPort ), is( false ) );
      logger.info( "OK" );
   }

   @Test
   void testStartRepeaterInCurrentJvmProcess() throws Throwable
   {
      final CARepeater repeater = new CARepeater( testPort );
      repeater.start();
      Thread.sleep( 1500 );
      assertThat( CARepeaterStarter.isRepeaterRunning( testPort ), is( true ) );
      repeater.shutdown();
      Thread.sleep( 1500 );
      assertThat( CARepeaterStarter.isRepeaterRunning( testPort ), is( false ) );
   }

   @Test
   void testIsRepeaterRunning_detectsShareableSocketWhenReservedInSameJvm() throws Throwable
   {
      try ( DatagramSocket listeningSocket = UdpSocketUtilities.createBroadcastAwareListeningSocket( testPort, true ) )
      {
         assertThat( CARepeaterStarter.isRepeaterRunning( testPort), is(true  ) );
      }
      assertThat( CARepeaterStarter.isRepeaterRunning( testPort), is(false ) );
   }

   @Test
   void testIsRepeaterRunning_detectsNonShareableSocketWhenReservedInSameJvm() throws Throwable
   {
      try ( DatagramSocket listeningSocket = UdpSocketUtilities.createBroadcastAwareListeningSocket(testPort, false ) )
      {
         assertThat( CARepeaterStarter.isRepeaterRunning( testPort), is(true  ) );
      }
      assertThat( CARepeaterStarter.isRepeaterRunning( testPort), is(false ) );
   }

   @Test
   void testIsRepeaterRunning_detectsSocketReservedInDifferentJvmOnDifferentLocalAddresses() throws Throwable
   {
      final List<Inet4Address> localAddressList = NetworkUtilities.getLocalNetworkInterfaceAddresses();
      for ( Inet4Address localAddress : localAddressList )
      {
         testIsRepeaterRunning_detectsSocketReservedInDifferentJvmOnDifferentLocalAddress( localAddress.getHostAddress() );
      }
   }

/*- Private methods ----------------------------------------------------------*/

   private void testIsRepeaterRunning_detectsSocketReservedInDifferentJvmOnDifferentLocalAddress( String localAddress ) throws Throwable
   {
      Validate.notNull( localAddress, "The localAddress was not provided." );

      logger.finer( "Checking whether repeater instance detected on local address: '" + localAddress + "'" );

      // Spawn an external process to reserve a socket on port 5065 for 5000 milliseconds
      final String classPath=  System.getProperty( "java.class.path", "<java.class.path not found>" );
      final String classWithMainMethod =  UdpSocketReserver.class.getName();
      final String portToReserve = "5065";
      final String reserveTimeInMillis = "3000";
      final Process process = new ProcessBuilder().command( "java",
                                                            "-cp",
                                                            classPath,
                                                            classWithMainMethod,
                                                            localAddress,
                                                            portToReserve,
                                                            reserveTimeInMillis )
            .inheritIO()
            .start();

      assertThat( "Test error: The test process did not start.", process.isAlive(), is( true ) );

      // Allow some time for the CA Repeater to reserve the socket
      Thread.sleep( 1500 );

      assertThat( "The isRepeaterRunning method failed to detect that the socket was reserved.",
                  CARepeaterStarter.isRepeaterRunning( testPort ), is( true ) );

      logger.finer( "Waiting for process to complete..." );
      process.waitFor();
      logger.finer( "The test process completed." );

      assertThat( "The isRepeaterRunning method failed to detect that the socket is now available.",
                  CARepeaterStarter.isRepeaterRunning( testPort ), is( false ) );

      logger.info( "The test PASSED." );
   }


/*- Nested Classes -----------------------------------------------------------*/

}
