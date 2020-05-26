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

   private static final StreamHandler flushingStandardOutputStreamHandler;

   static {
      // Could consider here setting the default locale for this instance of
      // the Java Virtual Machine. But currently (2020-05-10) it is not
      // considered that the library "owns" this decision.
      // Locale.setDefault( Locale.ROOT );
      // Note: the definition below determines the format of all log messages
      // emitted by the CA library.
      System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tF %1$tT.%1$tL %3$s %4$s %5$s %6$s %n");

      // Create a stream handler that is like the normal output stream handler except
      // it will ensure that each message gets flushed to the log. Note: this may
      // impose some performance penalty during testing if copious messages are
      // being logged. During normal CA usage the library does not emit many
      // messages so in the normal situation this limitation should not be important.
      flushingStandardOutputStreamHandler = new StreamHandler( System.out, new SimpleFormatter() )
      {
         @Override
         public synchronized void publish( final LogRecord record ) {
            super.publish( record );
            flush();
         }
      };

      // The output handler will emit all messages that are sent to it. It is up
      // to the individual loggers to determine what is appropriate for their
      // particular operating contexts.
      flushingStandardOutputStreamHandler.setLevel( Level.ALL );
   }


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/

   /**
    * Returns a logger for the specified class that will send messages to the
    * standard output stream providing their log level exceeds the debug
    * level defined by the CA_DEBUG system property.
    *
    * When CA_DEBUG is defined and set to anything other than "0" or "false"
    * ALL debug messages will be supported. Otherwise only messages of level
    * INFO and above will be emitted.
    *
    * @param clazz the class that the logger will be associated with
    *     when logging messages.
    *
    * @return the configured logger.
    */
   public static Logger getLogger( Class<?> clazz)
   {
      final String debugProperty = System.getProperty( "CA_DEBUG", "false" );
      final boolean debuggingDisabled = debugProperty.toLowerCase().equals( "false" ) || debugProperty.equals( "0" );
      final Level logLevel = debuggingDisabled ? Level.INFO : Level.ALL;
      return getLogger( clazz, logLevel );
   }

   /**
    * Returns a logger for the specified class that will send messages to the
    * standard output stream providing their log level exceeds the specified
    * debug level.
    *
    * @param clazz the class that the logger will be associated with
    *     when logging messages.
    *
    * @param debugLevel Set the log level specifying which message levels
    *    will be logged by this logger. Message levels lower than this
    *    value will be discarded. The level value Level.OFF can be used
    *    to turn off logging.
    *
    * @return the configured logger.
    */
   public static Logger getLogger( Class<?> clazz, Level debugLevel )
   {
      // Currently (2020-05-14) the simple name is used in the log. This
      // is considerably shorter than the fully qualified class name. Other
      // implementations are possible. For example the Spring framework
      // takes the approach of shortening the package names in the FQN to
      // a single character. Another approach would be to extract the name
      // automatically from the calling stack.
      final Logger logger = Logger.getLogger( clazz.getSimpleName() );
      logger.setUseParentHandlers( false );

      if ( logger.getHandlers().length == 0 )
      {
         logger.addHandler( flushingStandardOutputStreamHandler );
      }
      else
      {
         System.out.println( "\nWARNING: More than one logger defined for class: '" + clazz.getSimpleName() + "'.\n" );
      }
      logger.setLevel( debugLevel );

      return logger;
   }


/*- Package-level methods ----------------------------------------------------*/

   /**
    * Provided to enable tests only.
    * @param logger the logger whose handlers are to be disposed.
    */
   static void disposeLogger( Logger logger)
   {
      if ( logger.getHandlers().length == 1 )
      {
         logger.removeHandler( flushingStandardOutputStreamHandler );
      }
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
