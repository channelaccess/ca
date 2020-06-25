/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.examples;

/*- Imported packages --------------------------------------------------------*/

import net.jcip.annotations.ThreadSafe;
import org.epics.ca.EpicsChannelAccessTestServer;
import org.epics.ca.impl.JavaProcessManager;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;


/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

@ThreadSafe
public class ExampleTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( ExampleTest.class );

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/

   @BeforeEach
   void beforeEach()
   {
      EpicsChannelAccessTestServer.start();
   }

   @AfterEach
   void afterEach()
   {
      EpicsChannelAccessTestServer.shutdown();
   }

   @Test
   void runExample() throws InterruptedException
   {
      final Properties properties = new Properties();
      final JavaProcessManager exampleRunner = new JavaProcessManager( Example.class, properties, new String[] {} );
      final boolean startedOk = exampleRunner.start( true );
      assertThat( startedOk, is( true ) );
      final boolean completedOK = exampleRunner.waitFor(10, TimeUnit.SECONDS );

      // If a timeout occurs ensure that the example runner gets shutdown.
      if (! completedOK )
      {
         exampleRunner.shutdown();
      }
      assertThat( completedOK, is( true ) );
      assertThat( exampleRunner.getExitValue(), is( 0 ) );
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
