package org.epics.ca;

import org.epics.ca.impl.monitor.MonitorNotificationServiceFactoryCreator;

public interface Constants
{

   enum ChannelProperties
   {
      nativeType, nativeTypeCode, remoteAddress, nativeElementCount
   }

   /**
    * String value of the JVM property key to turn on debugging.
    */
   String CA_DEBUG = "CA_DEBUG";

   /**
    * String value of the JVM property key to strip hostname returned by InetAddress.getLocalHost().getHostName().
    */
   String CA_STRIP_HOSTNAME = "CA_STRIP_HOSTNAME";

   /**
    * String value of the JVM property key to provide (override) hostname.
    */
   String CA_HOSTNAME_KEY = "HOSTNAME";

   /**
    * String value of the JVM property key to configure the monitor notification engine.
    */
   String CA_MONITOR_NOTIFIER_IMPL = "CA_MONITOR_NOTIFIER_IMPL";

   /**
    * String value defining the default monitor notification engine.
    */
   String CA_MONITOR_NOTIFIER_DEFAULT_IMPL = MonitorNotificationServiceFactoryCreator.DEFAULT_IMPL;

   /**
    * Minimal priority.
    */
   short CHANNEL_PRIORITY_MIN = 0;
   /**
    * Maximal priority.
    */
   short CHANNEL_PRIORITY_MAX = 99;
   /**
    * Default priority.
    */
   short CHANNEL_PRIORITY_DEFAULT = CHANNEL_PRIORITY_MIN;
   /**
    * DB links priority.
    */
   short CHANNEL_PRIORITY_LINKS_DB = CHANNEL_PRIORITY_MAX;
   /**
    * Archive priority.
    */
   short CHANNEL_PRIORITY_ARCHIVE = (CHANNEL_PRIORITY_MAX + CHANNEL_PRIORITY_MIN) / 2;
   /**
    * OPI priority.
    */
   short CHANNEL_PRIORITY_OPI = CHANNEL_PRIORITY_MIN;


   /* -------- Core CA constants -------- */

   /**
    * CA protocol major revision (implemented by this library).
    */
   short CA_MAJOR_PROTOCOL_REVISION = 4;

   /**
    * CA protocol minor revision (implemented by this library).
    */
   short CA_MINOR_PROTOCOL_REVISION = 13;

   /**
    * Unknown CA protocol minor revision.
    */
   short CA_UNKNOWN_MINOR_PROTOCOL_REVISION = 0;

   /**
    * CA protocol port base.
    */
   int CA_PORT_BASE = 5056;

   /**
    * Default CA server port.
    */
   int CA_SERVER_PORT = CA_PORT_BASE + 2 * CA_MAJOR_PROTOCOL_REVISION;

   /**
    * Default CA repeater port.
    */
   int CA_REPEATER_PORT = CA_PORT_BASE + 2 * CA_MAJOR_PROTOCOL_REVISION + 1;


   /**
    * CA Repeater attempted registration interval in seconds.
    */
    int CA_REPEATER_REGISTRATION_INTERVAL = 60;

   /**
    * CA protocol message header size.
    */
   short CA_MESSAGE_HEADER_SIZE = 16;

   /**
    * CA protocol message extended header size.
    */
   short CA_EXTENDED_MESSAGE_HEADER_SIZE = CA_MESSAGE_HEADER_SIZE + 8;

   /**
    * UDP maximum send message size.
    * MAX_UDP: 1500 (max of Ethernet and 802.{2,3} MTU) - 20(IP) - 8(UDP)
    * (the MTU of Ethernet is currently independent of its speed variant)
    */
   int MAX_UDP_SEND = 1024;

   /**
    * UDP maximum receive message size.
    */
   int MAX_UDP_RECV = 0xFFFF + 16;

   /**
    * TCP maximum receive message size.
    */
   int MAX_TCP_RECV = 1024 * 16 + CA_EXTENDED_MESSAGE_HEADER_SIZE;

   /**
    * Default priority (corresponds to POSIX SCHED_OTHER)
    */
   short CA_DEFAULT_PRIORITY = 0;

   /**
    * Read access right mask.
    */
   int CA_PROTO_ACCESS_RIGHT_READ = 1 << 0;

   /**
    * Write access right mask.
    */
   int CA_PROTO_ACCESS_RIGHT_WRITE = 1 << 1;

   /**
    * Do not require response for CA search request.
    */
   short CA_SEARCH_DONTREPLY = 5;

   /**
    * Require response (even if not found) for CA search request over TCP.
    */
   short CA_SEARCH_DOREPLY = 10;

   /**
    * Echo (state-of-health message) response timeout in ms.
    */
   long CA_ECHO_TIMEOUT = 5000;

   /**
    * Max. (requested) string size.
    */
   int MAX_STRING_SIZE = 40;

   /**
    * Unreasonable channel name length.
    */
   int UNREASONABLE_CHANNEL_NAME_LENGTH = 500;

}
