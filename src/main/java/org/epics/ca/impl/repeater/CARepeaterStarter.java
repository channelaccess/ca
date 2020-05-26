/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/
/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.epics.ca.Constants;
import org.epics.ca.util.logging.LibraryLogManager;

import java.io.*;
import java.net.*;
import java.util.*;

import java.util.concurrent.atomic.AtomicReference;
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

   private static final AtomicReference<Process> lastStartedProcess =  new AtomicReference<>();

   public static void shutdownProcessStreamConsumer()
   {

   }

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
      final int fallbackRepeaterPort = parseToInt( System.getProperty( "EPICS_CA_REPEATER_PORT" ), Constants.CA_REPEATER_PORT );
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
         {
            logger.info( "Creating CA Repeater instance which will run on port " + port + "." );
            repeater = new CARepeater( port );
         }
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

   /**
    * Starts the CA Repeater in a separate JVM process, if it is not already
    * running.
    *
    * @param repeaterPort specifies the port to listen on in the inclusive range 1-65535.
    * @param debugEnable whether debug logging should be enabled in the spawned
    *    subprocess.
    * @param outputCaptureEnable whether the standard output and standard error of
    *   the spawned subprocess should be captured and sent to the logging system in
    *   the owning process.
    * @throws IllegalArgumentException if the repeater port is out of range.
    * @throws IllegalStateException if some other unexpected condition prevents
    *    repeater from being started.
    * @throws RuntimeException if the JVM network stack was incorrectly configured.
    */
   public static void startRepeaterIfNotAlreadyRunning( int repeaterPort, boolean debugEnable, boolean outputCaptureEnable )
   {
      Validate.inclusiveBetween( 1, 65535, repeaterPort, "The port must be in the inclusive range 1-65535.");

      if( ! NetworkUtilities.verifyTargetPlatformNetworkStackIsChannelAccessCompatible() )
      {
         throw new RuntimeException( "The network stack is incorrectly configured for EPICS CA protocol." );
      }

      synchronized ( CARepeaterStarter.class  )
      {
         if ( ! isRepeaterRunning( repeaterPort ) )
         {
            final Process process = startRepeaterInSeparateJvmProcess( repeaterPort, debugEnable, outputCaptureEnable );
            lastStartedProcess.set( process );
         }
      }
   }

   /**
    * Shuts down the last repeater that this class started (if any)
    */
   public static void shutdownLastStartedRepeater()
   {
      logger.fine( "Shutting down any CA Repeaters that were started by this process..." );

      if ( lastStartedProcess.get() != null )
      {
         logger.fine( "A CA Repeater was found." );
         lastStartedProcess.get().destroyForcibly();
         logger.fine( "The CA Repeater was sent a termination signal." );
      }
      else
      {
         logger.fine( "There are NO CA Repeaters that need terminating." );
      }
   }


/*- Package-level access methods ---------------------------------------------*/

   /**
    * Starts the CA Repeater, which should not already be running, in a separate
    * JVM process. Returns the process handle which can subsequently be used
    * to monitor the lifecycle of the process.
    *
    * @param repeaterPort specifies the port that the CA Repeater will listen on
    *    in the range 1-65535.
    * @param debugEnable whether debug logging should be enabled in the spawned
    *    subprocess.
    * @param outputCaptureEnable whether the standard output and standard error of
    *   the spawned subprocess should be captured and sent to the logging system in
    *   the owning process.
    * @return process handle which can subsequently be used to monitor the process
    *    and/or shut it down.
    * @throws IllegalArgumentException if the repeater port is out of range.
    * @throws IllegalStateException if the repeater was already running.
    * @throws RuntimeException if some other unexpected condition prevents
    *    repeater from being started.
    */
   static Process startRepeaterInSeparateJvmProcess( int repeaterPort, boolean debugEnable, boolean outputCaptureEnable )
   {
      Validate.inclusiveBetween( 1, 65535, repeaterPort, "The port must be in the inclusive range 1-65535.");
      Validate.validState( ! isRepeaterRunning( repeaterPort ), "The repeater is already running on port." + repeaterPort );

      logger.fine( "Starting the repeater in a separate process..." );

      // Make an entry in the log of all local IPV4 network addresses.
      logger.info( "The following local interfaces have been discovered..." ) ;
      final List<Inet4Address> addrList = NetworkUtilities.getLocalNetworkInterfaceAddresses();
      addrList.forEach( (ip) -> logger.info( ip.toString() ) );

      final String classPath = System.getProperty("java.class.path", "<java.class.path not found>");
      final String classWithMainMethod = CARepeaterStarter.class.getName();
      final String repeaterPortAsString = Integer.toString( repeaterPort );

      final List<String> commandLine = Arrays.asList( "java",
                                                      "-cp", classPath,
                                                      "-DCA_DEBUG=" + debugEnable,
                                                      "-Djava.net.preferIPv4Stack=true",
                                                      "-Djava.net.preferIPv6Stack=false",
                                                      classWithMainMethod,
                                                      "-p", repeaterPortAsString );

      try
      {
         // Spawn a new CA Repeater as a child of the existing process.
         logger.finest( "Attempting to run CA Repeater in separate process using command line: '" + commandLine + "'." );

         final Process process;
         if ( outputCaptureEnable  )
         {
            logger.finest( "The output from the CA Repeater will be captured in the log." );
            process = new ProcessBuilder().command( commandLine )
                                          .start();
            ProcessStreamConsumer.consumeFrom( process );
         }
         else
         {
            logger.finest( "The output from the CA Repeater will NOT be captured in the log." );
            process = new ProcessBuilder().command( commandLine )
                                          .redirectError( ProcessBuilder.Redirect.to( NULL_FILE ) )
                                          .redirectOutput( ProcessBuilder.Redirect.to( NULL_FILE ) )
                                          .redirectInput( ProcessBuilder.Redirect.from( NULL_FILE ) )
                                          .start();
         }

         logger.finest( "The process was started OK." );
         return process;
      }
      catch ( IOException ex )
      {
         final String message = "Failed to run '" + commandLine + "' in separate process.";
         logger.log( Level.WARNING, message, ex );
         throw new RuntimeException( message, ex );
      }
   }

   /**
    * Check if a repeater is running bound to any of the addresses associated
    * with the local machine.
    *
    * @param repeaterPort repeater port.
    * @return <code>true</code> if repeater is already running, <code>false</code> otherwise
    */
   static boolean isRepeaterRunning( int repeaterPort )
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
