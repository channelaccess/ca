/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/

import org.epics.ca.ThreadWatcher;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class CARepeaterServiceManagerTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final int TEST_PORT = 45783;
   private static final Logger logger = LibraryLogManager.getLogger( CARepeaterServiceManagerTest.class );
   private ThreadWatcher threadWatcher;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/

   @BeforeAll
   static void beforeAll()
   {
      // This is a guard condition. There is no point in checking the behaviour
      // of this class if the network stack is not appropriately configured for
      // channel access.
      assertThat( NetworkUtilities.verifyTargetPlatformNetworkStackIsChannelAccessCompatible(), is( true ) );

      // Currently (2020-05-22) this test is not supported when the VPN connection is active on the local machine.
      if ( NetworkUtilities.isVpnActive() )
      {
         fail( "This test is not supported when a VPN connection is active on the local network interface." );
      }
   }

   @BeforeEach
   void beforeEach()
   {
      threadWatcher = ThreadWatcher.start();
      assertThat( CARepeaterStatusChecker.isRepeaterRunning( TEST_PORT ), is( false ) );
   }

   @AfterEach
   void afterEach()
   {
      assertThat( CARepeaterStatusChecker.verifyRepeaterStops( TEST_PORT ), is( true ) );
      assertDoesNotThrow( () -> threadWatcher.verify(), "Thread leak detected !" );
   }

   @Test
   void testCARepeaterServiceManager_requestAndCancelService()
   {
      final CARepeaterServiceManager caRepeaterServiceManager1 = new CARepeaterServiceManager();
      caRepeaterServiceManager1.requestServiceOnPort( 2 );
      assertThat( caRepeaterServiceManager1.getServiceInstances(), is( 1 ) );
      assertThat( CARepeaterStatusChecker.verifyRepeaterStarts( 2 ), is( true ) );

      caRepeaterServiceManager1.cancelServiceRequestOnPort( 2 );
      assertThat( caRepeaterServiceManager1.getServiceInstances(), is( 0 ) );
      assertThat( CARepeaterStatusChecker.verifyRepeaterStops( 2 ), is( true ) );
   }

   @Test
   void testCARepeaterServiceManager_requestAndCancelServiceTwiceOnSamePort()
   {
      final CARepeaterServiceManager caRepeaterServiceManager1 = new CARepeaterServiceManager();
      assertThat( caRepeaterServiceManager1.getServiceInstances(), is( 0 ) );

      caRepeaterServiceManager1.requestServiceOnPort( 2 );
      assertThat( caRepeaterServiceManager1.getServiceInstances(), is( 1 ) );
      assertThat( CARepeaterStatusChecker.verifyRepeaterStarts( 2 ), is( true ) );

      caRepeaterServiceManager1.requestServiceOnPort( 2 );
      assertThat( caRepeaterServiceManager1.getServiceInstances(), is( 1 ) );
      assertThat( CARepeaterStatusChecker.isRepeaterRunning( 2 ), is( true ) );

      caRepeaterServiceManager1.cancelServiceRequestOnPort( 2 );
      assertThat( caRepeaterServiceManager1.getServiceInstances(), is( 1 ) );
      assertThat( CARepeaterStatusChecker.isRepeaterRunning( 2 ), is( true ) );

      caRepeaterServiceManager1.cancelServiceRequestOnPort( 2 );
      assertThat( caRepeaterServiceManager1.getServiceInstances(), is( 0 ) );
      assertThat( CARepeaterStatusChecker.verifyRepeaterStops( 2 ), is( true ) );
   }

   @Test
   void testCARepeaterServiceManager_requestAndCancelServiceOnDifferentPorts()
   {
      final CARepeaterServiceManager caRepeaterServiceManager1 = new CARepeaterServiceManager();
      assertThat( caRepeaterServiceManager1.getServiceInstances(), is( 0 ) );

      caRepeaterServiceManager1.requestServiceOnPort( 22 );
      assertThat( caRepeaterServiceManager1.getServiceInstances(), is( 1 ) );
      assertThat( CARepeaterStatusChecker.verifyRepeaterStarts( 22 ), is( true ) );

      caRepeaterServiceManager1.requestServiceOnPort( 33 );
      assertThat( caRepeaterServiceManager1.getServiceInstances(), is( 2 ) );
      assertThat( CARepeaterStatusChecker.isRepeaterRunning( 22 ), is( true ) );
      assertThat( CARepeaterStatusChecker.verifyRepeaterStarts( 33 ), is( true ) );

      caRepeaterServiceManager1.cancelServiceRequestOnPort( 999 );
      assertThat( caRepeaterServiceManager1.getServiceInstances(), is( 2 ) );
      assertThat( CARepeaterStatusChecker.isRepeaterRunning( 22 ), is( true ) );
      assertThat( CARepeaterStatusChecker.isRepeaterRunning( 33 ), is( true ) );

      caRepeaterServiceManager1.cancelServiceRequestOnPort( 22 );
      assertThat( caRepeaterServiceManager1.getServiceInstances(), is( 1 ) );
      assertThat( CARepeaterStatusChecker.verifyRepeaterStops( 22 ), is( true ) );
      assertThat( CARepeaterStatusChecker.isRepeaterRunning( 33 ), is( true ) );

      caRepeaterServiceManager1.cancelServiceRequestOnPort( 33 );
      assertThat( caRepeaterServiceManager1.getServiceInstances(), is( 0 ) );
      assertThat( CARepeaterStatusChecker.verifyRepeaterStops( 33 ), is( true ) );
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
