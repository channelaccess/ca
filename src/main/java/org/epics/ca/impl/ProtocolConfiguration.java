/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca.impl;

/*- Imported packages --------------------------------------------------------*/

import net.jcip.annotations.Immutable;
import org.apache.commons.lang3.Validate;
import org.epics.ca.Constants;

import java.util.Properties;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Holds the standard EPICS parameters that the user configures to control
 * the behaviour of the CA library with respect to channel-access protocol
 * handling.
 *
 * These parameters can be set differently for each CA Context.
 */
@Immutable
public class ProtocolConfiguration
{

/*- Public attributes --------------------------------------------------------*/

   public enum PropertyNames
   {
      EPICS_CA_ADDR_LIST,
      EPICS_CA_AUTO_ADDR_LIST,
      EPICS_CA_CONN_TMO,
      EPICS_CA_REPEATER_PORT,
      EPICS_CA_SERVER_PORT,
      EPICS_CA_MAX_ARRAY_BYTES
   }

   public enum PropertyDefaults
   {
      EPICS_CA_ADDR_LIST( "" ),
      EPICS_CA_AUTO_ADDR_LIST( true ),
      EPICS_CA_CONN_TMO( 30.0f ),
      EPICS_CA_REPEATER_PORT( Constants.CA_REPEATER_PORT_DEFAULT ),
      EPICS_CA_SERVER_PORT( Constants.CA_SERVER_PORT_DEFAULT ),
      EPICS_CA_MAX_ARRAY_BYTES( Integer.MAX_VALUE );

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
   public ProtocolConfiguration( Properties properties )
   {
      this.properties = Validate.notNull( properties );
   }

/*- Public methods -----------------------------------------------------------*/

   /**
    * Returns a space-separated list of broadcast addresses which can be used
    * for process variable name resolution.
    *
    * Each address must be of the form: ip.number:port or host.name:port
    *
    * @return the configured value.
    */
   public String getAddressList()
   {
      final String defaultValue = (String) PropertyDefaults.EPICS_CA_ADDR_LIST.getDefaultValue();
      return ConfigurationReader.readStringProperty( PropertyDefaults.EPICS_CA_ADDR_LIST.toString(), properties, defaultValue );
   }

   /**
    * Defines whether or not the network interfaces should be discovered at runtime.
    *
    * @return the configured value.
    */
   public boolean getAutoAddressList()
   {
      final boolean defaultValue = (Boolean) PropertyDefaults.EPICS_CA_AUTO_ADDR_LIST.getDefaultValue();
      return ConfigurationReader.readBooleanProperty( PropertyDefaults.EPICS_CA_AUTO_ADDR_LIST.toString(), properties, defaultValue );
   }

   /**
    * If the context doesn't see a beacon from a server that it is connected to for connectionTimeout
    * seconds then a state-of-health message is sent to the server over TCP/IP. If this state-of-health
    * message isn't promptly replied to then the context will assume that the server is no longer
    * present on the network and disconnect.
    *
    * @return the configured value.
    */
   public float getConnectionTimeout()
   {
      final float defaultValue = (Float) PropertyDefaults.EPICS_CA_CONN_TMO.getDefaultValue();
      return ConfigurationReader.readFloatProperty( PropertyDefaults.EPICS_CA_CONN_TMO.toString(), properties, defaultValue );
   }

   /**
    * Returns the port number that the CA library will contact when attempting to register with the CA Repeater.
    * @return the configured value.
    */
   public int getRepeaterPort()
   {
      final int defaultValue = (Integer) PropertyDefaults.EPICS_CA_REPEATER_PORT.getDefaultValue();
      return ConfigurationReader.readIntegerProperty( PropertyDefaults.EPICS_CA_REPEATER_PORT.toString(), properties, defaultValue );
   }

   /**
    * Returns the port number that the CA library will use when broadcasting channel search requests.
    * @return the configured value.
    */
   public int getServerPort()
   {
      final int defaultValue = (Integer) PropertyDefaults.EPICS_CA_SERVER_PORT.getDefaultValue();
      return ConfigurationReader.readIntegerProperty( PropertyDefaults.EPICS_CA_SERVER_PORT.toString(), properties, defaultValue );
   }

   /**
    * Returns the length in bytes of the maximum array size that may pass through CA.
    * Defaults to 0 (&lt;=0 means unlimited).
    *
    * @return the configured value.
    */
   public int getMaxArrayBytes()
   {
      final int defaultValue = (Integer) PropertyDefaults.EPICS_CA_MAX_ARRAY_BYTES.getDefaultValue();
      return ConfigurationReader.readIntegerProperty( PropertyDefaults.EPICS_CA_MAX_ARRAY_BYTES.toString(), properties, defaultValue );
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
