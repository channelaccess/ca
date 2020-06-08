/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import org.epics.ca.impl.repeater.NetworkUtilities;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;


/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class EpicsChannelAccessTestServerTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( EpicsChannelAccessTestServerTest.class );
   private ThreadWatcher threadWatcher;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/

   @BeforeEach
   void beforeEach()
   {
      threadWatcher = ThreadWatcher.start();

      // Currently (2020-05-22) this test is not supported when the VPN connection is active on the local machine.
      if ( NetworkUtilities.isVpnActive() )
      {
         fail( "This test is not supported when a VPN connection is active on the local network interface." );
      }
   }

   @AfterEach
   void afterEach()
   {
      threadWatcher.verify();
   }

   @RepeatedTest( 10 )
   void startThenShutdownRepeatedly() throws InterruptedException
   {
      EpicsChannelAccessTestServer.start();
      Thread.sleep( 100 );
      EpicsChannelAccessTestServer.shutdown();
   }

   @Test
   void startWhenAlreadyStarted_throwsIllegalStateException()
   {
      EpicsChannelAccessTestServer.start();
      RuntimeException ex = assertThrows( IllegalStateException.class, EpicsChannelAccessTestServer::start );
      assertThat( ex.getMessage(), is( "The EpicsChanneAccessTestServer was not shutdown." ) );
      EpicsChannelAccessTestServer.shutdown();
   }

   @Test
   void shutdownWhenAlreadyShutdown_throwsIllegalStateException()
   {
      RuntimeException ex = assertThrows( IllegalStateException.class, EpicsChannelAccessTestServer::shutdown );
      assertThat( ex.getMessage(), is( "The EpicsChanneAccessTestServer was not started." ) );
   }

   @Test
   void testRepeaterBeaconForwarding() throws InterruptedException
   {
      // The logging is set to verbose so that the log messages can be examined
      // by eye to see whether the repeater successfully registered. There should
      // be messages in the log saying that one client was registered. Then there
      // should be multiple messages as the test server comes up and sends beacon
      // messages which the repeater forwards to the CA client library.
      System.setProperty( "CA_LIBRARY_LOG_LEVEL", Level.ALL.toString() );
      System.setProperty( "CA_REPEATER_LOG_LEVEL", Level.ALL.toString() );
      System.setProperty( "CA_REPEATER_OUTPUT_CAPTURE", "true" );
      System.setProperty( "CA_REPEATER_SHUTDOWN_ON_CONTEXT_CLOSE", "true");

      // Create a new Context. This will cause a CA Repeater instance to be spawned.
      // If the system property "CA_REPEATER_SHUTDOWN_ON_CONTEXT_CLOSE" is asserted
      // then the spawned CA Repeater will be automatically killed when the context
      // goes out of scope.
      try ( Context context = new Context() )
      {
         // Allow a couple of seconds for the CA library to start a repeater and
         // to register with it.
         Thread.sleep( 1000 );
         
         // Now start the Epics Channel Access Test Server which should emit a
         // number of beacon messages to say that it is alive. If all is well
         // the log messages should show that these messages will get forwarded
         // to the Context.
         EpicsChannelAccessTestServer.start();
         Thread.sleep( 10_000 );
      }

      EpicsChannelAccessTestServer.shutdown();
   }


/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
