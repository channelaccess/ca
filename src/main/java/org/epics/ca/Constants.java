package org.epics.ca;

import org.epics.ca.impl.monitor.MonitorNotificationServiceFactoryCreator;

public interface Constants
{
   enum ChannelProperties
   {
      nativeType, nativeTypeCode, remoteAddress, nativeElementCount
   }

   /**
    * String value of the JVM property key which specifies the minimum level of the log messages
    * that the CA library will send to the console.
    * @see java.util.logging.Level
    */
   String CA_LIBRARY_LOG_LEVEL = "CA_LIBRARY_LOG_LEVEL";

   /**
    * String value of the JVM property key which specifies whether to strip the hostname returned by
    * InetAddress.getLocalHost().getHostName().
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
    * String value of the JVM property key which specifies the minimum level of the log messages that the CA Repeater
    * will send to the console. This property is only considered when the <code>CA_REPEATER_OUTPUT_CAPTURE</code>
    * JBM property is set to true.
    * @see java.util.logging.Level
    */
   String CA_REPEATER_LOG_LEVEL = "CA_REPEATER_LOG_LEVEL";

   /**
    * Defines the value of the JVM property key which specify whether the output of the CA Repeater should be
    * captured in the CA library log.
    */
   String CA_REPEATER_OUTPUT_CAPTURE = "CA_REPEATER_OUTPUT_CAPTURE";

   /**
    * Defines the value of a JVM property key which specifies whether an attempt should be made to
    * ensure that a CA Repeater has been spawned and is running when a CA library context is created.
    */
   String CA_REPEATER_START_ON_CONTEXT_OPEN = "CA_REPEATER_START_ON_CONTEXT_OPEN";

   /**
    * Defines the value of the JVM property key which specifies whether an attempt should be made to
    * shutdown any spawned CA Repeater when the last remaining CA library context is closed.
    */
   String CA_REPEATER_SHUTDOWN_ON_CONTEXT_CLOSE = "CA_REPEATER_SHUTDOWN_ON_CONTEXT_CLOSE";


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
    * Initial delay in milliseconds between creation of a new Context and the
    * first attempt to register with the CA Repeater.
    */
   int CA_REPEATER_INITIAL_REGISTRATION_DELAY = 500;

   /**
    * CA Repeater attempted registration interval in milliseconds.
    */
    int CA_REPEATER_REGISTRATION_INTERVAL = 60_000;

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
   int CA_PROTO_ACCESS_RIGHT_READ = 1;

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
