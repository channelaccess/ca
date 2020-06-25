/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.epics.ca.impl.JavaProcessManager;
import org.epics.ca.impl.LibraryConfiguration;
import org.epics.ca.util.logging.LibraryLogManager;

import java.net.*;
import java.util.*;

import java.util.logging.Level;
import java.util.logging.Logger;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Provides a service for starting and shutting down CA Repeater service
 * instances on the specified port.
 */
class CARepeaterServiceInstance
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( CARepeaterServiceInstance.class );
   private final int port;
   private JavaProcessManager processManager;


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Constructs a new service instance which will operate on the specified port.
    * @param port the port to operate on.
    */
   CARepeaterServiceInstance( int port )
   {
      this.port = port;
   }

/*- Public methods -----------------------------------------------------------*/

   // Instances of this class are intended to be stored in collections.
   // An instance is considered equal to another instance if the ports match.
   // The state of the processManager is NOT considered.
   @Override
   public boolean equals( Object o )
   {
      if ( this == o )
      {
         return true;
      }
      if ( !(o instanceof CARepeaterServiceInstance) )
      {
         return false;
      }
      CARepeaterServiceInstance that = (CARepeaterServiceInstance) o;
      return port == that.port;
   }

   @Override
   public int hashCode()
   {
      return Objects.hash( port );
   }


/*- Package-level access methods ---------------------------------------------*/

   /**
    * Returns the port on which this service instance is running.
    * @return the port.
    */
   int getPort()
   {
      return port;
   }

   /**
    * Attempts to start this service instance, handling any conditions that
    * prevent startup internally, but sending this information to the log.
    */
   void start()
   {
      if( ! LibraryConfiguration.getInstance().isRepeaterEnabled() )
      {
         logger.warning( "The CA Repeater Service configuration is set to disabled." );
         return;
      }

      if ( CARepeaterServiceManager.isRepeaterRunning( port ) )
      {
         logger.warning( "The CA Repeater is already running on port." + port );
         logger.warning( "Possibly it was started previously by another process." );
         return;
      }

      if ( processManager != null )
      {
         logger.severe( "The CA Repeater is already running on port." + port );
         logger.severe( "This is an unexpected condition which suggests a programming error." );
         return;
      }

      try
      {
         final Level logLevel = LibraryConfiguration.getInstance().getRepeaterLogLevel();
         final boolean outputCaptureEnable = LibraryConfiguration.getInstance().isRepeaterOutputCaptureEnabled();
         processManager = startRepeater( port, logLevel, outputCaptureEnable );
      }
      catch ( RuntimeException ex )
      {
         logger.warning( "The CA Repeater on port " + port + " failed to start." );
      }
   }

   /**
    * Attempts to shutdown this service instance, handling any conditions that
    * prevent shutdown internally, but sending this information to the log.
    */
   void shutdown()
   {
      if ( ! LibraryConfiguration.getInstance().isRepeaterEnabled() )
      {
         logger.warning( "The CA Repeater Service configuration is set to disabled." );
         return;
      }

      if ( ! CARepeaterServiceManager.isRepeaterRunning( port ) )
      {
         logger.warning( "The CA Repeater is NOT already running on port." + port );
      }

      if ( processManager == null )
      {
         logger.severe( "The CA Repeater was never started on port." + port );
         logger.severe( "This is an unexpected condition which suggests a programming error." );
         return;
      }

      if ( ! processManager.isAlive() )
      {
         logger.warning( "The CA Repeater on port " + port + " has already died." );
      }

      if( ! processManager.shutdown() )
      {
         logger.warning( "The CA Repeater on port " + port + " failed to shutdown." );
      }
      processManager = null;
   }

   /**
    * Returns true when the process associated with this manager instance is still
    * alive.
    *
    * @return the result.
    */
   boolean isProcessAlive()
   {
      if ( processManager == null )
      {
         return false;
      }
      else
      {
         return processManager.isAlive();
      }
   }


/*- Private methods ----------------------------------------------------------*/

   /**
    * Starts the CA Repeater, which should not already be running, in a separate
    * JVM process. Returns the process handle which can subsequently be used
    * to monitor the lifecycle of the process.
    *
    * @param repeaterPort        specifies the port that the CA Repeater will listen on
    *                            in the range 1-65535.
    * @param logLevel            the logging level to be used for the spawned process.
    * @param outputCaptureEnable whether the standard output and standard error of
    *                            the spawned subprocess should be captured and sent to the logging system in
    *                            the owning process.
    * @return process handle which can subsequently be used to monitor the process
    * and/or shut it down.
    * @throws IllegalArgumentException if the repeater port is out of range.
    * @throws NullPointerException     if the logLevel argument was null.
    * @throws IllegalStateException    if the repeater was already running.
    * @throws RuntimeException         if some other unexpected condition prevents
    *                                  repeater from being started.
    */
   private static JavaProcessManager startRepeater( int repeaterPort, Level logLevel, boolean outputCaptureEnable )
   {
      Validate.notNull( logLevel );
      Validate.inclusiveBetween( 1, 65535, repeaterPort, "The port must be in the inclusive range 1-65535." );
      Validate.validState( ! CARepeaterServiceManager.isRepeaterRunning( repeaterPort ), "The repeater is already running on port " + repeaterPort );

      logger.fine( "Starting the repeater in a separate process..." );

      // Make an entry in the log of all local IPV4 network addresses.
      logger.fine( "The following local interfaces have been discovered..." );
      final List<Inet4Address> addrList = NetworkUtilities.getLocalNetworkInterfaceAddresses();
      addrList.forEach( ( ip ) -> logger.fine( ip.toString() ) );

      final Properties properties = new Properties();
      properties.put( "java.net.preferIPv4Stack", "true" );
      properties.put( "java.net.preferIPv6Stack", "false" );
      properties.put( "CA_LIBRARY_LOG_LEVEL", logLevel.toString() );
      final String repeaterPortAsString = Integer.toString( repeaterPort );
      final String[] programArgs = new String[] { "-p", repeaterPortAsString };
      final JavaProcessManager processManager = new JavaProcessManager( CARepeater.class, properties, programArgs );
      processManager.start( outputCaptureEnable );
      return processManager;
   }

   /**
    * Shuts down the CA Repeater which was previously started by the supplied Java
    * process manager.
    *
    * @param repeaterProcessManager the process manager.
    * @throws NullPointerException if the repeaterProcessManager argument was null.
    */
   private static void stopRepeater( JavaProcessManager repeaterProcessManager )
   {
      Validate.notNull( repeaterProcessManager );
      if ( repeaterProcessManager.isAlive() )
      {
         logger.fine( "Sending the CA Repeater a termination signal..." );
         repeaterProcessManager.shutdown();
         logger.fine( "The CA Repeater was sent a termination signal." );
      }
      else
      {
         logger.fine( "The CA Repeater process was no longer alive => nothing to do." );
      }
   }

/*- Nested Classes -----------------------------------------------------------*/

}
