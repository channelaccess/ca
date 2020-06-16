/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca.util.logging;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;

import java.util.logging.*;

import static org.epics.ca.Constants.CA_LIBRARY_LOG_LEVEL_DEFAULT;
import static org.epics.ca.Constants.CA_LIBRARY_LOG_LEVEL;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class LibraryLogManager
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final StreamHandler flushingStandardOutputStreamHandler;

   static
   {
      // Could consider here setting the default locale for this instance of
      // the Java Virtual Machine. But currently (2020-05-10) it is not
      // considered that the library "owns" this decision.
      // Locale.setDefault( Locale.ROOT );
      // Note: the definition below determines the format of all log messages
      // emitted by the CA library.
      System.setProperty( "java.util.logging.SimpleFormatter.format", "%1$tF %1$tT.%1$tL %3$s %4$s %5$s %6$s %n" );

      // Create a stream handler that is like the normal output stream handler except
      // it will ensure that each message gets flushed to the console. Note: this may
      // impose some performance penalty during testing if copious messages are
      // being logged. During normal CA usage the library does not emit many
      // messages so in the normal situation this limitation should not be important.
      flushingStandardOutputStreamHandler = new StreamHandler( System.out, new SimpleFormatter() )
      {
         @Override
         public synchronized void publish( final LogRecord record )
         {
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
    * standard output stream providing their log level exceeds the level defined 
    * by the CA_LIBRARY_LOG_LEVEL system property.
    *
    * When CA_LIBRARY_LOG_LEVEL is not defined all messages of Level.INFO and
    * above will be logged.
    *
    * @param clazz the class that the log messages will be associated with.
    * @return the configured logger.
    * 
    * throws NullPointerException if clazz was null.
    * throws IllegalArgumentException if the string token associated with
    * CA_LIBRARY_LOG_LEVEL could not be interpreted as a valid log level.
    */
   public static Logger getLogger( Class<?> clazz )
   {
      Validate.notNull( clazz );

      final String loglevelAsString = System.getProperty( CA_LIBRARY_LOG_LEVEL, CA_LIBRARY_LOG_LEVEL_DEFAULT  );
      final Level logLevel = Level.parse( loglevelAsString );
      return getLogger( clazz, logLevel );
   }

   /**
    * Returns a logger for the specified class that will send messages to the
    * standard output stream providing their log level exceeds the specified
    * log level.
    *
    * @param clazz the class that the logger will be associated with
    *     when logging messages.
    *
    * @param logLevel Set the log level specifying which message levels
    *    will be sent to the standard output stream by this logger. Message levels
    *    lower than this value will be discarded. The value Level.OFF can be
    *    used to completely turn off logging.
    *
    * @return the configured logger.
    */
   public static Logger getLogger( Class<?> clazz, Level logLevel )
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
      logger.setLevel( logLevel );

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
