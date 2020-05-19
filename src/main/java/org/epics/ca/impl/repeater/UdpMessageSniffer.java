/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;


/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Provides support for monitoring CA messages associated with UDP broadcasts
 * sent to some port of interest.
 *
 * The CA protocol uses a mixture of broadcast and unicast messages.  For a simple
 * tool like this which does not leverage off the low-level, OS-specific, libraries
 * that support the network interface hardware, the unicast messages remain
 * invisible. This leaves only the following messages available for observation:
 * <Ul>
 *    <li>CA Search Messages - sent from the CA Repeater to the IOC's listening
 *    socket which typically operate on port 5064.</li>
 *    <li>CA Beacon messages - sent from the IOC Send socket to the CA Repeater
 *    which typically listens on port 5065.</li>
 * </Ul>
 */
public class UdpMessageSniffer
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/
/*- Main ---------------------------------------------------------------------*/

   /**
    * Starts the UDP Message Sniffer from the command line.
    *
    * The first and only argument should specify the port to monitor which
    * will typically be the ports on which CA messages are broadcast
    * (usually 5064 or 5065).
    *
    * @param argv arguments - should be exactly one.
    */
   public static void main( String[] argv )
   {
      if( ! NetworkUtilities.verifyTargetPlatformNetworkStackIsChannelAccessCompatible() )
      {
         return;
      }

      if ( argv.length != 1 )
      {
         System.out.println( "Usage: java -cp <caJarFile> org.epics.ca.impl.repeater.UdpMessageSniffer <port>" );
         return;
      }

      int port;
      try
      {
         port = Integer.parseInt( argv[ 0 ] );
      }
      catch( NumberFormatException ex)
      {
         System.out.println( "The supplied argument ('" + argv[ 0 ] + "') could not be converted to an integer." );
         return;
      }

      if ( ( port < 0 ) || (port > 65535 ) )
      {
         System.out.println( "The supplied argument ('" + argv[ 0 ] + "') was outside the allowed range (0-65535" );
         return;
      }

      sniff( port );

   }

/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level access methods ---------------------------------------------*/

   private static void sniff( int port )
   {
      DatagramSocket socket;
      try
      {
         socket = UdpSocketUtilities.createBroadcastAwareListeningSocket(port, true );
         //socket = new DatagramSocket(port, InetAddress.getByName( "0.0.0.0") );
         System.out.println ( "Listening for traffic on port " + port);
      }
      catch ( SocketException ex )
      {
         System.out.println( "ERROR: The listening socket on port " + port + " could not be opened." );
         return;
      }

      boolean socketErrors = false;
      while( ! socketErrors )
      {
         DatagramPacket rxPacket = new DatagramPacket( new byte[ 16384 ], 16384 );
         try
         {
            socket.receive(rxPacket);
         }
         catch( IOException ex )
         {
            System.out.println( "ERROR: The listening socket experienced a problem. This utility will quit." );
            socketErrors = true;
            continue;
         }

         System.out.println( "\nReceived packet from " + rxPacket.getSocketAddress() + " of length " + rxPacket.getLength() + " bytes.");
         boolean unprocessedMessages = true;
         while ( unprocessedMessages )
         {
            // Note the RX packet data is shortened by the length of the message that is consumed.
            System.out.println( CARepeaterMessage.extractMessageAsString(rxPacket ) );
            unprocessedMessages = rxPacket.getLength() > 0;
         }
      }
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
