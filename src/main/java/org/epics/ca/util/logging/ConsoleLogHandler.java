package org.epics.ca.util.logging;

import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Implementation of Java Logging API handler.
 */
public class ConsoleLogHandler extends Handler
{

   /**
    * Logging formatter.
    */
   private final Formatter formatter;

   /**
    * Default constructor.
    */
   public ConsoleLogHandler()
   {
      this (new ConsoleLogFormatter ());
   }

   /**
    * Construct handler with using giver formatter.
    *
    * @param   formatter   console log formatter, non-<code>null</code>.
    */
   public ConsoleLogHandler( Formatter formatter )
   {
      this.formatter = formatter;
   }

   /**
    * @see java.util.logging.Handler#close()
    */
   public void close() throws SecurityException
   {
      // noop
   }

   /**
    * @see java.util.logging.Handler#flush()
    */
   public void flush()
   {
      System.out.flush ();
   }

   /**
    * Prints the log record to the console using the current formatter, if the
    * log record is loggable.
    *
    * @param record the log record to publish
    * @see java.util.logging.Handler#publish(java.util.logging.LogRecord)
    */
   public void publish( LogRecord record )
   {
      if ( isLoggable (record) )
         System.out.print (formatter.format (record));
   }

   /**
    * Setup this handler as the only one root handler.
    *
    * @param logLevel root log level to be set.
    */
   public static void defaultConsoleLogging( Level logLevel )
   {
      LogManager.getLogManager ().reset ();
      Logger rootLogger = Logger.getLogger ("");
      rootLogger.setLevel (logLevel);
      rootLogger.addHandler (new ConsoleLogHandler ());
   }

}
