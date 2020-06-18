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

/*- Private attributes -------------------------------------------------------*/

   private static final int CA_PORT_BASE = 5056;
   private final Properties properties;

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

   /**
    * Default address list
    */
   public static final String EPICS_CA_ADDR_LIST_DEFAULT = "";

   /**
    * Default address list
    */
   public static final boolean EPICS_CA_AUTO_ADDR_LIST_DEFAULT = true;

   /**
    * Default connection timeout.
    */
   public static final float EPICS_CA_CONN_TMO_DEFAULT = 30.0f;

   /**
    * Default CA repeater port.
    */
   public static final int EPICS_CA_REPEATER_PORT_DEFAULT = CA_PORT_BASE + 2 * Constants.CA_MAJOR_PROTOCOL_REVISION + 1;

   /**
    * Default CA server port.
    */
   public static final int EPICS_CA_SERVER_PORT_DEFAULT = CA_PORT_BASE + 2 * Constants.CA_MAJOR_PROTOCOL_REVISION;

   /**
    * Default maximum size of array passed through CA. (&lt;=0 means unlimited).
    */
   public static final int EPICS_CA_MAX_ARRAY_BYTES_DEFAULT = 0;


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Constructs a new instance whose default values may be overrideen
    * by environmental variables only.
    */
   public ProtocolConfiguration()
   {
      this( new Properties() );
   }

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
      return ConfigurationReader.readStringProperty( PropertyNames.EPICS_CA_ADDR_LIST.toString(), properties, EPICS_CA_ADDR_LIST_DEFAULT );
   }

   /**
    * Defines whether or not the network interfaces should be discovered at runtime.
    *
    * @return the configured value.
    */
   public boolean getAutoAddressList()
   {
      return ConfigurationReader.readBooleanProperty( PropertyNames.EPICS_CA_AUTO_ADDR_LIST.toString(), properties, EPICS_CA_AUTO_ADDR_LIST_DEFAULT );
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
      return ConfigurationReader.readFloatProperty( PropertyNames.EPICS_CA_CONN_TMO.toString(), properties, EPICS_CA_CONN_TMO_DEFAULT );
   }

   /**
    * Returns the port number that the CA library will contact when attempting to register with the CA Repeater.
    * @return the configured value.
    */
   public int getRepeaterPort()
   {
      return ConfigurationReader.readIntegerProperty( PropertyNames.EPICS_CA_REPEATER_PORT.toString(), properties, EPICS_CA_REPEATER_PORT_DEFAULT );
   }

   /**
    * Returns the port number that the CA library will use when broadcasting channel search requests.
    * @return the configured value.
    */
   public int getServerPort()
   {
      return ConfigurationReader.readIntegerProperty( PropertyNames.EPICS_CA_SERVER_PORT.toString(), properties, EPICS_CA_SERVER_PORT_DEFAULT );
   }

   /**
    * Returns the length in bytes of the maximum array size that may pass through CA.
    * Defaults to 0 (&lt;=0 means unlimited).
    *
    * @return the configured value.
    */
   public int getMaxArrayBytes()
   {
      return ConfigurationReader.readIntegerProperty( PropertyNames.EPICS_CA_MAX_ARRAY_BYTES.toString(), properties, EPICS_CA_MAX_ARRAY_BYTES_DEFAULT );
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
