/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca.impl;

/*- Imported packages --------------------------------------------------------*/

import net.jcip.annotations.Immutable;
import org.apache.commons.lang3.Validate;
import java.util.Properties;
import java.util.logging.Level;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Holds other parameters that the user may configure to control general-
 * purpose aspects of the CA library behaviour.
 *
 * This class is a singleton shared throughout the application.
 */
@Immutable
public class LibraryConfiguration
{

/*- Public attributes --------------------------------------------------------*/

   public enum PropertyNames
   {
      CA_MONITOR_NOTIFIER_IMPL,
      CA_REPEATER_DISABLE,
      CA_REPEATER_OUTPUT_CAPTURE,
      CA_REPEATER_LOG_LEVEL,
      CA_LIBRARY_LOG_LEVEL
   }

   public enum PropertyDefaults
   {
      CA_MONITOR_NOTIFIER_IMPL( "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl,16" ),
      CA_REPEATER_DISABLE( false ),
      CA_REPEATER_OUTPUT_CAPTURE( false ),
      CA_REPEATER_LOG_LEVEL( Level.INFO ),
      CA_LIBRARY_LOG_LEVEL( Level.INFO ) ;

      private final Object defaultValue;
      PropertyDefaults( Object defaultValue )
      {
         this.defaultValue = defaultValue;
      }

      public Object getDefaultValue()
      {
         return defaultValue;
      }
   }

/*- Private attributes -------------------------------------------------------*/

   private static final LibraryConfiguration instance = new LibraryConfiguration( System.getProperties() );
   private final Properties properties;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Constructs a new instance whose default values may be overridden by
    * environmental variables or values set in the supplied properties
    * object.
    *
    * @param properties an object providing property overrides.
    */
   private LibraryConfiguration( Properties properties )
   {
      this.properties = Validate.notNull( properties );
   }

/*- Public methods -----------------------------------------------------------*/

   public static LibraryConfiguration getInstance()
   {
      return instance;
   }

   /**
    * Returns the configuration of the CA library's monitor notification engine. See the README
    * file for further information.
    *
    * @return the configured value.
    */
   public String getMonitorNotifierImplementation()
   {
      final String defaultValue = (String) PropertyDefaults.CA_MONITOR_NOTIFIER_IMPL.getDefaultValue();
      return ConfigurationReader.readStringProperty( PropertyDefaults.CA_MONITOR_NOTIFIER_IMPL.toString(), properties, defaultValue );
   }

   /**
    * Indicates whether the CA library should start a CA Repeater instance when the first CA library context
    * is created and stop it when the last CA library context is destroyed.
    *
    * When disabled the CA library will assume that a separate repeater instance has been started on the
    * local machine and will attempt to register with that.
    *
    * @return the configured value.
    */
   public boolean isRepeaterEnabled()
   {
      final boolean defaultValue = (Boolean) PropertyDefaults.CA_REPEATER_DISABLE.getDefaultValue();
      return ! ConfigurationReader.readBooleanProperty( PropertyDefaults.CA_REPEATER_DISABLE.toString(), properties, defaultValue );
   }

   /**
    * Indicates whether the output of the CA Repeater should be captured in the CA library log.
    * @return the configured value.
    */
   public boolean isRepeaterOutputCaptureEnabled()
   {
      final boolean defaultValue = (Boolean) PropertyDefaults.CA_REPEATER_OUTPUT_CAPTURE.getDefaultValue();
      return ConfigurationReader.readBooleanProperty( PropertyDefaults.CA_REPEATER_OUTPUT_CAPTURE.toString(), properties, defaultValue );
   }

   /**
    * Returns the minimum level of the log messages that the CA Repeater will send to the standard output stream.
    * @return the configured value.
    */
   public Level getRepeaterLogLevel()
   {
      final Level defaultValue = (Level) PropertyDefaults.CA_REPEATER_LOG_LEVEL.getDefaultValue();
      return ConfigurationReader.readDebugLevelProperty( PropertyDefaults.CA_REPEATER_LOG_LEVEL.toString(), properties, defaultValue );
   }

   /**
    * Returns the minimum level of log messages that the CA library will send to the standard output stream.
    * @return the configured value.
    */
   public Level getLibraryLogLevel()
   {
      final Level defaultValue = (Level) PropertyDefaults.CA_LIBRARY_LOG_LEVEL.getDefaultValue();
      return ConfigurationReader.readDebugLevelProperty( PropertyDefaults.CA_LIBRARY_LOG_LEVEL.toString(), properties, defaultValue );
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
