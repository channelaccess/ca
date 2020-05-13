/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca.util.logging;

/*- Imported packages --------------------------------------------------------*/

import java.util.logging.*;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class LibraryLogManager
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final StreamHandler flushingStreamHandler;

   static {
      System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT.%1$tL %3$s %4$s %5$s %6$s %n");
      flushingStreamHandler = new StreamHandler( System.out, new SimpleFormatter() )
      {
         @Override
         public synchronized void publish( final LogRecord record ) {
            super.publish(record);
            flush();
         }
      };
      flushingStreamHandler.setLevel( Level.ALL );
   }


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/

   public static Logger getLogger( Class<?> clazz)
   {
      final String debugProperty = System.getProperty( "CA_DEBUG", "false" );
      final boolean debuggingDisabled = debugProperty.toLowerCase().equals( "false" ) || debugProperty.equals( "0" );
      final Level logLevel = debuggingDisabled ? Level.INFO : Level.ALL;
      return getLogger( clazz, logLevel );
   }

   public static Logger getLogger( Class<?> clazz, Level debugLevel )
   {
      final Logger logger = Logger.getLogger( clazz.getSimpleName() );
      logger.setUseParentHandlers( false );
      logger.addHandler( flushingStreamHandler );
      logger.setLevel( debugLevel );
      return logger;
   }

/*- Package-level methods ----------------------------------------------------*/
/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
