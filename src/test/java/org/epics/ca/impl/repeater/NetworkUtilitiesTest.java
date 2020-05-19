/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

class NetworkUtilitiesTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/
/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/

   @BeforeAll
   static void beforeAll()
   {
      // This is a guard condition. There is no point in checking the behaviour
      // of the NetworkUtilities class if the network stack is not appropriately
      // configured for channel access.
      assertThat( NetworkUtilities.verifyTargetPlatformNetworkStackIsChannelAccessCompatible(), is( true ) );
   }

   @Test
   void testIsThisMyIp_withMiscellaneousAddresses() throws UnknownHostException
   {
      assertThat( NetworkUtilities.isThisMyIpAddress( InetAddress.getByName( "bbc.co.uk" ) ), is( false ) );
      assertThat( NetworkUtilities.isThisMyIpAddress( InetAddress.getLoopbackAddress()), is( true ) );
      assertThat( NetworkUtilities.isThisMyIpAddress( InetAddress.getLocalHost()), is( true ) );
      assertThat( NetworkUtilities.isThisMyIpAddress( InetAddress.getByName( "127.0.0.1") ), is( true ) );
      assertThat( NetworkUtilities.isThisMyIpAddress( InetAddress.getByName( "127.0.0.8") ), is( true ) );
      assertThat( NetworkUtilities.isThisMyIpAddress( InetAddress.getByName( "0.0.0.0") ), is( true ) );
      assertThat( NetworkUtilities.isThisMyIpAddress( InetAddress.getByName( "192.168.0.1") ), is( false ) );
   }

   @Test
   void testIsThisMyIp_withAllLocalHostNameAddresses() throws UnknownHostException
   {
      final String hostName = InetAddress.getLocalHost().getHostName();
      final InetAddress[] localAddresses = InetAddress.getAllByName( hostName );
      Arrays.asList( localAddresses ).forEach( (addr) -> {
         System.out.println( "Testing with IP: " + addr );
         assertThat( NetworkUtilities.isThisMyIpAddress( addr ), is( true ) );
      } );
   }

   @Test
   void testIsThisMyIp_withAllLocalHostAddresses() throws UnknownHostException
   {
      final InetAddress[] localAddresses = InetAddress.getAllByName( "localhost" );
      Arrays.asList( localAddresses ).forEach( (addr) -> {
         System.out.println( "Testing with IP: " + addr );
         assertThat( NetworkUtilities.isThisMyIpAddress( addr ), is( true ) );
      } );
   }


/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
