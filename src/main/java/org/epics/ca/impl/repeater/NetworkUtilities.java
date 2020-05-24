/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/
/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.epics.ca.util.logging.LibraryLogManager;

import java.net.*;
import java.util.*;
import java.util.logging.Logger;


/**
 * Provides general-purpose, network-related helper functions for the CA Repeater.
 */
public class NetworkUtilities
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( NetworkUtilities.class );

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/

   public static boolean verifyTargetPlatformNetworkStackIsChannelAccessCompatible()
   {
      if ( verifyIpv4NetworkStackPreferred() && verifyIpv6NetworkStackNotPreferred() )
      {
         logger.info( "The network stack for this JVM is configured OK for channel access." );
         return true;
      }
      else
      {
         logger.warning( "JVM Configuration Error: the network stack for this JVM is NOT configured appropriately for channel access." );
         if ( ! NetworkUtilities.verifyIpv4NetworkStackPreferred() )
         {
            logger.warning( "Please ensure that the system property 'java.net.preferIPv4Stack' is set to true." );
         }
         if ( ! NetworkUtilities.verifyIpv6NetworkStackNotPreferred() )
         {
            logger.warning( "Please ensure that the system property 'java.net.preferIPv6Stack' is set to false." );
         }
         return false;
      }
   }

/*- Package-level methods ----------------------------------------------------*/

   /**
    * Returns an indication of whether a VPN is currently active on the local network.
    *
    * @return the indicator
    */
   static boolean isVpnActive()
   {
      final List<Inet4Address> addrList = new ArrayList<>();
      try
      {
         for ( Enumeration<NetworkInterface> eni = NetworkInterface.getNetworkInterfaces(); eni.hasMoreElements(); )
         {
            final NetworkInterface nif = eni.nextElement();
            // VPN's are typically implemented using Point-to-Point links.
            if ( nif.isUp() && nif.isPointToPoint() )
            {
               logger.info( "An active VPN has been detected." );
               return true;
            }
         }
      }
      catch ( SocketException e )
      {
         logger.warning( "Exception when querying local interfaces for registered IP4 addresses." );
      }
      logger.info( "An active VPN has NOT been detected." );
      return false;
   }

   /**
    * Returns a list of any IPV4 internet addresses that have been assigned to the local interfaces.
    *
    * Credit: the algorithm below is a modified form of the version described here:
    * https://stackoverflow.com/questions/494465/how-to-enumerate-ip-addresses-of-all-enabled-nic-cards-from-java
    *
    * @return the list, empty if the query fails or if there are none available.
    */
   static List<Inet4Address> getLocalNetworkInterfaceAddresses()
   {
      final List<Inet4Address> addrList = new ArrayList<>();
      try
      {
         for( Enumeration<NetworkInterface> eni = NetworkInterface.getNetworkInterfaces(); eni.hasMoreElements(); )
         {
            final NetworkInterface nif = eni.nextElement();

            logger.finest( "name: " + nif.getName() );
            logger.finest( "- isUp: " + nif.isUp() );
            logger.finest( "- isPointToPoint: " + nif.isPointToPoint() );
            logger.finest( "- isVirtual: " + nif.isVirtual() );
            logger.finest( "- supportsMulticast: " + nif.supportsMulticast() );
            logger.finest( "- isLoopback: " + nif.isLoopback() );

            logger.finest( "- if addresses: " );
            for ( InterfaceAddress ifAddr : nif.getInterfaceAddresses() )
            {
               final InetAddress inetAddress = ifAddr.getAddress();
               logger.finest( "  - addr: " + inetAddress);
               logger.finest( "  - subnetMask: " + ifAddr.getNetworkPrefixLength() );
               logger.finest( "  - broadcast: " + ifAddr.getBroadcast() );

               if ( inetAddress instanceof Inet4Address )
               {
                  Inet4Address inet4Address = (Inet4Address) inetAddress;
                  addrList.add( inet4Address );
               }
            }
            logger.finest( "" );
         }
      }
      catch ( SocketException e )
      {
         logger.warning( "Exception when querying local interfaces for registered IP4 addresses." );
      }
      return addrList;
   }

   /**
    * Returns a list of the broadcast enabled addresses associated with the local
    * interfaces.
    *
    * Credit: the algorithm below is a modified form of the version described here:
    * https://stackoverflow.com/questions/494465/how-to-enumerate-ip-addresses-of-all-enabled-nic-cards-from-java
    *
    * @return the list, empty if the query fails or if there are none available.
    */
   static List<Inet4Address> getLocalBroadcastAddresses()
   {
      final List<Inet4Address> addrList = new ArrayList<>();
      try
      {
         for( Enumeration<NetworkInterface> eni = NetworkInterface.getNetworkInterfaces(); eni.hasMoreElements(); )
         {
            final NetworkInterface nif = eni.nextElement();
            for ( InterfaceAddress ifAddr : nif.getInterfaceAddresses() )
            {
               final InetAddress inetAddress = ifAddr.getBroadcast();
               if ( inetAddress != null   )
               {
                  addrList.add( (Inet4Address) inetAddress );
               }
            }
         }
      }
      catch ( SocketException e )
      {
         logger.warning( "Exception when querying local interfaces for registered IP4 addresses." );
      }
      return addrList;
   }

   /**
    * Returns an indication of whether the supplied IP address is valid for
    * the local interface.
    *
    * @implNote
    * The wildcard address and loopback address(es) are always available.
    * Additional local addresses are tested by interrogating the Java
    * network stack.
    *
    * @param addr the address to check.
    * @return the result.
    * @throws NullPointerException if the address argument was null.
    */
   static boolean isThisMyIpAddress( InetAddress addr )
   {
      Validate.notNull( addr );

      // Check if the address is the wildcard address (0.0.0.0) or the loopback address (127.0.0.1)
      if ( addr.isAnyLocalAddress() || addr.isLoopbackAddress() )
      {
         return true;
      }

      // Check if the address is defined on any interfaces on the local host.
      try
      {
         return NetworkInterface.getByInetAddress( addr ) != null;
      }
      catch( SocketException e )
      {
         return false;
      }
   }

/*- Private methods ----------------------------------------------------------*/

   private static boolean verifyIpv4NetworkStackPreferred()
   {
      final String propertyName = "java.net.preferIPv4Stack";
      logger.finest( "The property: '" + propertyName + "' is set to: '" + System.getProperty( propertyName ) + "'." );

      // Note: according to the documentation below the default JVM network stack setting for 'java.net.preferIPv4Stack' is false.
      // So this is the value that is used below if the property is not explicitly defined.
      // https://docs.oracle.com/javase/7/docs/api/java/net/doc-files/net-properties.html
      final boolean result =  System.getProperty( propertyName, "false" ).equals( "true" );
      logger.finest( "verifyIpv4NetworkStackPreferred is set to: '" + result + "'." );
      return result;
   }

   private static boolean verifyIpv6NetworkStackNotPreferred()
   {
      final String propertyName = "java.net.preferIPv6Stack";
      logger.finest( "The property: '" + propertyName + "' is set to: '" + System.getProperty( propertyName ) + "'." );

      // Note: according to the documentation below the default JVM network stack setting for 'java.net.preferIPv6Stack' is false.
      // So this is the value that is used below if the property is not explicitly defined.
      // https://docs.oracle.com/javase/7/docs/api/java/net/doc-files/net-properties.html
      final boolean result =  System.getProperty( propertyName, "false" ).equals( "false" );
      logger.finest( "verifyIpv6NetworkStackNotPreferred is set to: '" + result  + "'." );
      return result;
   }

/*- Nested Classes -----------------------------------------------------------*/

}
