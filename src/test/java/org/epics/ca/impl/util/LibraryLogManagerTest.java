/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.util;


/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.time.StopWatch;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

class LibraryLogManagerTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/

   @Test
   void testGetLoggerWithDebugEnabled()
   {
      System.setProperty( "CA_DEBUG", "1" );
      final Logger debugLogger = LibraryLogManager.getLogger( LibraryLogManagerTest.class );
      debugLogger.finest( "** This log message is at level FINEST **" );
      debugLogger.finer( "** This log message is at level FINER **" );
      debugLogger.fine( "** This log message is at level FINE **" );
      debugLogger.config( "** This log message is at level CONFIG **" );
      debugLogger.info( "** This log message is at level INFO **" );
      debugLogger.warning( "** This log message is at level WARNING **" );
      debugLogger.severe( "** This log message is at level SEVERE **" );
   }

   @Test
   void testGetLoggerWithDebugDisabled()
   {
      System.setProperty( "CA_DEBUG", "0" );
      final Logger noDebugLogger = LibraryLogManager.getLogger( LibraryLogManagerTest.class );
      noDebugLogger.finest( "** This log message is at level FINEST **" );
      noDebugLogger.finer( "** This log message is at level FINER **" );
      noDebugLogger.fine( "** This log message is at level FINE **" );
      noDebugLogger.config( "** This log message is at level CONFIG **" );
      noDebugLogger.info( "** This log message is at level INFO **" );
      noDebugLogger.warning( "** This log message is at level WARNING **" );
      noDebugLogger.severe( "** This log message is at level SEVERE **" );
   }

   @Test
   void testGetLoggerWithDefaultDebugLevel()
   {
      final Logger defaultLogger = LibraryLogManager.getLogger( LibraryLogManagerTest.class );
      defaultLogger.finest( "** This log message is at level FINEST **" );
      defaultLogger.finer( "** This log message is at level FINER **" );
      defaultLogger.fine( "** This log message is at level FINE **" );
      defaultLogger.config( "** This log message is at level CONFIG **" );
      defaultLogger.info( "** This log message is at level INFO **" );
      defaultLogger.warning( "** This log message is at level WARNING **" );
      defaultLogger.severe( "** This log message is at level SEVERE **" );
   }

   @Test
   void testGetLoggerWithExceptions()
   {
      final Logger exceptionLogger = LibraryLogManager.getLogger( LibraryLogManagerTest.class );

      final RuntimeException exception = new RuntimeException( "This is a test exception." );
      exceptionLogger.log( Level.FINEST, "** This exception log message is at level FINEST **", exception );
      exceptionLogger.log( Level.FINER, "** This exception log message is at level FINER **", exception  );
      exceptionLogger.log( Level.FINE, "** This exception log message is at level FINE **", exception  );
      exceptionLogger.log( Level.CONFIG, "** This exception log message is at level CONFIG **", exception  );
      exceptionLogger.log( Level.INFO, "** This exception log message is at level INFO **", exception  );
      exceptionLogger.log( Level.WARNING, "** This exception log message is at level WARNING **", exception  );
      exceptionLogger.log( Level.SEVERE, "** This exception log message is at level SEVERE **", exception  );
   }

   @RepeatedTest( 3 )
   void testPerformanceWithDebugDisabled()
   {
      System.setProperty( "CA_DEBUG", "0" );
      final Logger noDebugLogger = LibraryLogManager.getLogger( LibraryLogManagerTest.class );

      final StopWatch stopWatch = StopWatch.createStarted();
      for ( int i = 0; i < 1_000_000; i++  )
      {
         noDebugLogger.finest("** This log message is at level FINEST **");
      }
      final long elapsedTimeInMicroseconds = stopWatch.getTime( TimeUnit.MICROSECONDS );
      noDebugLogger.info( "Sending 1,000,000 messages to disabled debug took " + elapsedTimeInMicroseconds + "us" );
   }

   @RepeatedTest( 3 )
   void testPerformanceWithDebugEnabled()
   {
      System.setProperty( "CA_DEBUG", "1" );
      final Logger noDebugLogger = LibraryLogManager.getLogger( LibraryLogManagerTest.class );

      final StopWatch stopWatch = StopWatch.createStarted();
      for ( int i = 0; i < 1_000; i++  )
      {
         noDebugLogger.finest("** This log message is at level FINEST **");
      }
      final long elapsedTimeInMillis = stopWatch.getTime( TimeUnit.MILLISECONDS );
      noDebugLogger.info( "Sending 1,000,000 messages to disabled debug took " + elapsedTimeInMillis + "ms" );
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/


}
