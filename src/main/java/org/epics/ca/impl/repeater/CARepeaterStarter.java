/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/
/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Starts the CA repeater in a separate JVM instance.
 */
public class CARepeaterStarter
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

private static final Logger logger = Logger.getLogger( CARepeaterStarter.class.getName () );

static
{
   // force only IPv4 sockets, since EPICS does not work right with IPv6 sockets
   // see http://java.sun.com/j2se/1.5.0/docs/guide/net/properties.html
   System.setProperty ( "java.net.preferIPv4Stack", "true" );
   CARepeaterUtils.initializeLogger( logger );
}

/**
 * System JVM property key to disable CA repeater.
 */
public static final String CA_DISABLE_REPEATER = "CA_DISABLE_REPEATER";


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/

   /**
    * Attempts to starts the CA Repeater in a separate JVM instance if it is
    * not already running.
    *
    * If the CA Repeater is already running then this method simply returns
    * without taking any further action.
    *
    * @param repeaterPort positive integer specifying the port to run it on.
    *
    * @throws IllegalArgumentException if the repeater port is invalid.
    * @throws IllegalStateException if an attempt was made to start the
    *    repeater but for some reason this was not possible. In these
    *    circumstances further information is available in the log.
    */
   public static void spawnRepeaterIfNotAlreadyRunning( final int repeaterPort )
   {
      logger.finest( "Starting the repeater..." );

      // Validate input argument.
      if ( repeaterPort < 1 )
      {
         final String msg = "The repeater port must be a positive integer";
         logger.warning( msg);
         throw new IllegalArgumentException( msg );
      }

      // Nothing to do, if a repeater instance is already running
      logger.finest( "Checking whether the repeater is already running..." );
      if ( isRepeaterRunning( repeaterPort ) )
      {
         logger.info( "The repeater is already running and will not be started." );
         return;
      }
      logger.finest( "The repeater is NOT already running so will be started now. " );

      // Define a Privileged action which will start a separate JVM to run the repeater.
      final PrivilegedAction<Optional<Throwable>> action = () ->
      {
         logger.finest( "Building CA Repeater command line..." );

         // java.home java.class.path
         final String[] commandLine = new String[] {
               System.getProperty ("java.home") + File.separator + "bin" + File.separator + "java",
               "-classpath",
               System.getProperty( "java.class.path", "<java.class.path not found>"),
               CARepeater.class.getName (),
               "-p",
               String.valueOf( repeaterPort )
         };

         try
         {
            logger.finest( "Attempting to run CA Repeater in separate process..." );
            logger.finest( "Command Line: '" + Arrays.toString( commandLine ) + "'" );
            final Process process = Runtime.getRuntime().exec( commandLine );
            logger.finest( "Process was started." );
         }
         catch ( IOException ex )
         {
            logger.log( Level.WARNING, "Failed to run '" + commandLine[ 0 ] + "' in separate process.", ex );
            return Optional.of( ex );
         }
         return Optional.empty();
      };

      // Now run the privileged action
      final Optional<Throwable> optionalResult = AccessController.doPrivileged(action );
      if ( optionalResult.isPresent() )
      {
         final String msg = "Unable to start the CA Repeater. See the log for further details.";
         logger.info( msg );
         throw new IllegalStateException( msg, optionalResult.get() );
      }

      logger.info( "The CA repeater was started on the local machine on port " + repeaterPort  + "." ) ;
   }

/*- Package-level access methods ---------------------------------------------*/

   /**
    * Check if a repeater is running bound to the wildcard instance on the local machine.
    *
    * @param repeaterPort repeater port.
    * @return <code>true</code> if repeater is already running, <code>false</code> otherwise
    */
   static boolean isRepeaterRunning( int repeaterPort )
   {
      logger.log( Level.INFO, "Checking whether repeater is running on port " + repeaterPort );
      final InetSocketAddress wildcardSocketAddress = new InetSocketAddress( repeaterPort );
      final boolean repeaterIsRunning = ! SocketUtilities.isSocketAvailable( wildcardSocketAddress );
      final String runningOrNot = repeaterIsRunning ? " IS " : " is NOT ";
      logger.info( "The repeater on port " + repeaterPort + runningOrNot + "running." ) ;
      return repeaterIsRunning;
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
