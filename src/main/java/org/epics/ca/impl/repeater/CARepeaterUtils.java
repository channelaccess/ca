/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/
/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

import org.epics.ca.util.logging.ConsoleLogHandler;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility functions used by the CA Repeater.
 */
class CARepeaterUtils
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/
/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/

   /**
    * Attempts to interpret the supplied string as an integer, returning
    * the result if successful or otherwise some default value.
    *
    * @param stringToParse the input string.
    * @param defaultValue the value to be returned if the  input string cannot
    *    be parsed.
    *
    * @return the result
    */
   static int parseToInt( String stringToParse, int defaultValue )
   {
      int ret;
      try
      {
         ret = Integer.parseInt( stringToParse );
      }
      catch( NumberFormatException ex)
      {
         ret = defaultValue; //Use default value if parsing failed
      }
      return ret;
   }

   /**
    * Initialises the supplied logger.
    *
    * @param logger the logger to initialise.
    */
   static void initializeLogger( Logger logger )
   {

      logger.setLevel( Level.ALL );

      // Install console logger only if there is not one already installed
      Logger inspectedLogger = logger;
      boolean found = false;
      while ( inspectedLogger != null )
      {
         for ( Handler handler : inspectedLogger.getHandlers() )
         {
            if ( handler instanceof ConsoleLogHandler )
            {
               found = true;
               break;
            }
         }
         inspectedLogger = inspectedLogger.getParent();
      }

      if ( ! found )
      {
         logger.addHandler( new ConsoleLogHandler() );
      }
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/
}
