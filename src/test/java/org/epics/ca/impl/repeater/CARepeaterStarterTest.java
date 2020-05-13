/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.repeater;


/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.*;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.epics.ca.impl.repeater.CARepeaterStarter.getLocalNetworkInterfaceAddresses;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

class CARepeaterStarterTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( CARepeaterStarterTest.class );
   private final int testPort = 5065;


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/

   @BeforeEach
   void beforeEach()
   {
      assertThat( "Test precondition failure: the repeater should NOT be running on port " + testPort + ".", CARepeaterStarter.isRepeaterRunning( testPort ), is (false ) );
   }

   @AfterEach
   void afterEach() throws InterruptedException
   {
      System.out.flush();
      System.err.flush();
      Thread.sleep( 1000 );
   }

   @Test
   void testNothingShouldBeRunningOnRepeaterPort()
   {
      // checked in beforeEach
   }

   @Test
   void testStartRepeaterInSeparateJvmProcess() throws Throwable
   {
      final Optional<Process> optProcess = CARepeaterStarter.startRepeaterInSeparateJvmProcess( testPort );
      assertThat( optProcess.isPresent(), is( true ) );
      assertThat( optProcess.get().isAlive(), is( true ) );
      Thread.sleep( 2000 );
      assertThat( optProcess.get().isAlive(), is( true ) );
      assertThat( CARepeaterStarter.isRepeaterRunning( testPort ), is( true ) );
      Thread.sleep( 5000 );
      optProcess.ifPresent( Process::destroy );
      Thread.sleep( 1000 );
      assertThat( optProcess.get().isAlive(), is( false ) );
      assertThat( CARepeaterStarter.isRepeaterRunning( testPort ), is( false ) );
   }

   @Test
   void testStartRepeaterInCurrentJvmProcess() throws Throwable
   {
      final CARepeater repeater = new CARepeater( testPort );
      repeater.start();
      Thread.sleep( 1000 );
      assertThat( CARepeaterStarter.isRepeaterRunning( testPort ), is( true ) );
      repeater.shutdown();
      Thread.sleep( 1000 );
      assertThat( CARepeaterStarter.isRepeaterRunning( testPort ), is( false ) );
   }

   @Test
   void testIsRepeaterRunning_detectsSocketReservedInSameJvm() throws Throwable
   {
      try ( DatagramSocket listeningSocket = SocketUtilities.createBroadcastAwareListeningSocket( testPort, false ) )
      {
         assertThat( CARepeaterStarter.isRepeaterRunning( testPort), is(true  ) );
      }

      assertThat( CARepeaterStarter.isRepeaterRunning( testPort), is(false ) );
      try ( DatagramSocket listeningSocket = SocketUtilities.createBroadcastAwareListeningSocket( testPort, true ) )
      {
         assertThat( CARepeaterStarter.isRepeaterRunning( testPort), is(true  ) );
      }
      assertThat( CARepeaterStarter.isRepeaterRunning( testPort), is(false ) );
   }


   @Test
   void testIsRepeaterRunning_detectsSocketReservedOnDifferentLocalAddresses() throws Throwable
   {
      final List<Inet4Address> localAddressList = getLocalNetworkInterfaceAddresses();
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
      final String classWithMainMethod =  SocketReserver.class.getName();
      final String portToReserve = "5065";
      final String reserveTimeInMillis = "5000";
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
      Thread.sleep( 1000 );

      assertThat( "The isRepeaterRunning method failed to detect that the socket was reserved.",
                  CARepeaterStarter.isRepeaterRunning( testPort ), is( true ) );

      logger.finer( "Waiting for process to complete..." );
      process.waitFor();
      logger.finer( "The test process completed." );

      assertThat( "The isRepeaterRunning method failed to detect that the socket is now available.",
                  CARepeaterStarter.isRepeaterRunning( testPort ), is( false ) );

      logger.info( "The test PASSED" );
   }


/*- Nested Classes -----------------------------------------------------------*/

   public static class SocketReserver
   {
      private static final Logger logger = LibraryLogManager.getLogger( SocketReserver.class );

      public static void main( String[] args ) throws SocketException, InterruptedException, UnknownHostException
      {
         if ( args.length != 3 )
         {
            logger.info( "Usage: java -cp <classpath> org.epics.ca.impl.repeater.CARepeaterStarterTest$SocketReserver <arg1> <arg2> <arg3>");
            logger.info( "Where: arg1 = localAddress; arg2 = port; arg3 = reserveTimeInMillis" );
            return;
         }

         final InetAddress inetAddress = InetAddress.getByName( args[ 0 ] );
         final int port = Integer.parseInt( args[ 1 ] );
         final long sleepTimeInMillis = Integer.parseInt( args[ 2 ] );

         logger.info( "Reserving socket: '" + inetAddress + ":" + port + "'." );

         try ( DatagramSocket listeningSocket = new DatagramSocket( null ) )
         {
            listeningSocket.setReuseAddress( false );
            listeningSocket.bind( new InetSocketAddress( inetAddress, port ) );
            logger.info( "Sleeping...");
            Thread.sleep( sleepTimeInMillis );
            logger.info( "Awake again...");
            logger.info( "Releasing socket.");
         }
      }
   }

}
