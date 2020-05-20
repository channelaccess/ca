/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/
/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

import org.epics.ca.util.logging.LibraryLogManager;

import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
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
         System.out.println( "The network stack for this JVM is configured OK for channel access." );
         return true;
      }
      else
      {
         System.out.println( "JVM Configuration Error: the network stack for this JVM is NOT configured appropriately for channel access.");
         if ( ! NetworkUtilities.verifyIpv4NetworkStackPreferred() )
         {
            System.out.println( "Please ensure that the system property 'java.net.preferIPv4Stack' is set to true.");
         }
         if ( ! NetworkUtilities.verifyIpv6NetworkStackNotPreferred() )
         {
            System.out.println( "Please ensure that the system property 'java.net.preferIPv6Stack' is set to false.");
         }
         return false;
      }
   }

/*- Package-level methods ----------------------------------------------------*/

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
            final NetworkInterface ifc = eni.nextElement();
            if( ifc.isUp() )
            {
               for (Enumeration<InetAddress> ena = ifc.getInetAddresses(); ena.hasMoreElements(); )
               {
                  final InetAddress addr = ena.nextElement();
                  if ( addr instanceof Inet4Address)
                  {
                     addrList.add( (Inet4Address) addr );
                  }
               }
            }
         }
      }
      catch ( SocketException e )
      {
         logger.log(Level.WARNING, "Exception when querying local interfaces for registered IP4 addresses." );
      }
      return addrList;
   }

   /**
    * Returns an indication of whether the supplied IP address is
    * valid for the local interface.
    *
    * @param addr the address to check.
    * @return the result.
    */
   static boolean isThisMyIpAddress( InetAddress addr )
   {
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
