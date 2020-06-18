/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/
/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

public class Constants
{

/*- Public attributes --------------------------------------------------------*/

   public enum ChannelProperties
   {
      nativeType, nativeTypeCode, remoteAddress, nativeElementCount
   }

   /**
    * String value of the JVM property key which specifies whether to strip the hostname returned by
    * InetAddress.getLocalHost().getHostName().
    */
   public static final String CA_STRIP_HOSTNAME = "CA_STRIP_HOSTNAME";

   /**
    * String value of the JVM property key to provide (override) hostname.
    */
   public static final String CA_HOSTNAME_KEY = "HOSTNAME";

   /**
    * Minimal priority.
    */
   public static final short CHANNEL_PRIORITY_MIN = 0;

   /**
    * Maximal priority.
    */
   public static final short CHANNEL_PRIORITY_MAX = 99;

   /**
    * Default priority.
    */
   public static final short CHANNEL_PRIORITY_DEFAULT = CHANNEL_PRIORITY_MIN;

   /**
    * DB links priority.
    */
   public static final short CHANNEL_PRIORITY_LINKS_DB = CHANNEL_PRIORITY_MAX;

   /**
    * Archive priority.
    */
   public static final short CHANNEL_PRIORITY_ARCHIVE = (short) ((CHANNEL_PRIORITY_MAX + CHANNEL_PRIORITY_MIN) / 2);

   /**
    * OPI priority.
    */
   public static final short CHANNEL_PRIORITY_OPI = CHANNEL_PRIORITY_MIN;

   /* -------- Core CA constants -------- */

   /**
    * CA protocol major revision (implemented by this library).
    */
   public static final short CA_MAJOR_PROTOCOL_REVISION = 4;

   /**
    * CA protocol minor revision (implemented by this library).
    */
   public static final short CA_MINOR_PROTOCOL_REVISION = 13;

   /**
    * Unknown CA protocol minor revision.
    */
   public static final short CA_UNKNOWN_MINOR_PROTOCOL_REVISION = 0;

   /**
    * Initial delay in milliseconds between creation of a new Context and the
    * first attempt to register with the CA Repeater.
    */
   public static final int CA_REPEATER_INITIAL_REGISTRATION_DELAY = 500;

   /**
    * CA Repeater attempted registration interval in milliseconds.
    */
   public static final int CA_REPEATER_REGISTRATION_INTERVAL = 60_000;

   /**
    * CA protocol message header size.
    */
   public static final short CA_MESSAGE_HEADER_SIZE = 16;

   /**
    * CA protocol message extended header size.
    */
   public static final short CA_EXTENDED_MESSAGE_HEADER_SIZE = (short) (CA_MESSAGE_HEADER_SIZE + 8);

   /**
    * UDP maximum send message size.
    * MAX_UDP: 1500 (max of Ethernet and 802.{2,3} MTU) - 20(IP) - 8(UDP)
    * (the MTU of Ethernet is currently independent of its speed variant)
    */
   public static final int MAX_UDP_SEND = 1024;

   /**
    * UDP maximum receive message size.
    */
   public static final int MAX_UDP_RECV = 0xFFFF + 16;

   /**
    * TCP maximum receive message size.
    */
   public static final int MAX_TCP_RECV = 1024 * 16 + CA_EXTENDED_MESSAGE_HEADER_SIZE;

   /**
    * Default priority (corresponds to POSIX SCHED_OTHER)
    */
   public static final short CA_DEFAULT_PRIORITY = 0;

   /**
    * Read access right mask.
    */
   public static final int CA_PROTO_ACCESS_RIGHT_READ = 1;

   /**
    * Write access right mask.
    */
   public static final int CA_PROTO_ACCESS_RIGHT_WRITE = 1 << 1;

   /**
    * Do not require response for CA search request.
    */
   public static final short CA_SEARCH_DONTREPLY = 5;

   /**
    * Require response (even if not found) for CA search request over TCP.
    */
   public static final short CA_SEARCH_DOREPLY = 10;

   /**
    * Echo (state-of-health message) response timeout in ms.
    */
   public static final long CA_ECHO_TIMEOUT = 5000;

   /**
    * Max. (requested) string size.
    */
   public static final int MAX_STRING_SIZE = 40;

   /**
    * Unreasonable channel name length.
    */
   public static final int UNREASONABLE_CHANNEL_NAME_LENGTH = 500;

/*- Private attributes -------------------------------------------------------*/
/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Private methods ----------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
