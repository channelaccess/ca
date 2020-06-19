/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/
/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.epics.ca.impl.JavaProcessManager;
import org.epics.ca.impl.LibraryConfiguration;
import org.epics.ca.impl.ProtocolConfiguration;
import org.epics.ca.util.logging.LibraryLogManager;

import java.io.*;
import java.net.*;
import java.util.*;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides mechanisms for starting the CA Repeater either directly from
 * the command-line or in a separate JVM process via a library call.
 */
public class CARepeaterStarter
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( CARepeaterStarter.class );
   private static final File NULL_FILE = new File( ( System.getProperty("os.name").startsWith( "Windows") ? "NUL" : "/dev/null" ) );

   private static final Map<Integer,JavaProcessManager> repeaterProcessMap = new HashMap<>();
   private static final Map<Integer,Integer> repeaterInterestMap = new HashMap<>();

/*- Main ---------------------------------------------------------------------*/

   /**
    * Starts the CA Repeater from the command line.
    *
    * If at least two arguments are provided and the first argument is "-p"
    * or "--port" then an attempt will be made to parse the second argument
    * as the port number to use when starting the repeater.
    *
    * If the second argument is a valid port number (positive integer) then
    * the repeater will be started on that port. If it isn't then the
    * repeater will be started on the fallback repeater port.
    *
    * The fallback repeater port is the EPICS default repeater port (which
    * depends on the CA Protocol Version) or the value specified by the
    * system property 'EPICS_CA_REPEATER_PORT'.
    *
    * The default repeater port depends on the CA Protocol Version.
    * For the version currently supported by this library CA Version 4.13
    * the default repeater port is 5065.
    *
    * @param argv arguments.
    */
   public static void main( String[] argv )
   {
      if( ! NetworkUtilities.verifyTargetPlatformNetworkStackIsChannelAccessCompatible() )
      {
         System.exit( 128 );
      }

      logger.info( "The CA Repeater main method has been invoked with " + argv.length + " arguments." );
      logger.info( "The arguments were: " + Arrays.toString( argv ) );

      final boolean portArgumentSupplied = ( argv.length >= 2 && ( argv[ 0 ].equals ("-p") || argv[ 0 ].equals ("--port") ) );
      final int fallbackRepeaterPort = new ProtocolConfiguration().getRepeaterPort();
      final int port = portArgumentSupplied ? parseToInt( argv[ 1 ], fallbackRepeaterPort ) : fallbackRepeaterPort;

      // Nothing to do, if a repeater instance is already running
      if ( CARepeaterStarter.isRepeaterRunning( port ) )
      {
         logger.info( "The repeater is already running and a new instance will not be started." );
         logger.info( "This process will now terminate." );
         System.exit( 129 );
      }

      final CARepeater repeater;
      try
      {
         logger.info( "Creating CA Repeater instance which will run on port " + port + "." );
         repeater = new CARepeater( port );
      }
      catch( CARepeater.CaRepeaterStartupException ex )
      {
         logger.warning( "An exception occurred when attempting to start the repeater." );
         logger.warning( "The exception message was: " + ex.getMessage() );
         System.exit( 130 );
         return;
      }

      // Run, run, run...
      repeater.start();
   }


