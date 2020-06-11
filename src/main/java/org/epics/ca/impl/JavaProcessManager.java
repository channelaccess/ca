/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.epics.ca.util.logging.LibraryLogManager;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;


/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Provides the ability to spawn JVM instances which execute a user-specified
 * Java class with user-specified system properties and arguments.
 */
public class JavaProcessManager
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( JavaProcessManager.class );

   private static final File NULL_FILE = new File (( System.getProperty ("os.name").startsWith( "Windows" ) ? "NUL" : "/dev/null" ) );

   private final Class<?> classWithMainMethod;
   private final String[] programArgs;
   private final Properties systemProperties;
   private final String classPath;

   private Process process;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Constructs a new instance.
    *
    * @param classWithMainMethod the Java class which contains the main method
    *   to be executed.
    * @param systemProperties the system properties to be set for the spawned
    *   process. As a minimum this must contain a definition for the class
    *   path ("java.class.path").
    * @param programArgs the program arguments.
    *
    * @throws NullPointerException if any of the supplied arguments were null.
    */
   public JavaProcessManager( Class<?> classWithMainMethod, Properties systemProperties, String[] programArgs )
   {
      this.classWithMainMethod = Validate.notNull( classWithMainMethod );
      this.systemProperties = Validate.notNull( systemProperties );
      this.programArgs = Validate.notNull( programArgs );

      final String cp = System.getProperty( "java.class.path" );
      Validate.validState( cp != null );
      Validate.notBlank( cp );
      this.classPath = cp;

      logger.finer("Created JavaProcessManager for class named '" + classWithMainMethod.getName() + "'." );
      logger.finer("The class path is '" + classPath + "'." );
      logger.finer("The system properties are: '" + systemProperties.toString() + "'." );
      logger.finer("The program arguments are: '" + Arrays.toString( programArgs) + "'." );
    }

/*- Public methods -----------------------------------------------------------*/

   /**
    * Attempts to start a new JVM process based on the information supplied in
    * the class constructor; returns success indicator.
    *
    * This method does not block.
    *
    * @param outputCaptureEnable whether the output from the spawned process
    *    should be logged.
    *
    * @return indicator, set true if the startup process was successful.
    */
   public boolean start( boolean outputCaptureEnable )
   {
      logger.info( "Starting a new JVM to run Java class: '" + classWithMainMethod.getSimpleName() + "' [output capture = '" + outputCaptureEnable + "']..." );
      
      // Create initial command line.
      final List<String> commandLine = new ArrayList<>( Arrays.asList( "java", "-cp", classPath ) );

      // Add any supplied system properties.
      final Set<String> keys = systemProperties.stringPropertyNames();
      logger.finer( "There are '" + keys.size() + "' additional system properties." );
      keys.forEach( propName -> {
         final String newProperty = "-D" + propName + "=" + systemProperties.getProperty( propName );
         logger.finer( "Adding system property: '" + newProperty + "' to command line." );
         commandLine.add( newProperty );
      } );

      // Add the name of the class containing the main method to be executed.
      commandLine.add( classWithMainMethod.getName() );

      // Add any program arguments.
      commandLine.addAll( Arrays.asList( programArgs ) );

      // Attempt to start the process.
      try
      {
         // Spawn a new process as a child of the existing process.
         logger.finer( "The new JVM will be started using the command line: '" + commandLine + "'." );
         
         if ( outputCaptureEnable  )
         {
            logger.finest( "The output from the process will be captured in the log." );
            this.process = new ProcessBuilder().command( commandLine ).start();
            JavaProcessStreamConsumer.consumeFrom( process );
         }
         else
         {
            logger.finest( "The output from the process will NOT be captured in the log." );
            this.process = new ProcessBuilder().command( commandLine )
                  .redirectError( ProcessBuilder.Redirect.to( NULL_FILE ) )
                  .redirectOutput( ProcessBuilder.Redirect.to( NULL_FILE ) )
                  .redirectInput( ProcessBuilder.Redirect.from( NULL_FILE ) )
                  .start(); // IOException -->
         }

         logger.finest( "The process was started OK." );
      }
      catch ( RuntimeException | IOException ex )
      {
         final String message = "Failed to run '" + commandLine + "' in separate process.";
         logger.log( Level.WARNING, message, ex );
         return false;
      }

      // Return success indicator based on the measured state of "aliveness".
      return process.isAlive();
   }

   /**
    * Attempts to send an OS signal to kill the process associated with this
    * class instance; WAITS for up to one second for the process to terminate;
    * returns success indicator.
    *
    * The process associated with this manager need not necessarily be alive
    * when this method is invoked.
    *
    * @return indicator, set true if the shutdown process was successful.
    */
   public boolean shutdown()
   {
      logger.finer( "Attempting to kill the process cooperatively..." );
      try
      {
         // According to the API this method will not throw an exception.
         // Nevertheless implementations may vary so if something IS thrown
         // ensure that at least it gets to the log.
         process.destroy();
      }
      catch ( RuntimeException ex )
      {
         logger.log( Level.WARNING, "Exception when terminating the process cooperatively.", ex );
         return false;
      }
      
      if ( process.isAlive() )
      {
         logger.finer( "Attempting to kill the process forcibly..." );
         try
         {
            // According to the API this method will not throw an exception.
            // Nevertheless implementations may vary so if something IS thrown
            // ensure that at least it gets to the log.
            process.destroyForcibly();
         }
         catch ( RuntimeException ex )
         {
            logger.log( Level.WARNING, "Exception when terminating the process forcibly.", ex );
            return false;
         }
      }
      
      logger.finer( "Waiting for process termination..." );
      final boolean terminatedOk;
      try
      {
         terminatedOk = process.waitFor(1, TimeUnit.SECONDS );
      }
      catch ( InterruptedException ex )
      {
         logger.warning( "The process termination wait period was interrupted." );
         return false;
      }

      logger.info( "The process shutdown sequence finished. Result " + (terminatedOk ? "OK." : "FAIL." ) );
      return terminatedOk;
   }

   /**
    * Returns true when the process associated with this manager instance is still
    * alive.
    *
    * @return the result.
    */
   public boolean isAlive()
   {
      return process.isAlive();
   }

   /**
    * Waits for the process associated witht his manager to terminate or for a
    * timeout to occur, whichever comes soonest.
    *
    * @param timeout the maximum time to wait.
    * @param timeUnit the time units.
    * @return true indicates the process has terminated; false indicates a
    * timeout occurred.
    * @throws InterruptedException if the current thread was interrupted while waiting.
    */
   public boolean waitFor( long timeout, TimeUnit timeUnit ) throws InterruptedException
   {
      return process.waitFor( timeout, timeUnit );
   }

/*- Package-level methods ----------------------------------------------------*/
/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
