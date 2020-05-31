/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/

import org.epics.ca.util.logging.LibraryLogManager;

import java.net.*;
import java.util.logging.Logger;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Test helper class which reserves a UDP socket for a user-specified time interval.
 */
class UdpSocketReserver
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger(CARepeaterStarterTest.class );

/*- Main ---------------------------------------------------------------------*/

   public static void main( String[] args ) throws SocketException, InterruptedException, UnknownHostException
   {
      if ( args.length != 3 )
      {
         logger.info( "Usage: java -cp <classpath> org.epics.ca.impl.repeater.UdpSocketReserver <arg1> <arg2> <arg3>");
         logger.info( "Where: arg1 = localAddress; arg2 = port; arg3 = reserveTimeInMillis" );
         return;
      }

      final InetAddress inetAddress = InetAddress.getByName(args[ 0 ] );
      final int port = Integer.parseInt( args[ 1 ] );
      final long sleepTimeInMillis = Integer.parseInt( args[ 2 ] );

      logger.info( "Reserving socket: '" + inetAddress + ":" + port + "'." );

      try ( DatagramSocket listeningSocket = new DatagramSocket(null ) )
      {
         listeningSocket.setReuseAddress( false );
         listeningSocket.bind( new InetSocketAddress(inetAddress, port ) );
         logger.info( "Sleeping...");
         Thread.sleep( sleepTimeInMillis );
         logger.info( "Awake again...");
         logger.info( "Releasing socket.");
      }
   }

/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/
/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
