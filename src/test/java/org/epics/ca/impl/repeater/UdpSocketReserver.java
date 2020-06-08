/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.epics.ca.impl.JavaProcessManager;
import org.epics.ca.util.logging.LibraryLogManager;

import java.net.*;
import java.util.Properties;
import java.util.logging.Logger;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Test helper class which reserves a user-specified UDP socket in non-shared
 * mode (SO_REUSEADDR = false) for a user-specified time interval.
 */
class UdpSocketReserver
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( UdpSocketReserver.class );

/*- Main ---------------------------------------------------------------------*/

   /**
    * Runs the socket reserver according to the supplied arguments.
    *
    * @param args the internet address to reserve, the port the port to reserve
    *    and the reservation time in milliseconds.
    */
   public static void main( String[] args )
   {
      if ( args.length != 3 )
      {
         logger.info( "Usage: java -cp <classpath> org.epics.ca.impl.repeater.UdpSocketReserver <arg1> <arg2> <arg3>");
         logger.info( "Where: arg1 = localAddress; arg2 = port; arg3 = reserveTimeInMillis" );
         return;
      }

      try
      {
         // UnknownHostException -->
         final InetAddress inetAddress = InetAddress.getByName(args[ 0 ]);
         final int port = Integer.parseInt(args[ 1 ]);
         final long sleepTimeInMillis = Integer.parseInt(args[ 2 ]);

         logger.info("Reserving socket: '" + inetAddress + ":" + port + "' for '" + sleepTimeInMillis + "' milliseconds.");

         try ( DatagramSocket listeningSocket = new DatagramSocket(null) )
         {
            // SocketException -->
            listeningSocket.setReuseAddress( false );
            listeningSocket.bind( new InetSocketAddress(inetAddress, port ) );
            logger.info("Sleeping...");

            // InterruptedException -->
            Thread.sleep( sleepTimeInMillis);
            logger.info("Awake again...");
            logger.info("Releasing socket.");
         }
      }
      catch( SocketException | UnknownHostException | InterruptedException ex )
      {
         logger.warning( "The program failed to run as expected due to the following exception: '" + ex + "'." );
         System.exit( -1 );
      }

      System.exit( 0 );
   }

/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/

   /**
    * Starts the Socket Reserver in a separate process.
    *
    * @param address the internet address to reserve.
    * @param port the port to reserve in the range 0-65535.
    * @param socketReservationTime the reservation time in milliseconds.
    *
    * @return reference to the manager which can be used subsequently to
    *    monitor or cancel the spawned process.
    *
    * @throws NullPointerException if the address was null.
    * @throws IllegalArgumentException if the address was blank.
    * @throws IllegalArgumentException if the port was outside the allowed range.
    * @throws IllegalArgumentException if the socket reservation time was outside the allowed range.
    */
   public static JavaProcessManager start( String address, int port, int socketReservationTime )
   {
      Validate.notBlank( address, "The reservation socket cannot be blank." );
      Validate.inclusiveBetween(0, 65535, port, "The port must be in the range 0-65535." );
      Validate.isTrue( socketReservationTime >= 0, "The reservation time must be greater than or equal to zero." );

      // Spawn an external process to reserve a socket on port 5065 for 5000 milliseconds
      final Properties properties = new Properties();
      properties.setProperty( "java.net.preferIPv4Stack", "true" );
      properties.setProperty( "java.net.preferIPv6Stack", "false" );
      final String[] programArgs = new String[] { address, String.valueOf( port ), String.valueOf( socketReservationTime ) };
      final JavaProcessManager processManager = new JavaProcessManager( UdpSocketReserver.class, properties, programArgs );
      final boolean started = processManager.start( true );
      return processManager;
   }

/*- Package-level methods ----------------------------------------------------*/
/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
