/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.repeater;


/*- Imported packages --------------------------------------------------------*/

import org.epics.ca.ThreadWatcher;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.fail;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class CARepeaterServiceInstanceTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( CARepeaterServiceInstanceTest.class );
   private ThreadWatcher threadWatcher;
   
   private static final int testPort = 5065;
   private static final Level caRepeaterDebugLevel = Level.ALL;
   private static final boolean caRepeaterOutputCaptureEnable = false;

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
      
      final InetSocketAddress wildcardAddress = new InetSocketAddress( 5065 );
      logger.info( "Checking Test Precondition: CA Repeater should NOT be running... on: " + wildcardAddress );

      if ( CARepeaterServiceManager.isRepeaterRunning( testPort ) )
      {
         logger.info( "Test Precondition FAILED: the CA Repeater was detected to be already running." );
         fail();
      }
      else
      {
         logger.info( "Test Precondition OK: the CA Repeater was not detected to be already running." );
      }
      logger.info( "Starting Test." );
   }

   @AfterEach
   void afterEach()
   {
      threadWatcher.verify();
   }


   @Test
   void testStartRepeater() throws Throwable
   {
      logger.info("Starting CA Repeater in separate process.");

      final CARepeaterServiceInstance instance = new CARepeaterServiceInstance( testPort );
      instance.start();

      logger.info("The CA Repeater process was created.");
      logger.info("Verifying that the CA Repeater process is reported as being alive...");
      try
      {
         assertThat( instance.isProcessAlive(), is(true ) );
         logger.info("OK");
         logger.info("Waiting a moment to allow the spawned process time to reserve the listening port...");
         Thread.sleep(1500);
         logger.info("Waiting completed.");
         logger.info("Verifying that the CA Repeater is detected as running...");
         assertThat( CARepeaterServiceManager.isRepeaterRunning( testPort), is( true));
         logger.info("OK");
         logger.info("Shutting down the CA Repeater process...");
         instance.shutdown();

         logger.info("Verifying that the CA Repeater process is no longer reported as being alive...");
         assertThat( instance.isProcessAlive(), is(false));
         logger.info("OK");
         logger.info("Waiting a moment to allow the OS to release the listening port...");
         Thread.sleep(1500);
         logger.info("Verifying that the CA Repeater is no longer detected as running...");
         assertThat( CARepeaterServiceManager.isRepeaterRunning( testPort ), is( false));
         logger.info("OK");
      }
      finally
      {
         instance.shutdown();
      }
   }

   @Test
   void testIsRepeaterRunning_startRepeaterInCurrentJvmProcess() throws Throwable
   {
      final CARepeater repeater = new CARepeater( testPort );
      repeater.start();
      Thread.sleep( 1500 );
      assertThat( CARepeaterServiceManager.isRepeaterRunning( testPort ), is( true ) );
      repeater.shutdown();
      Thread.sleep( 1500 );
      assertThat( CARepeaterServiceManager.isRepeaterRunning( testPort ), is( false ) );
   }


   /*- Private methods ----------------------------------------------------------*/
   
   private static Stream<Arguments> getArgumentsForTestIsRepeaterRunning_detectsSocketReservedInDifferentJvmOnDifferentLocalAddresses()
   {
      final List<Inet4Address> localAddressList = NetworkUtilities.getLocalNetworkInterfaceAddresses();
      final List<String> localAddresses = localAddressList.stream().map( Inet4Address::getHostAddress).collect(Collectors.toList());
      return localAddresses.stream().map( Arguments::of );
   }


/*- Nested Classes -----------------------------------------------------------*/

}
