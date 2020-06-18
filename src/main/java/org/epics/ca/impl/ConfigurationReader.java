/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca.impl;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.epics.ca.util.logging.LibraryLogManager;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Provides helper methods to return configuration information of different
 * types from either the supplied default values, environmental variables set
 * in the host operating system, or from the supplied properties object.
 */
public class ConfigurationReader
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( ConfigurationReader.class );

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/

   /**
    * Returns the value of the named string configuration item by obtaining
    * it from either the operating system environment, the supplied properties
    * object or the supplied default.
    *
    * @param item the name of the configuration item to read.
    * @param properties an object whose configuration entries may override
    *    the values specified by OS environmental variables.
    * @param defaultValue the value to return when neither the OS environment
    *    nor the properties object provide value resolution.
    * @return the result.
    * @throws NullPointerException if any of the arguments were null.
    */
   public static String readStringProperty( String item, Properties properties, String defaultValue )
   {
      Validate.notNull( item );
      Validate.notNull( properties );
      Validate.notNull( defaultValue );

      final String sValue = properties.getProperty( item, System.getenv( item ) );
      return (sValue != null) ? sValue : defaultValue;
   }

   /**
    * Returns the value of the named boolean configuration item by obtaining
    * it from either the operating system environment, the supplied properties
    * object or the supplied default.
    *
    * When a boolean value is specified any of the following string values may
    * be used: "yes" / "true" / "no" / "false".
    *
    * @param item the name of the configuration item to read.
    * @param properties an object whose configuration entries may override
    *    the values specified by OS environmental variables.
    * @param defaultValue the value to return when neither the OS environment
    *    nor the properties object provide value resolution.
    * @return the result.
    * @throws NullPointerException if any of the arguments were null.
    */
   public static boolean readBooleanProperty( String item, Properties properties, boolean defaultValue )
   {
      Validate.notNull( item );
      Validate.notNull( properties );

      final String sValue = properties.getProperty( item, System.getenv( item ) );
      if ( sValue != null )
      {
         if ( ( sValue.equalsIgnoreCase( "yes" ) ) || ( sValue.equalsIgnoreCase( "true" ) ) )
         {
            return true;
         }
         else if ( ( sValue.equalsIgnoreCase( "no" ) ) || ( sValue.equalsIgnoreCase( "false" ) ) )
         {
            return false;
         }
         else
         {
            logger.config( "Failed to parse boolean value for property " + item + ": \"" + sValue + "\", \"YES\" or \"NO\" expected.");
            return defaultValue;
         }
      }
      else
      {
         return defaultValue;
      }
   }

   /**
    * Returns the value of the named floating-point configuration item by obtaining
    * it from either the operating system environment, the supplied properties
    * object or the supplied default.
    *
    * @param item the name of the configuration item to read.
    * @param properties an object whose configuration entries may override
    *    the values specified by OS environmental variables.
    * @param defaultValue the value to return when neither the OS environment
    *    nor the properties object provide value resolution.
    * @return the result.
    * @throws NullPointerException if any of the arguments were null.
    */
   public static float readFloatProperty( String item, Properties properties,  float defaultValue )
   {
      Validate.notNull( item );
      Validate.notNull( properties );

      final String sValue = properties.getProperty( item, System.getenv( item ));
      if ( sValue != null )
      {
         try
         {
            return Float.parseFloat( sValue );
         }
         catch ( Throwable th )
         {
            logger.config( "Failed to parse float value for property " + item + ": \"" + sValue + "\"." );
         }
      }
      return defaultValue;
   }

   /**
    * Returns the value of the named integer configuration item by obtaining
    * it from either the operating system environment, the supplied properties
    * object or the supplied default.
    *
    * @param item the name of the configuration item to read.
    * @param properties an object whose configuration entries may override
    *    the values specified by OS environmental variables.
    * @param defaultValue the value to return when neither the OS environment
    *    nor the properties object provide value resolution.
    * @return the result.
    * @throws NullPointerException if any of the arguments were null.
    */
   public static int readIntegerProperty( String item, Properties properties, int defaultValue )
   {
      Validate.notNull( item );
      Validate.notNull( properties );

      final String sValue = properties.getProperty( item, System.getenv( item ));
      if ( sValue != null )
      {
         try
         {
            return Integer.parseInt( sValue );
         }
         catch ( Throwable th )
         {
            logger.config( "Failed to parse integer value for property " + item + ": \"" + sValue + "\"." );
         }
      }
      return defaultValue;
   }

   /**
    * Returns the value of the named logging level configuration item by obtaining
    * it from either the operating system environment, the supplied properties
    * object or the supplied default.
    *
    * @param item the name of the configuration item to read.
    * @param properties an object whose configuration entries may override
    *    the values specified by OS environmental variables.
    * @param defaultValue the value to return when neither the OS environment
    *    nor the properties object provide value resolution.
    * @return the result.
    * @throws NullPointerException if any of the arguments were null.
    */
   @SuppressWarnings( "SameParameterValue" )
   public static Level readDebugLevelProperty( String item, Properties properties, Level defaultValue )
   {
      Validate.notNull( item );
      Validate.notNull( properties );
      Validate.notNull( defaultValue );

      final String sValue = readStringProperty( item, properties, defaultValue.toString() );
      try
      {
         return Level.parse( sValue );
      }
      catch ( Throwable th )
      {
         logger.config( "Failed to parse logging level value for property " + item + ": \"" + sValue + "\"." );
      }
      return defaultValue;
   }

/*- Package-level methods ----------------------------------------------------*/
/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
