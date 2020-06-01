/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import org.epics.ca.impl.repeater.CARepeaterStarter;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.logging.Level;
import java.util.logging.Logger;


/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class EpicsChannelAccessTestServerTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( EpicsChannelAccessTestServerTest.class );

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/

   @BeforeEach
   void beforeEach()
   {
      System.setProperty( "CA_DEBUG", "true" );
   }

   @RepeatedTest( 10 )
   void startThenShutdown() throws InterruptedException
   {
      EpicsChannelAccessTestServer server = EpicsChannelAccessTestServer.start();
      Thread.sleep( 100 );
      server.shutdown();
   }

   @RepeatedTest( 1 )
   void startTwiceThenShutdown()
   {
      EpicsChannelAccessTestServer server1 = EpicsChannelAccessTestServer.start();
      EpicsChannelAccessTestServer server2 = EpicsChannelAccessTestServer.start();
      server1.shutdown();
      server2.shutdown();
   }

   @RepeatedTest( 1 )
   void startOnceThenShutdownTwice()
   {
      EpicsChannelAccessTestServer server = EpicsChannelAccessTestServer.start();
      server.shutdown();
      server.shutdown();
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

      final EpicsChannelAccessTestServer server;
      try ( Context context = new Context() )
      {
         // Allow one second for the CA library to start a repeater and to register with it.
         Thread.sleep( 1000 );
         
         // Now start the test server which should emit a number of beacon messages
         // to say that it is alive.
         server = EpicsChannelAccessTestServer.start();
         Thread.sleep( 10_000 );
      }
      server.shutdown();
      CARepeaterStarter.shutdownLastStartedRepeater();
   }


   /*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
