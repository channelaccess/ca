/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/

import org.epics.ca.ThreadWatcher;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

class NetworkUtilitiesTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( NetworkUtilitiesTest.class );
   private ThreadWatcher threadWatcher;
   
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

   @BeforeEach
   void beforeEach()
   {
      threadWatcher = ThreadWatcher.start();
   }

   @AfterEach
   void afterEach()
   {
      threadWatcher.verify();
   }

   @Test
   void testIsThisMyIp_suppliedWithNullThrowsNullPointerException()
   {
      assertThrows( NullPointerException.class, () -> NetworkUtilities.isThisMyIpAddress( null ) );
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
         logger.info( "Testing with IP: " + addr );
         assertThat( NetworkUtilities.isThisMyIpAddress( addr ), is( true ) );
      } );
   }

   @Test
   void testIsThisMyIp_withAllLocalHostAddresses() throws UnknownHostException
   {
      final InetAddress[] localAddresses = InetAddress.getAllByName( "localhost" );
      Arrays.asList( localAddresses ).forEach( (addr) -> {
         logger.info( "Testing with IP: " + addr );
         assertThat( NetworkUtilities.isThisMyIpAddress( addr ), is( true ) );
      } );
   }

   @Test
   void testGetLocalNetworkInterfaceAddresses()
   {
      logger.info( "The following network addresses have been detected on the local network interface:" );
      final List<Inet4Address> list = NetworkUtilities.getLocalNetworkInterfaceAddresses();
      list.forEach( (addr) -> logger.info("- " + addr.toString() ));
   }

   @Test
   void testGetLocalBroadcastAddresses()
   {
      logger.info( "The following broadcast addresses have been detected on the local network interface:" );
      final List<Inet4Address> list = NetworkUtilities.getLocalBroadcastAddresses();
      list.forEach( (addr) -> logger.info("- " + addr.toString() ));
   }

   @Test
   void testIsVpnActive()
   {
      NetworkUtilities.isVpnActive();
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
