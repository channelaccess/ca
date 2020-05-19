/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.time.StopWatch;
import org.epics.ca.util.logging.LibraryLogManager;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Provides support for testing a user-specified socket to see whether it
 * is available and reservable.
 */
public class UdpSocketTester
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/
/*- Main ---------------------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( UdpSocketTester.class );

   /**
    * Starts the Socket Tester from the command line.
    *
    * The first argument should specify the port to test which will typically be the
    * ports on which are CA-relevant (usually 5062, 5064 or 5065). The second argument
    * should specify how long to run the test for (in seconds).
    *
    * During the test period the program will run the following loop:
    * - check that the specified UDP socket is available.
    * - reserve the socket.
    * - check that is unavailable.
    * - release the socket.
    * - repeat.
    *
    * @param args arguments - should be exactly one.
    */
   public static void main( String[] args )
   {
      if( ! NetworkUtilities.verifyTargetPlatformNetworkStackIsChannelAccessCompatible() )
      {
         return;
      }

      if ( args.length != 2 )
      {
         System.out.println( "Usage: java -cp <caJarFile> org.epics.ca.impl.repeater.UdpSocketTester <port> <testPeriodInSeconds>" );
         return;
      }

      int port;
      try
      {
         port = Integer.parseInt( args[ 0 ] );
      }
      catch( NumberFormatException ex)
      {
         System.out.println( "The supplied argument ('" + args[ 0 ] + "') could not be converted to an integer." );
         return;
      }

      if ( ( port < 0 ) || (port > 65535 ) )
      {
         System.out.println( "The supplied argument ('" + args[ 0 ] + "') was outside the allowed range (0-65535" );
         return;
      }

      int testPeriodInSeconds;
      try
      {
         testPeriodInSeconds = Integer.parseInt( args[ 1 ] );
      }
      catch( NumberFormatException ex)
      {
         System.out.println( "The supplied argument ('" + args[ 1 ] + "') could not be converted to an integer." );
         return;
      }

      if ( testPeriodInSeconds < 1 )
      {
         System.out.println( "The supplied argument ('" + args[ 1 ] + "') was not positive." );
         return;
      }

      if ( checkSocket( port, testPeriodInSeconds ) )
      {
         System.out.println( "The test PASSED. The socket was detected as being both available and reservable." );
         System.exit( 0 );
      }
      else
      {
         System.out.println( "The test FAILED. The socket is NOT available/reservable." );
         System.exit(  -1 );
      }
   }

/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level access methods ---------------------------------------------*/

   public static boolean checkSocket( int testPort, int testPeriodInSeconds )
   {
      Validate.inclusiveBetween( 0, 65535, testPort );
      Validate.isTrue( testPeriodInSeconds > 0 );

      System.out.println( "Checking socket based on port: '" + testPort + "'..." );

      final InetSocketAddress wildcardAddress = new InetSocketAddress( testPort );
      final StopWatch stopWatch = StopWatch.createStarted();
      while ( stopWatch.getTime( TimeUnit.SECONDS )< testPeriodInSeconds )
      {
         logger.finest( "Checking that the socket is detected as being available: " + wildcardAddress );
         if ( ! UdpSocketUtilities.isSocketAvailable(wildcardAddress )  )
         {
            System.out.println( "The socket was NOT detected as being available." );
            System.exit( -1 );
         }
         logger.finest( "Socket available." );
         try ( DatagramSocket dummy = UdpSocketUtilities.createBroadcastAwareListeningSocket(testPort, false ) )
         {
            logger.finest( "Socket reserved." );
            if ( UdpSocketUtilities.isSocketAvailable(wildcardAddress )  )
            {
               logger.finest( "Socket available = true" );
               System.out.println( "The socket was NOT detected as being reserved." );
               return false;
            }
         }
         catch (  SocketException ex  )
         {
            System.out.println( "The socket was NOT reservable." );
         }
      }
      return true;

   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
