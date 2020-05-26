/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.util.logging;


/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.time.StopWatch;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Date;
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
   void testGetLogger_withDebugEnabled()
   {
      System.setProperty( "CA_DEBUG", "1" );
      final Logger debugLogger = LibraryLogManager.getLogger(LibraryLogManagerTest.class );
      debugLogger.finest( "** This log message is at level FINEST **" );
      debugLogger.finer( "** This log message is at level FINER **" );
      debugLogger.fine( "** This log message is at level FINE **" );
      debugLogger.config( "** This log message is at level CONFIG **" );
      debugLogger.info( "** This log message is at level INFO **" );
      debugLogger.warning( "** This log message is at level WARNING **" );
      debugLogger.severe( "** This log message is at level SEVERE **" );
      System.clearProperty( "CA_DEBUG" );
      LibraryLogManager.disposeLogger( debugLogger );
   }

   @Test
   void testGetLogger_withDebugDisabled()
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
      LibraryLogManager.disposeLogger( noDebugLogger );
   }

   @Test
   void testGetLogger_withDefaultDebugLevel()
   {
      final Logger defaultLogger = LibraryLogManager.getLogger( LibraryLogManagerTest.class );
      defaultLogger.finest( "** This log message is at level FINEST **" );
      defaultLogger.finer( "** This log message is at level FINER **" );
      defaultLogger.fine( "** This log message is at level FINE **" );
      defaultLogger.config( "** This log message is at level CONFIG **" );
      defaultLogger.info( "** This log message is at level INFO **" );
      defaultLogger.warning( "** This log message is at level WARNING **" );
      defaultLogger.severe( "** This log message is at level SEVERE **" );
      LibraryLogManager.disposeLogger( defaultLogger );
   }

   @Test
   void testGetLogger_withExceptions()
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
      LibraryLogManager.disposeLogger( exceptionLogger );
   }

   @RepeatedTest( 3 )
   void testFinest_performanceWithDebugDisabled()
   {
      System.setProperty( "CA_DEBUG", "0" );
      final Logger noDebugLogger = LibraryLogManager.getLogger( LibraryLogManagerTest.class );
      final StopWatch stopWatch = StopWatch.createStarted();
      for ( int i = 0; i < 1_000_000; i++  )
      {
         noDebugLogger.finest("** This log message is at level FINEST **");
      }
      final long elapsedTimeInMicroseconds = stopWatch.getTime( TimeUnit.MICROSECONDS );
      noDebugLogger.info( "Sending 1,000,000 messages to DISABLED debug took " + elapsedTimeInMicroseconds + "us" );
      LibraryLogManager.disposeLogger( noDebugLogger );
   }

   @RepeatedTest( 3 )
   void testFinest_performanceWithDebugEnabled()
   {
      System.setProperty( "CA_DEBUG", "1" );
      final Logger debugLogger = LibraryLogManager.getLogger( LibraryLogManagerTest.class );
      final StopWatch stopWatch = StopWatch.createStarted();
      for ( int i = 0; i < 1_000; i++  )
      {
         debugLogger.finest("** This log message is at level FINEST **");
      }
      final long elapsedTimeInMillis = stopWatch.getTime( TimeUnit.MILLISECONDS );
      debugLogger.info( "Sending 1,000 messages to ENABLED debug took " + elapsedTimeInMillis + "ms" );
      LibraryLogManager.disposeLogger( debugLogger );
   }

   @Test
   void logMessage()
   {
      final Logger defaultLogger = LibraryLogManager.getLogger( LibraryLogManagerTest.class );

      // Note: see the reference below for further details about the format options available for logging.
      // https://docs.oracle.com/javase/1.5.0/docs/api/java/text/MessageFormat.html

      // String formatting options
      defaultLogger.log( Level.INFO, "STRING formatting options:" );
      defaultLogger.log( Level.INFO, "My msg with '{0}' format specifier is: {0}",  "Hello" );
      defaultLogger.log( Level.INFO, "My msg with '{0} {1}' format specifier is: {0} {1}\n", new String[] { "Hello", "world !" } );

      // Number formatting options
      defaultLogger.log( Level.INFO, "NUMBER formatting options:" );
      defaultLogger.log( Level.INFO, "My msg with '{0,number}' format specifier is: {0,number}", 123456789.987654321 );
      defaultLogger.log( Level.INFO, "My msg with '{0,number,integer}' specifier is: {0,number,integer}", 123456789.987654321 );
      defaultLogger.log( Level.INFO, "My msg with '{0,number,currency}' specifier is: {0,number,currency}", 123456789.987654321 );
      defaultLogger.log( Level.INFO, "My msg with '{0,number,percent}' specifier is: {0,number,percent}\n", 123456789.987654321 );

      // Date formatting options
      defaultLogger.log( Level.INFO, "DATE formatting options:" );
      defaultLogger.log( Level.INFO, "My msg with '{0,date}' format specifier is: {0,date}", new Date() );
      defaultLogger.log( Level.INFO, "My msg with '{0,date,short}' specifier is: {0,date,short}", new Date() );
      defaultLogger.log( Level.INFO, "My msg with '{0,date,medium}' specifier is: {0,date,medium}", new Date() );
      defaultLogger.log( Level.INFO, "My msg with '{0,date,long}' specifier is: {0,date,long}", new Date() );
      defaultLogger.log( Level.INFO, "My msg with '{0,date,full}' specifier is: {0,date,full}\n", new Date() );

      // Time formatting options
      defaultLogger.log( Level.INFO, "TIME formatting options:" );
      defaultLogger.log( Level.INFO, "My msg with '{0,time}' format specifier is: {0,time}", new Date() );
      defaultLogger.log( Level.INFO, "My msg with '{0,time,short}' specifier is: {0,time,short}", new Date() );
      defaultLogger.log( Level.INFO, "My msg with '{0,time,medium}' specifier is: {0,time,medium}", new Date() );
      defaultLogger.log( Level.INFO, "My msg with '{0,time,long}' specifier is: {0,time,long}", new Date() );
      defaultLogger.log( Level.INFO, "My msg with '{0,time,full}' specifier is: {0,time,full}\n", new Date() );

      LibraryLogManager.disposeLogger( defaultLogger );
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/


}
