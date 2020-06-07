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

public class JavaProcessManager
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( JavaProcessManager.class );

   private static final File NULL_FILE = new File(( System.getProperty("os.name").startsWith("Windows") ? "NUL" : "/dev/null" ) );

   private final Class<?> classWithMainMethod;
   private final String[] programArgs;
   private final Properties systemProperties;

   private Process process;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   public JavaProcessManager( Class<?> classWithMainMethod, Properties systemProperties, String[] programArgs )
   {
      logger.finer("Created JavaProcessManager for class named '" + classWithMainMethod.getName() + "'." );
      logger.finer("The system properties are: '" + systemProperties.toString() + "'." );
      logger.finer("The program arguments are: '" + Arrays.toString( programArgs) + "'." );
      this.classWithMainMethod = Validate.notNull( classWithMainMethod );
      this.systemProperties = Validate.notNull( systemProperties );
      this.programArgs = Validate.notNull( programArgs );
   }

/*- Public methods -----------------------------------------------------------*/

   public boolean start( boolean outputCaptureEnable )
   {
      logger.info( "Starting the process with output capture set to: '" + outputCaptureEnable + "'..." );
      if ( System.getProperty( "java.class.path" ) == null )
      {
         throw new RuntimeException( "The Java class path was not found." );
      }
      
      // Create initial command line
      final String classPath = System.getProperty( "java.class.path" );
      final List<String> commandLine = new ArrayList<>( Arrays.asList("java", "-cp", classPath ) );

      // Add any supplied system properties
      final Set<String> keys = systemProperties.stringPropertyNames();
      logger.finer( "There are '" + keys.size() + "' additional system properties." );
      keys.forEach( propName -> {
         final String newProperty = "-D" + propName + "=" + systemProperties.getProperty( propName );
         logger.finer( "Adding system property: '" + newProperty + "' to command line." );
         commandLine.add( newProperty );
      } );

      // Add name of class containing the main method
      commandLine.add( classWithMainMethod.getName() );

      // Add any program arguments.
      commandLine.addAll( Arrays.asList( programArgs ) );
      
      try
      {
         // Spawn a new process as a child of the existing process.
         logger.finer( "The new JVM will be started using the command line: '" + commandLine + "'." );
         
         if ( outputCaptureEnable  )
         {
            logger.finest( "The output from the process will be captured in the log." );
            this.process = new ProcessBuilder().command( commandLine ).start();
            JavaProcessStreamConsumer.consumeFrom(process );
         }
         else
         {
            logger.finest( "The output from the process will NOT be captured in the log." );
            this.process = new ProcessBuilder().command( commandLine )
                  .redirectError( ProcessBuilder.Redirect.to( NULL_FILE ) )
                  .redirectOutput( ProcessBuilder.Redirect.to( NULL_FILE ) )
                  .redirectInput( ProcessBuilder.Redirect.from( NULL_FILE ) )
                  .start();
         }

         logger.finest( "The process was started OK." );
      }
      catch ( IOException ex )
      {
         final String message = "Failed to run '" + commandLine + "' in separate process.";
         logger.log( Level.WARNING, message, ex );
         throw new RuntimeException( message, ex );
      }

      return process.isAlive();
   }
   
   public boolean shutdown()
   {
      logger.finer( "Attempting to terminate the process cooperatively..." );
      try
      {
         process.destroy();
      }
      catch ( RuntimeException ex )
      {
         logger.warning( "Exception when terminating the process cooperatively." );
      }
      
      if ( process.isAlive() )
      {
         logger.finer( "Attempting to terminate the process forcibly..." );
         try
         {
            process.destroy();
         }
         catch ( RuntimeException ex )
         {
            logger.warning( "Exception when terminating the process forcibly." );
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

      logger.info( "The process termination sequence finished. Result " + (terminatedOk ? "OK." : "FAIL." ) );
      return terminatedOk;
   }
   
   public boolean isAlive()
   {
      return process.isAlive();
   }   

/*- Package-level methods ----------------------------------------------------*/
/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
