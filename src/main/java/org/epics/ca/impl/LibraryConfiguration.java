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

   /**
    * Default Monitor Notifier Engine configuration.
    */
   public static final String CA_MONITOR_NOTIFIER_IMPL_DEFAULT = "BlockingQueueMultipleWorkerMonitorNotificationServiceImpl,16";

   /**
    * Default state of enablement of the CA Repeater spawned by the CA library.
    */
   public static final boolean CA_REPEATER_DISABLE_DEFAULT = false;

   /**
    * Default state of enablement of CA Repater output capture.
    */
   public static final boolean CA_REPEATER_OUTPUT_CAPTURE_DEFAULT = false;

   /**
    * Default log level for the CA Repeater.
    */
   public static final Level CA_REPEATER_LOG_LEVEL_DEFAULT = Level.INFO;

   /**
    * Default log level for the CA library.
    */
   public static final Level CA_LIBRARY_LOG_LEVEL_DEFAULT = Level.INFO;


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
      return ConfigurationReader.readStringProperty( PropertyNames.CA_MONITOR_NOTIFIER_IMPL.toString(), properties, CA_MONITOR_NOTIFIER_IMPL_DEFAULT );
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
      return ! ConfigurationReader.readBooleanProperty( PropertyNames.CA_REPEATER_DISABLE.toString(), properties, CA_REPEATER_DISABLE_DEFAULT );
   }

   /**
    * Indicates whether the output of the CA Repeater should be captured in the CA library log.
    * @return the configured value.
    */
   public boolean isRepeaterOutputCaptureEnabled()
   {
      return ConfigurationReader.readBooleanProperty( PropertyNames.CA_REPEATER_OUTPUT_CAPTURE.toString(), properties, CA_REPEATER_OUTPUT_CAPTURE_DEFAULT );
   }

   /**
    * Returns the minimum level of the log messages that the CA Repeater will send to the standard output stream.
    * @return the configured value.
    */
   public Level getRepeaterLogLevel()
   {
      return ConfigurationReader.readDebugLevelProperty( PropertyNames.CA_REPEATER_LOG_LEVEL.toString(), properties, CA_REPEATER_LOG_LEVEL_DEFAULT );
   }

   /**
    * Returns the minimum level of log messages that the CA library will send to the standard output stream.
    * @return the configured value.
    */
   public Level getLibraryLogLevel()
   {
      return ConfigurationReader.readDebugLevelProperty(PropertyNames.CA_LIBRARY_LOG_LEVEL.toString(), properties, CA_LIBRARY_LOG_LEVEL_DEFAULT);
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