/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/

   public static void startRepeaterOnPort( int repeaterPort )
   {
      synchronized( CARepeaterStarter.class )
      {
         logger.info( "Processing request to start CA Repeater on port: '" + repeaterPort + "'." );

         if ( ! LibraryConfiguration.getInstance().isRepeaterEnabled() )
         {
            logger.info( "The CA Repeater lifecycle management is disabled => nothing to do." );
            return;
         }

//         if ( isRepeaterRunning( repeaterPort ) )
//         {
//            logger.warning( "The CA Repeater is already running on port: '" + repeaterPort + "' => nothing to do." );
//            return;
//         }

         if ( ! repeaterInterestMap.containsKey( repeaterPort ) )
         {
            final Level logLevel = LibraryConfiguration.getInstance().getRepeaterLogLevel();
            final boolean outputCaptureEnable = LibraryConfiguration.getInstance().isRepeaterOutputCaptureEnabled();
            final JavaProcessManager repeaterProcessManager = startRepeater( repeaterPort, logLevel, outputCaptureEnable );
            repeaterProcessMap.put( repeaterPort, repeaterProcessManager );
            repeaterInterestMap.put( repeaterPort, 1 );
            return;
         }

         final int currentInterestLevel = repeaterInterestMap.get( repeaterPort );
         logger.info( "Increasing interest level in the repeater running on port: '" + repeaterPort + "'." );
         repeaterInterestMap.put( repeaterPort, currentInterestLevel + 1 );
      }
   }

   public static void stopRepeaterOnPort( int repeaterPort )
   {
      synchronized( CARepeaterStarter.class )
      {
         logger.info("Processing request to stop CA Repeater on port: '" + repeaterPort + "'.");

         if ( ! LibraryConfiguration.getInstance().isRepeaterEnabled() )
         {
            logger.info("The CA Repeater lifecycle management is disabled => nothing to do.");
            return;
         }

//         if ( ! isRepeaterRunning( repeaterPort ) )
//         {
//            logger.info("The local repeater is NOT running on port: '" + repeaterPort + "' => nothing to do.");
//            return;
//         }

//         if ( ! repeaterProcessMap.containsKey( repeaterPort ) )
//         {
//            logger.warning("There was no CA Repeater instance running on port: '" + repeaterPort + "' that was started by the current JVM instance => nothing to do.");
//            return;
//         }

         if ( repeaterInterestMap.get( repeaterPort) == 0 )
         {
            logger.warning("Attempt to shutdown a repeater on port '" + repeaterPort + " for which there was already no expressed interest => nothing to do.");
            return;
         }

         final int currentInterestLevel = repeaterInterestMap.get( repeaterPort );
         if ( currentInterestLevel == 1 )
         {
            logger.info("Shutting down the repeater running on port: '" + repeaterPort + "'.");
            final JavaProcessManager repeaterProcessManager = repeaterProcessMap.get( repeaterPort );
            stopRepeater( repeaterProcessManager );
            repeaterInterestMap.remove( repeaterPort );
            return;
         }

         logger.info("Reducing interest level in the repeater running on port: '" + repeaterPort + "'.");
         repeaterInterestMap.put( repeaterPort, currentInterestLevel - 1);
      }
   }

   /**
    * Check if a repeater is running bound to any of the addresses associated
    * with the local machine.
    *
    * @param repeaterPort repeater port.
    * @return <code>true</code> if repeater is already running, <code>false</code> otherwise
    */
   public static boolean isRepeaterRunning( int repeaterPort )
   {
      logger.finest( "Checking whether repeater is running on port " + repeaterPort );

      // The idea here is that binding to a port on thewildcard addreess we check all
      // possible network interfaces on which the CA Repeater may be already running.
      final InetSocketAddress wildcardSocketAddress = new InetSocketAddress( repeaterPort );
      logger.finest( "Checking socket address " + wildcardSocketAddress );
      final boolean repeaterIsRunning = ! UdpSocketUtilities.isSocketAvailable( wildcardSocketAddress );
      final String runningOrNot = repeaterIsRunning ? " IS " : " IS NOT ";
      logger.finest( "The repeater on port " + repeaterPort + runningOrNot + "running." ) ;
      return repeaterIsRunning;
   }

/*- Package-level access methods ---------------------------------------------*/

   /**
    * Starts the CA Repeater, which should not already be running, in a separate
    * JVM process. Returns the process handle which can subsequently be used
    * to monitor the lifecycle of the process.
    *
    * @param repeaterPort specifies the port that the CA Repeater will listen on
    *    in the range 1-65535.
    * @param debugLevel the debug level to be used for logging in the spawned process.
    * @param outputCaptureEnable whether the standard output and standard error of
    *   the spawned subprocess should be captured and sent to the logging system in
    *   the owning process.
    * @return process handle which can subsequently be used to monitor the process
    *    and/or shut it down.
    * @throws IllegalArgumentException if the repeater port is out of range.
    * @throws NullPointerException if the debugLevel argument was null.
    * @throws IllegalStateException if the repeater was already running.
    * @throws RuntimeException if some other unexpected condition prevents
    *    repeater from being started.
    */
   static JavaProcessManager startRepeater( int repeaterPort, Level debugLevel, boolean outputCaptureEnable )
   {
      Validate.notNull( debugLevel );
      Validate.inclusiveBetween( 1, 65535, repeaterPort, "The port must be in the inclusive range 1-65535.");
      Validate.validState( ! isRepeaterRunning( repeaterPort ), "The repeater is already running on port." + repeaterPort );

      logger.fine( "Starting the repeater in a separate process..." );

      // Make an entry in the log of all local IPV4 network addresses.
      logger.info( "The following local interfaces have been discovered..." ) ;
      final List<Inet4Address> addrList = NetworkUtilities.getLocalNetworkInterfaceAddresses();
      addrList.forEach( (ip) -> logger.info( ip.toString() ) );
      
      final Properties properties = new Properties();
      properties.put( "java.net.preferIPv4Stack", "true" );
      properties.put( "java.net.preferIPv6Stack", "false" );
      properties.put( "CA_LIBRARY_LOG_LEVEL", debugLevel.toString() );
      final String repeaterPortAsString = Integer.toString( repeaterPort );
      final String[] programArgs = new String[] { "-p", repeaterPortAsString };
      final JavaProcessManager processManager = new JavaProcessManager( CARepeaterStarter.class, properties, programArgs );
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
   static void stopRepeater( JavaProcessManager repeaterProcessManager )
   {
      Validate.notNull( repeaterProcessManager );
      if(  repeaterProcessManager.isAlive() )
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

/*- Private methods ----------------------------------------------------------*/

   /**
    * Attempts to interpret the supplied string as an integer, returning
    * the result if successful or otherwise some default value.
    *
    * @param stringToParse the input string.
    * @param defaultValue the value to be returned if the  input string cannot
    *    be parsed.
    *
    * @return the result
    */
   private static int parseToInt( String stringToParse, int defaultValue )
   {
      int ret;
      try
      {
         ret = Integer.parseInt( stringToParse );
      }
      catch( NumberFormatException ex)
      {
         ret = defaultValue; //Use default value if parsing failed
      }
      return ret;
   }


/*- Nested Classes -----------------------------------------------------------*/

}
