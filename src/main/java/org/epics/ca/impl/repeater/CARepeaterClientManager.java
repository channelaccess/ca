/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.repeater;


/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.epics.ca.util.logging.LibraryLogManager;

import java.net.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Provides operations for registering a new CA Repeater Client and for
 * performing bulk operations (send, housekeeping...) on the clients that
 * have previously been registered.
 */
class CARepeaterClientManager
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( CARepeaterClientManager.class );
   private final Map<InetSocketAddress,CARepeaterClientProxy> clientMap = Collections.synchronizedMap( new HashMap<>() );
   private final InetSocketAddress repeaterListeningSocketAddress;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Constructs a new instance which will advertise the CA Repeater's
    * availability at the specified socket.
    *
    * @param repeaterListeningSocketAddress the socket on which the CA
    *    repeater will advertise it is listening.
    */
   public CARepeaterClientManager( InetSocketAddress repeaterListeningSocketAddress )
   {
      this.repeaterListeningSocketAddress = Validate.notNull( repeaterListeningSocketAddress );
   }


/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/

   /**
    * Registers a new CA Repeater client and attempts to send it a CA_REPEATER_CONFIRM
    * message to indicate that it will be forwarded future beacon messages as they
    * arrive at the CA Repeater.
    *
    * Subsequent to the above, deregisters any clients who are no longer active.
    *
    * @param clientListeningSocketAddress specifies the address of the socket
    *    on which the new CA repeater client is expected to be listening.
    *    Since the CA Repeater is intended only for LOCAL clients this should
    *    correspond to a port on the local machine.
    * @throws NullPointerException if the clientListeningSocketAddress argument was null.
    */
   void registerNewClient( InetSocketAddress clientListeningSocketAddress )
   {
      Validate.notNull( clientListeningSocketAddress );

      logger.finest( "Attempting to register new client listening at socket address: " + clientListeningSocketAddress);

      // Attempt to create a new CA Repeater Client Proxy.
      final CARepeaterClientProxy proxy;
      try
      {
         logger.finest( "Creating new proxy for CA Repeater Client... ");
         // SocketException -->
         proxy = new CARepeaterClientProxy( clientListeningSocketAddress );
         logger.finest( "The proxy was created OK. ");
      }
      catch( SocketException ex )
      {
         logger.log( Level.WARNING, "The proxy could not be created.", ex );
         return;
      }

      // Attempt to send the new repeater client a CA_REPEATER_CONFIRM message.
      logger.finest( "Sending repeater registration confirm message to '" + clientListeningSocketAddress + "'." );
      if( proxy.sendCaRepeaterConfirmMessage( repeaterListeningSocketAddress.getAddress() ) )
      {
         // If the message was sent successfully then add the new proxy to
         // the map of managed proxies.
         logger.finest( "Adding new client to list of registered CA repeater clients." );
         clientMap.put( clientListeningSocketAddress, proxy );
         logger.finest( "The list now contains " + clientMap.size() + " clients." );
      }
      else
      {
         // If the confirmation could not be sent then free up the resources
         // and escalate the unexpected condition.
         proxy.close();
         logger.log( Level.WARNING, "Failed to send repeater registration confirm message." );
         return;
      }

      // Attempt to send previously existing clients a CA_PROTO_VERSION message. Why do we do this ?
      // The only hint is provided by the comment below taken from a previous version of the CA Repeater.
      // "send noop message to all other clients, not to accumulate clients when there are no beacons"
      logger.finest( "Sending repeater protocol version message to registered clients..." );
      logger.finest( "Will exclude the newly created client..." + clientListeningSocketAddress );
      final List<CARepeaterClientProxy> failedNotifications = sendVersionMessageToRegisteredClients( clientListeningSocketAddress );

      // Provide some visibility of notification failures in the log.
      if ( failedNotifications.size() > 0 )
      {
         logger.warning( "Failed to send protocol version message to one or more registered clients." );
      }

      // Every time a new client is created perform housekeeping on the
      // list of registered clients to remove any dead ones.
      logger.finest( "Performing housekeeping on registered clients..." );
      removeDeadClients();
   }

   /**
    * Forwards a beacon message using the supplied parameters to all registered CA Repeater clients
    * with the exception of any whose socket address matched the excluded address.
    *
    * Subsequently, deregisters any clients who are no longer active.
    *
    * @param serverProtocolMinorVersion the version number of the CA protocol running on the CA Server.
    * @param serverListeningPort the TCP port on which the CA Server is listening.
    * @param serverBeaconId the CA Server's sequential Beacon ID.
    * @param serverAddress the address of the CA server to be encoded in the message.
    * @param excluded the socket address of any client who should be excluded from notification.
    *    This parameter can be used to prevent CA repeater clients who originate beacon messages from having
    *    those messages reflected back to themselves.
    */
   void forwardBeacon( short serverProtocolMinorVersion, short serverListeningPort, int serverBeaconId, InetAddress serverAddress, InetSocketAddress excluded )
   {
      Validate.notNull( serverAddress );
      Validate.notNull( excluded );

      logger.finest( "Forwarding beacon with ID: " + serverBeaconId + " to " + clientMap.size() + " CA Repeater clients." );
      logger.finest( "Any CA Repeater client with socket address to " + excluded + " will be excluded from notification." );
      final List<CARepeaterClientProxy> failedNotifications = sendBeaconMessageToRegisteredClients( serverProtocolMinorVersion, serverListeningPort, serverBeaconId, serverAddress, excluded );

      // Every time a new client is created perform housekeeping on the
      // list of registered clients to remove any dead ones.
      logger.finest( "Performing housekeeping on registered clients..." );
      removeDeadClients();
   }

   /**
    * Forwards the supplied datagram to all registered CA Repeater clients with the
    * exception of any whose socket address matched the excluded address.
    *
    * Subsequent to the above, deregisters any clients who are no longer active.
    *
    * @param packet the datagram.
    * @param excluded the socket address of any client who should be excluded from notification.
    *    This parameter can be used to prevent CA repeater clients who originate datagrams
    *    from having those datagrams reflected back to themselves.
    */
   void forwardDatagram( DatagramPacket packet, InetSocketAddress excluded  )
   {
      Validate.notNull( packet );
      Validate.isTrue( packet.getSocketAddress() instanceof InetSocketAddress );

      logger.finest( "Forwarding datagram packet to " + clientMap.size() + " CA Repeater clients." );
      logger.finest( "Any CA Repeater client with socket address to " + excluded + " will be excluded from notification." );

      // Create a datagram packet using the same data but which does not specify the datagram
      // destination address.
      final DatagramPacket sendPacket = new DatagramPacket( packet.getData(), packet.getLength() );
      final List<CARepeaterClientProxy> failedNotifications = sendDatagramToRegisteredClients(sendPacket, excluded );
      logger.finest( "There were " + failedNotifications.size() + " send failures." );

      // Every time a datagram is sent perform housekeeping on the
      // list of registered clients to remove any dead ones.
      logger.finest( "Performing housekeeping on registered clients..." );
      removeDeadClients();
   }

   /**
    * Indicates whether the specified listening port is already assigned to one
    * of the clients within this manager's list of registered clients.
    *
    * @param listeningPort the listening port to test.
    *
    * @return the result, set true if a lcient is already listening on the specified port.
    */
   boolean isListeningPortAlreadyAssigned( int listeningPort )
   {
      return clientMap.keySet()
            .stream()
            .anyMatch( p -> p.getPort() == listeningPort );
   }


