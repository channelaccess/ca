/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;

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
      System.setProperty( "CA_DEBUG","1" );
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

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
