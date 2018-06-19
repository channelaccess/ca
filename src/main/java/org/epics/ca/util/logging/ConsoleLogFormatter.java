package org.epics.ca.util.logging;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * An implementation of <code>java.util.logging.Formatter</code>.
 * Produces single line log reports meant to go to the console.
 */
public class ConsoleLogFormatter extends Formatter
{
   /**
    * System property key to enable trace messages.
    */
   public static final String KEY_TRACE = "TRACE";

   /**
    * Line separator string.
    */
   private final boolean showTrace = System.getProperties ().containsKey (KEY_TRACE);

   /**
    * Line separator string.
    */
   private final static String lineSeparator = System.getProperty ("line.separator");

   /**
    * ISO 8601 date formatter.
    */
   private static final SimpleDateFormat timeFormatter = new SimpleDateFormat ("yyyy-MM-dd'T'HH:mm:ss.SSS");

   /**
    * Date object (used not to recreate it every time).
    */
   private final Date date = new Date ();

   /**
    * Format the given LogRecord.
    *
    * @param record the log record to be formatted.
    * @return a formatted log record
    */
   public String format( LogRecord record )
   {
      StringBuffer sb = new StringBuffer (128);

      synchronized ( date )
      {
         date.setTime (record.getMillis ());
         sb.append (timeFormatter.format (date));
      }
		
		/*
		if (record.getLoggerName() != null)
		{
			sb.append(' ');
			sb.append('[');
			sb.append(record.getLoggerName());
			sb.append(']');
		}
		*/

      sb.append (' ');
      sb.append (record.getMessage ());
      sb.append (' ');

      //sb.append(record.getLevel().getLocalizedName());

      // trace
      if ( showTrace )
      {
         // source
         sb.append ('[');
         if ( record.getSourceClassName () != null )
            sb.append (record.getSourceClassName ());

         // method name
         if ( record.getSourceMethodName () != null )
         {
            sb.append ('#');
            sb.append (record.getSourceMethodName ());
         }
         sb.append (']');
      }

      sb.append (lineSeparator);


      // exceptions
      if ( record.getThrown () != null )
      {
			/*
			try
			{
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				record.getThrown().printStackTrace(pw);
				pw.close();
				sb.append(sw.toString());
			} catch (Exception ex) {}
			*/
         record.getThrown ().printStackTrace ();
      }

      return new String (sb);
   }

}
