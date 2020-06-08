/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/
/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.epics.ca.util.logging.LibraryLogManager;

import java.net.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides higher-level UDP socket functions for use in the CA Repeater.
 */
class UdpSocketUtilities
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( UdpSocketUtilities.class );

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/

   /**
    * Returns a datagram socket configured for the purposes of sending
    * UDP datagrams to multiple destinations using information specified
    * in the datagram at send time.
    *
    * The socket is returned in the UNBOUND and UNCONNECTED state. This
    * provides the possibility for it to be configured with special
    * options (eg broadcast capability etc) prior to use.
    *
    * The socket can subsequently be bound to a local address either by
    * manual invocation of the socket's <code>bind</code> method or
    * automatically on the first attempted send operation.
    *
    * @return the configured socket.
    * @throws SocketException if the socket could not be created for any reason.
    * @throws SecurityException if the operation was not allowed for any reason.
    */
   static DatagramSocket createUnboundSendSocket() throws SocketException
   {
      // Attempt to create a socket which is initially unbound. This potentially
      // allows it to be configured before performing the bind operation.
      // SocketException, SecurityException -->
      // Note: the local variable below is deliberately retained to assist in debugging.
      @SuppressWarnings( "UnnecessaryLocalVariable" )
      final DatagramSocket socket = UdpSocketUtilities.createUnboundSocket();

      // If no exceptions have occurred return the socket which is now ready for use.
      return socket;
   }

   /**
    * Returns a datagram socket configured for the purposes of sending
    * UDP datagrams to multiple destinations using information specified
    * in the datagram at send time.
    *
    * The returned socket's broadcast capability is configurable by
    * means of the supplied argument.
    *
    * The socket is returned in the UNCONNECTED state, bound by the
    * operating system to one of the local machine's EPHEMERAL ports
    * (that's to say a port that is automatically selected by the
    * operating system).
    *
    * @param enableBroadcasts determines whether the returned socket supports
    *    broadcast capabilities. (Support depends also on the OS).
    * @return the configured socket.
    * @throws SocketException if the socket could not be created for any reason.
    * @throws SecurityException if the operation was not allowed for any reason.
    */
   static DatagramSocket createEphemeralSendSocket( boolean enableBroadcasts ) throws SocketException
   {
      // Attempt to create a socket which is initially unbound. This potentially
      // allows it to be configured before performing the bind operation.
      // SocketException, SecurityException -->
      final DatagramSocket socket = UdpSocketUtilities.createUnboundSocket();

      // Could perform socket configuration here, but nothing special required.
      // Note: Broadcast is by default enabled anyway.
      socket.setBroadcast( enableBroadcasts );

      // Create the address/port to bind it to. For the purposes of data transmission
      // this should be an ephemeral port on the local machine.
      final int EPHEMERAL_PORT = 0;
      final InetSocketAddress inetSocketAddress = new InetSocketAddress( EPHEMERAL_PORT );

      // Attempt to bind the newly created socket to the local address and port.
      // SocketException, SecurityException -->
      socket.bind( inetSocketAddress );

      // If no exceptions have occurred return the socket which is now ready for use.
      return socket;
   }

   /**
    * Returns a datagram socket configured for the purposes of listening to UDP
    * broadcast traffic on all available local network interfaces.
    *
    * In network terms this is achieved by creating a socket that is bound
    * to the so-called WILDCARD address (0.0.0.0) on the specified port.
    * The host network implementation is then required to ensure that
    * the socket receive BROADCAST messages from ANY of the local
    * networks associated with the host.
    *
    * @param port positive integer in the range 1-65535, specifying the port
    *    to listen on.
    * @param shareable determines whether the port is shareable with other
    *        operating system users, or whether it should be dedicated for
    *        the exclusive use of the calling client.
    * @return the configured socket.
    *
    * @throws IllegalArgumentException if the port was zero or negative.
    * @throws SocketException if the socket could not be created for any reason.
    */
   static DatagramSocket createBroadcastAwareListeningSocket( int port, boolean shareable ) throws SocketException
   {
      Validate.inclusiveBetween( 1, 65535, port );

      // Attempt to create a socket which is initially unbound. This
      // allows it to be configured before performing the bind operation.
      // SocketException, SecurityException -->
      final DatagramSocket socket = new DatagramSocket(null );

      // Create the address/port object to bind to.
      // IlegalArgumentException -->
      final InetSocketAddress wildcardBindAddress = new InetSocketAddress( port );

      // Configure the SO_REUSEADDR socket option which allows multiple sockets
      // to be bound to the same socket address. This is typically required for
      // the purpose of receiving multicast packets (a feature not used in the
      // current implementation of this CaRepeater). Another use for the flag
      // is to act as a sempahore to ensure that multiple CA repeaters do not
      // run on the same port.
      // Note: this operation MUST occur before any bind operation since attempts
      // to configure a socket after binding result in undefined behaviour.
      socket.setReuseAddress( shareable );

      // Attempt to bind the newly created socket to the specified port.
      // SocketException, SecurityException -->

      logger.finest( "Binding to: " + wildcardBindAddress );
      socket.bind( wildcardBindAddress );

      // Validate the post-condition that the configured socket reuse mode is
      // as specified by the shareable port argument. This may not be available
      // on some platforms. See the Stack Exchange discussion here:
      // https://stackoverflow.com/questions/14388706/how-do-so-reuseaddr-and-so-reuseport-differ
      Validate.isTrue( socket.getReuseAddress() == shareable, "The Socket REUSE mode was not as expected. Perhaps it is not supported on this platform." );

      // Validate the post-condition that the configured socket is
      // broadcast-aware.
      Validate.isTrue( socket.getBroadcast(), "The Socket BROADCAST mode was not as expected. Perhaps it is not supported on this platform." );
      return socket;
   }

   /**
    * Returns a boolean indicator of whether the supplied target socket is available.
    *
    * @param targetSocketAddress the target socket address to check.
    * @return the result.
    * @throws RuntimeException if some unexpected condition prevented the check
    *    from being made (for example SecurityException )
    */
   static boolean isSocketAvailable( InetSocketAddress targetSocketAddress )
   {
      boolean isAvailable;

      logger.finest( "Checking whether a UDP socket is available for target address '" + targetSocketAddress + "'." );

      // Need to create unbound first so that we can configure the socket as we want it.
      // SocketException -->
      try( final DatagramSocket socket = new DatagramSocket( null ) )
      {
         logger.finest( "Socket created ok." );

         // Strive to configure the socket for exclusive access.
         // SocketException -->
         socket.setReuseAddress( false );
         logger.finest( "Socket SO_REUSE set to FALSE ok." );

         // Now attempt to bind to the specified target address
         // SocketException -->
         // Security Exception -->
         // IllegalArgumentException
         try
         {
            socket.bind( targetSocketAddress );
            logger.finest("Socket bind ok.");
            return true;
         }
         catch ( SocketException ex )
         {
            // This is the exception which gets thrown if the socket is already in use.
            // An exception here is considered a "normal" part of the test.
            return false;
         }
      }
      catch ( RuntimeException | SocketException ex )
      {
         // An exception here is not expected. Where necessary convert
         // to a runtime exception, but always put a warning in the log.
         final String msg = "An unexpected exception was thrown.";
         logger.log( Level.WARNING, msg, ex );
         throw new RuntimeException( msg, ex );
      }
   }


/*- Private methods ----------------------------------------------------------*/

   /**
    * Returns an unbound datagram socket. That is a socket which is not
    * yet associated with any local address or port.
    *
    * An unbound socket will be automatically bound by the OS on the first
    * attempted send or receive operation.
    *
    * @return the socket.
    * @throws SocketException if the socket could not be opened.
    * @throws SecurityException if the operation was not allowed for any reason.
    */
   private static DatagramSocket createUnboundSocket() throws SocketException
   {
      return new DatagramSocket (null );
   }


/*- Nested Classes -----------------------------------------------------------*/

}
