/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.repeater;


/*- Imported packages --------------------------------------------------------*/

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

class CARepeaterStarterTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private final int testPort = 5065;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/

   @BeforeEach
   void beforeEach()
   {
      assertThat( "Repeater is NOT running on port " + testPort + ".", CARepeaterStarter.isRepeaterRunning( testPort ), is (false ) );
   }

   @Test
   void nothingShouldBeRunningOnRepeaterPort()
   {
      // checked in beforeEach
   }

   @Test
   void testStartRepeaterAsSeparateJvmProcess() throws Throwable
   {
      CARepeaterStarter.spawnRepeaterIfNotAlreadyRunning(testPort );
      Thread.sleep( 5000 );
      assertThat( CARepeaterStarter.isRepeaterRunning( testPort ), is( true ) );
   }

   @Test
   void testStartRepeaterInCurrentJvmProcess() throws Throwable
   {
      CARepeater repeater = new CARepeater( testPort );
      repeater.start();
      Thread.sleep( 1000 );
      assertThat( CARepeaterStarter.isRepeaterRunning( testPort ), is( true ) );
      repeater.shutdown();
      assertThat( CARepeaterStarter.isRepeaterRunning( testPort ), is( false ) );
   }


/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