/*- Private methods ----------------------------------------------------------*/

   /**
    * Sequentially sends the specified beacon message to all registered CA Repeater
    * Clients with the exception of any that might be listening on the excluded socket
    * address.
    *
    * @param serverCaVersionNumber the version number of the CA protocol running on the CA Server.
    * @param serverTcpListeningPort the TCP port on which the CA Server is listening.
    * @param serverBeaconId the CA Server's sequential Beacon ID.
    * @param serverAddress the internet address of the CA server.
    * @param excluded the socket address of any client who should be excluded from
    * notification. This parameter can be used to prevent CA repeater clients who
    * originate beacon messages from having those messages reflected back to themselves.

    * @return list of CA Repeater Client Proxies (if any) who failed to transmit the message.
    */
   private List<CARepeaterClientProxy> sendBeaconMessageToRegisteredClients( short serverCaVersionNumber,
                                                                             short serverTcpListeningPort,
                                                                             int serverBeaconId,
                                                                             InetAddress serverAddress,
                                                                             InetSocketAddress excluded )
   {
      Validate.notNull( serverAddress );
      Validate.notNull( excluded );

      // Make a copy of the clients to send the message to
      final List<CARepeaterClientProxy> clientList = new ArrayList<>( clientMap.values() );

      logger.finest( "Attempting to send 'CA_RSRV_IS_UP' (Beacon) message to " + clientList.size() + " CA Repeater clients.");

      final List<CARepeaterClientProxy> failedNotificationList = clientList.stream()
            .filter( client -> ! client.getClientListeningSocketAddress().equals( excluded ) )
            .filter( client -> ! client.sendCaServerBeaconMessage( serverCaVersionNumber, serverTcpListeningPort, serverBeaconId, serverAddress ) )
            .collect( Collectors.toList() );

      logger.finest( "There were " + failedNotificationList.size() + " send failures." );
      return failedNotificationList;
   }

   /**
    * Sequentially sends the supplied datagram to all registered CA Repeater Clients
    * with the exception of any that might be listening on the excluded socket address.
    *
    * The datagram packet must not explicitly set the destination internet address
    * since this is determined by the CA Repeater client listening address which was
    * set during class construction.
    *
    * @param packet the datagram packet to send.
    * @param excluded the socket address of any client who should be excluded from
    * notification. This parameter can be used to prevent CA repeater clients who
    * originate UDP messages from having those messages reflected back to themselves.
    *
    * @return list of CA Repeater Client Proxies (if any) who failed to transmit the message.
    * @throws NullPointerException if the packet argument was null.
    * @throws NullPointerException if the excluded argument was null.
    * @throws IllegalArgumentException if the packet's port was specified.
    * @throws IllegalArgumentException if the packet's internet address was specified.
    */
   private List<CARepeaterClientProxy> sendDatagramToRegisteredClients( DatagramPacket packet, InetSocketAddress excluded )
   {
      Validate.notNull( packet );
      Validate.notNull( excluded );
      Validate.isTrue( packet.getAddress() == null );
      Validate.isTrue( packet.getPort() == -1 );

      // Make a copy of the clients to send the message to
      final List<CARepeaterClientProxy> clientList = new ArrayList<>( clientMap.values() );

      logger.finest( "Attempting to send DATAGRAM to " + clientList.size() + " CA Repeater clients.");

      final List<CARepeaterClientProxy> failedNotificationList = clientList.stream()
         .filter( client -> ! client.getClientListeningSocketAddress().equals( excluded ) )
         .filter( client -> ! client.sendDatagram(packet ) )
         .collect( Collectors.toList() );

      logger.finest( "There were " + failedNotificationList.size() + " send failures." );
      return failedNotificationList;
   }

   /**
    * Sequentially sends a CA_PROTO_VERSION (NOP) message to all registered CA Repeater
    * Clients with the exception of any that might be listening on the excluded socket
    * address.
    *
    * @param excluded the socket address of any client who should be excluded from
    * notification. This parameter can be used to prevent CA repeater clients who
    * originate UDP messages from having those messages reflected back to themselves.
    *
    * @return list of CA Repeater Client Proxies (if any) who failed to transmit the message.
    * @throws NullPointerException if the excluded argument was null.
    * @throws IllegalArgumentException if the datagram packet specified a destination socket address.
    */

   private List<CARepeaterClientProxy> sendVersionMessageToRegisteredClients( InetSocketAddress excluded )
   {
      Validate.notNull( excluded );

      // Make a copy of the clients to send the message to
      final List<CARepeaterClientProxy> clientList = new ArrayList<>( clientMap.values() );

      logger.finest( "Attempting to send 'CA_PROTO_VERSION' message to CA Repeater client list containing " + clientList.size() + " CA Repeater clients.");
      logger.finest( "The client " + excluded + " will be excluded from CA_PROTO_VERSION notifications.");

      // Send the message to everyone on the list with the exception of the
      // message sender (who may also be registered as a CA client).
      final List<CARepeaterClientProxy> failedNotificationList = clientList.stream()
         .filter( client -> ! client.getClientListeningSocketAddress().equals( excluded ) )
         .filter( client -> ! client.sendCaVersionMessage() )
         .collect( Collectors.toList() );

      logger.finest( "There were " + failedNotificationList.size() + " send failures." );
      return failedNotificationList;
   }

   /**
    * Verifies that all the registered clients are still online.
    * Removes clients from the list which are no longer listening.
    */
   private void removeDeadClients()
   {
      logger.log(Level.FINEST, "Removing any CA Repeater Clients which previously registered but which now are dead." );
      logger.log(Level.FINEST, "There are currently " + clientMap.size() + " clients."  );

       // Make a copy of the clients to send the message to
      final List<CARepeaterClientProxy> clientList = new ArrayList<>( clientMap.values() );

      // Test each one and remove it if its "dead".
      clientList.stream()
         .filter( CARepeaterClientProxy::isClientDead )
         .forEach( this::removeClient);

      logger.log(Level.FINEST, "Following dead client cleanup there are now " + clientMap.size() + " clients."  );
   }

   /**
    * Removes the specified CA Repeater Client from the list of registered clients.
    *
    * @param proxy the proxy to remove.
    */
   private void removeClient( CARepeaterClientProxy proxy )
   {
      logger.finest( "Deregistering dead client which used to listen at socket address: " + proxy.getClientListeningSocketAddress() );
      final CARepeaterClientProxy noLongerRequiredProxy = clientMap.remove( proxy.getClientListeningSocketAddress() );

      logger.finest( "Closing dead client communication proxy." );
      noLongerRequiredProxy.close();
   }

/*- Nested Classes -----------------------------------------------------------*/

   public static class CaRepeaterClientManagerException extends Exception
   {
      public CaRepeaterClientManagerException( String message, Exception ex )
      {
         super( message, ex );
      }
   }

}
