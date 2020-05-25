/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.logging.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;


/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class LibraryVersionTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( LibraryVersionTest.class );

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/

   @Test
   void testLibraryVersion()
   {
      final URL testResource = LibraryVersionTest.class.getResource("LibraryVersionTest.class");
      final boolean runningFromjarX = testResource.toString().startsWith( "jar:" );

      final String versionString = LibraryVersion.getAsString();
      if ( runningFromjarX )
      {
         // Note: the version string will have to be updated on every new release.
         // However, new releases should not be published until all the unit tests pass.
         logger.info( "Test is running from JAR. Version is: "  + versionString + "'." );
         assertThat( LibraryVersion.getAsString(), is("1.3.0-SNAPSHOT" ) );
      }
      else
      {
         logger.info( "Test is running from class file without manifest. Version is: '" + versionString + "'." );
         assertThat( LibraryVersion.getAsString(), is("<unknown>") );
      }
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
