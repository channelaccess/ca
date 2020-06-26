/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.epics.ca.impl.JavaProcessManager;
import org.epics.ca.impl.LibraryConfiguration;
import org.epics.ca.util.logging.LibraryLogManager;

import java.net.*;
import java.util.*;

import java.util.concurrent.atomic.AtomicBoolean;
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
   private final JavaProcessManager processManager;
   private final AtomicBoolean activated;
   private final AtomicBoolean shutdown;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Constructs a new service instance which will operate on the specified port.
    * @param port the port to operate on.
    */
   CARepeaterServiceInstance( int port )
   {
      this.port = port;
      final Level logLevel = LibraryConfiguration.getInstance().getRepeaterLogLevel();
      this.processManager = createProcessManager( port, logLevel );
      this.activated = new AtomicBoolean( false );
      this.shutdown = new AtomicBoolean( false );
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
    * Returns the port associated with this service instance.
    *
    * @return the port.
    */
   int getPort()
   {
      return port;
   }

   /**
    * Activates this service instance, initiating the necessary steps to determine whether
    * a new CA Repeater process should be spawned and logging any situations that might
    * prevent that from happening.
    *
    * Note: activation is a one-off event. Attempts to activate a service instance
    * which is already activated will result in an exception.
    *
    * @throws IllegalStateException if the service instance was already activated.
    */
   void activate()
   {
      Validate.validState( ! activated.getAndSet( true ), "This service instance was already activated." );

      if( ! LibraryConfiguration.getInstance().isRepeaterEnabled() )
      {
         logger.info( "The CA Repeater Service configuration is set to disabled." );
         return;
      }

      if ( CARepeaterServiceManager.isRepeaterRunning( port ) )
      {
         logger.info( "The CA Repeater is already running on port " + port + "." );
         logger.info( "Possibly it was started previously by another operating system process." );
         return;
      }

      try
      {
         final boolean outputCaptureEnable = LibraryConfiguration.getInstance().isRepeaterOutputCaptureEnabled();
         start( outputCaptureEnable );
      }
      catch ( RuntimeException ex )
      {
         logger.warning( "The CA Repeater on port " + port + " failed to start." );
      }
   }

   /**
    * Shuts down this service instance, initiating the necessary steps to determine
    * whether any existing CA Repeater process should be killed and logging any
    * situations that might prevent that from happening.
    *
    * Note: shutdown is a one-off event. Attempts to shutdown a service instance
    * which is already shutdown will result in an exception.
    *
    * @throws IllegalStateException if the service instance was already shutdown.
    */
   void shutdown()
   {
      Validate.validState( activated.get(), "This service instance was not previously activated." );
      Validate.validState( ! shutdown.getAndSet( true ), "This service instance was already shut down." );

      if ( ! LibraryConfiguration.getInstance().isRepeaterEnabled() )
      {
         logger.info( "The CA Repeater Service configuration is set to disabled." );
         return;
      }

      if ( ! CARepeaterServiceManager.isRepeaterRunning( port ) )
      {
         logger.info( "The CA Repeater is not running on port " + port + "." );
         return;
      }

      if ( ! processManager.isAlive() )
      {
         logger.info( "The CA Repeater that is running on port " + port + " was not started by this service instance." );
         logger.info( "Possibly it was started previously by another operating system process." );
         return;
      }

      try
      {
         stopRepeater(  processManager );
      }
      catch ( RuntimeException ex )
      {
         logger.warning( "The CA Repeater on port " + port + " failed to shut down." );
      }
   }

   /**
    * Returns true when the process associated with this manager instance is still
    * alive.
    *
    * @return the result.
    */
   boolean isProcessAlive()
   {
      return processManager.isAlive();
   }

/*- Private methods ----------------------------------------------------------*/

   /**
    * Returns a process manager which can be used to spawn a CA Repeater process
    * and to monitor and manage its subsequent lifecycle.
    *
    * @param repeaterPort specifies the port that the CA Repeater will listen on
    *    in the range 1-65535.
    * @param logLevel the logging level to be used for the spawned process.

    * @return process handle which can subsequently be used to monitor the
    *    process and/or shut it down.
    * @throws IllegalArgumentException if the repeater port is out of range.
    * @throws NullPointerException if the logLevel argument was null.
    */
   private JavaProcessManager createProcessManager( int repeaterPort, Level logLevel )
   {
      Validate.notNull( logLevel );
      Validate.inclusiveBetween( 1, 65535, repeaterPort, "The port must be in the inclusive range 1-65535." );

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

      return new JavaProcessManager( CARepeater.class, properties, programArgs );
   }

   /**
    * Starts the CA Repeater process and waits a predefined delay before
    * verifying that the repeater is now running.
    *
    * @param outputCaptureEnable whether the standard output and standard error of
    *    the spawned process should be captured and sent to the logging system in
    *    the owning process.
    *
    * @throws RuntimeException if some unexpected condition prevented the
    *    repeater from being started.
    */
   private void start( boolean outputCaptureEnable )
   {
      processManager.start( outputCaptureEnable );

      try
      {
         Thread.sleep( REPEATER_STARTUP_DELAY );
      }
      catch ( InterruptedException e )
      {
         logger.warning( "Interrupted whilst waiting for CA Repeater to start running." );
         Thread.currentThread().interrupt();
      }

      if ( CARepeaterServiceManager.isRepeaterRunning( port ) )
      {
         logger.info( "The CA Repeater started ok." );
      }
      else
      {
         logger.warning( "The CA Repeater failed to start." );
      }
   }


   /**
    * Shuts down the CA Repeater which was previously started by the supplied Java
    * process manager.
    *
    * @param repeaterProcessManager the process manager.
    * @throws NullPointerException if the repeaterProcessManager argument was null.
    */
   private void stopRepeater( JavaProcessManager repeaterProcessManager )
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
         return;
      }

      try
      {
         Thread.sleep( REPEATER_SHUTDOWN_DELAY );
      }
      catch ( InterruptedException e )
      {
         logger.warning( "Interrupted whilst waiting for CA Repeater to start running." );
         Thread.currentThread().interrupt();
      }

      if ( CARepeaterServiceManager.isRepeaterRunning( port ) )
      {
         logger.warning( "The CA Repeater failed to shutdown." );
      }
      else
      {
         logger.info( "The CA Repeater was shutdown ok." );
      }
   }

/*- Nested Classes -----------------------------------------------------------*/

}
