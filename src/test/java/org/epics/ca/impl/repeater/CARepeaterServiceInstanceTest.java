/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/

import org.epics.ca.ThreadWatcher;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.*;

import java.util.logging.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.*;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class CARepeaterServiceInstanceTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final int TEST_PORT = 47952;
   private static final Logger logger = LibraryLogManager.getLogger( CARepeaterServiceInstanceTest.class );
   private ThreadWatcher threadWatcher;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/

   @BeforeAll
   static void beforeAll()
   {
      // This is a guard condition. There is no point in checking the behaviour
      // of the CARepeaterStarterTest class if the network stack is not appropriately
      // configured for channel access.
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
      assertThat( CARepeaterServiceManager.isRepeaterRunning( TEST_PORT ), is( false ) );
   }

   @AfterEach
   void afterEach()
   {
      assertThat( CARepeaterServiceManager.verifyRepeaterStops( TEST_PORT ), is( true ) );
      assertDoesNotThrow( () -> threadWatcher.verify(), "Thread leak detected !" );
   }

   @Test
   void doNothing() {}

   @Test
   void testActivateAndShutdown()
   {
      logger.info("Activating a new CA Repeater instance..");
      final CARepeaterServiceInstance instance = new CARepeaterServiceInstance( TEST_PORT );
      instance.activate();
      logger.info("OK");
      logger.info("Verifying that the CA Repeater process is reported as being alive...");
      assertThat( instance.isProcessAlive(), is(true ) );
      logger.info("OK");
      logger.info("Verifying that the CA Repeater starts running...");
      assertThat( CARepeaterServiceManager.verifyRepeaterStarts( TEST_PORT ), is( true ) );
      logger.info("OK");
      logger.info("Shutting down the CA Repeater process...");
      instance.shutdown();
      logger.info("OK");
      logger.info("Verifying that the CA Repeater process is no longer reported as being alive...");
      assertThat( instance.isProcessAlive(), is(false ) );
      logger.info("OK");
      logger.info("Verifying that the CA Repeater shuts down...");
      assertThat( CARepeaterServiceManager.verifyRepeaterStops( TEST_PORT ), is( true ) );
      logger.info("OK");
   }

   @Test
   void testShutdownWithoutPreviousActivateThrowsException()
   {
      final CARepeaterServiceInstance instance = new CARepeaterServiceInstance( TEST_PORT );
      final Throwable throwable = assertThrows( IllegalStateException.class, instance::shutdown );
      assertThat( throwable.getMessage(), is( "This service instance was not previously activated." ) );
   }

   @Test
   void testActivateTwiceThrowsException()
   {
      final CARepeaterServiceInstance instance = new CARepeaterServiceInstance( TEST_PORT );
      instance.activate();
      assertThat( CARepeaterServiceManager.verifyRepeaterStarts( TEST_PORT ), is( true ) );
      final Throwable throwable = assertThrows( IllegalStateException.class, instance::activate );
      assertThat( throwable.getMessage(), is( "This service instance was already activated." ) );
      instance.shutdown();
      assertThat( CARepeaterServiceManager.verifyRepeaterStops( TEST_PORT ), is( true ) );
   }

   @Test
   void testShutdownTwiceThrowsException()
   {
      final CARepeaterServiceInstance instance = new CARepeaterServiceInstance( TEST_PORT );
      instance.activate();
      assertThat( CARepeaterServiceManager.verifyRepeaterStarts( TEST_PORT ), is( true ) );
      instance.shutdown();
      assertThat( CARepeaterServiceManager.verifyRepeaterStops( TEST_PORT ), is( true ) );
      final Throwable throwable = assertThrows( IllegalStateException.class, instance::shutdown );
      assertThat( throwable.getMessage(), is( "This service instance was already shut down." ) );
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
