/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.epics.ca.util.logging.LibraryLogManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;


/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * Provides support for consuming the ouput of a spawned process and sending
 * both the stout and stderr streams to the log.
 */
public class ProcessStreamConsumer
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( org.epics.ca.impl.repeater.ProcessStreamConsumer.class );
   private final InputStream stdout;
   private final InputStream stderr;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   private ProcessStreamConsumer( Process process )
   {
      Validate.notNull( process );
      Validate.isTrue( process.isAlive() );

      this.stdout = process.getInputStream();
      this.stderr = process.getErrorStream();
   }

/*- Public methods -----------------------------------------------------------*/

   static ProcessStreamConsumer consumeFrom( Process process )
   {
      Validate.notNull( process );

      final ProcessStreamConsumer processStreamConsumer = new ProcessStreamConsumer( process );
      processStreamConsumer.start();
      return processStreamConsumer;
   }

   public void shutdown()
   {
      try
      {
         logger.finest( "Closing down the STDOUT stream consumer." );
         stdout.close();
         logger.finest( "The STDOUT stream consumer has been closed." );
      }
      catch ( IOException ex )
      {
         logger.log( Level.WARNING, "Exception when closing down the STDOUT stream consumer.", ex );
      }

      try
      {
         logger.finest( "Closing down the STDERR stream consumer." );
         stderr.close();
         logger.finest( "The STDERR stream consumer has been closed." );
      }
      catch ( IOException ex )
      {
         logger.log( Level.WARNING, "Exception when closing down the STDERR stream consumer.", ex );
      }
   }

/*- Package-level methods ----------------------------------------------------*/
/*- Private methods ----------------------------------------------------------*/

   private void start()
   {
      logger.finest( "Starting stream consumers..." );
      new Thread( () -> consumeAndLogSubProcessStream( "STDOUT", stdout, Level.INFO ) ).start();
      new Thread( () -> consumeAndLogSubProcessStream( "STDERR", stderr, Level.WARNING ) ).start();
      logger.finest( "The stream consumers are running." );
   }

   private void consumeAndLogSubProcessStream( String streamName, InputStream streamToConsume, Level logLevel )
   {
      Validate.notBlank( streamName );
      Validate.notNull( streamToConsume );
      Validate.notNull( logLevel );

      try ( final BufferedReader subProcessStreamReader = new BufferedReader( new InputStreamReader( streamToConsume ) ) )
      {
         logger.finest( "Monitoring stream " + streamName + " of type " + streamToConsume.getClass().getSimpleName()  + "." );
         try
         {
            String line;
            // Thread blocks here until some data arrives...
            while ( ( line = subProcessStreamReader.readLine() ) != null )
            {
               logger.log( logLevel, line );
            }
         }
         catch ( IOException ex )
         {
            logger.log( Level.WARNING, "Exception reading stream", ex );
         }
         logger.finest( "The stream " + streamName + " has terminated." );
      }
      catch( Exception ex )
      {
         logger.warning( "Failed to read subprocess stream. Exception message: '" + ex.getMessage() + "'." );
      }
   }

/*- Nested Classes -----------------------------------------------------------*/

}
