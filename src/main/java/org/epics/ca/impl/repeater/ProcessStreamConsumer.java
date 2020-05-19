/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.repeater;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.Validate;
import org.epics.ca.util.logging.LibraryLogManager;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
   private final ExecutorService executorService;
   private final Process process;


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   private ProcessStreamConsumer( Process process )
   {
      Validate.notNull(process );
      Validate.isTrue( process.isAlive() );
      this.process = process;
      this.executorService = Executors.newFixedThreadPool(3 );
   }

/*- Public methods -----------------------------------------------------------*/

   static void consumeFrom( Process process )
   {
      final ProcessStreamConsumer processStreamConsumer = new ProcessStreamConsumer(process );
      processStreamConsumer.start();
   }

/*- Package-level methods ----------------------------------------------------*/
/*- Private methods ----------------------------------------------------------*/

   private void start()
   {
      logger.finest( "Started." );
      executorService.submit( () -> consumeAndLogSubProcessStream( process.getInputStream(), Level.INFO ) );
      executorService.submit( () -> consumeAndLogSubProcessStream( process.getErrorStream(), Level.WARNING ) );
      executorService.submit( this::shutdownExecutorWhenProcessDies );
      logger.finest( "Ready." );
   }

   private void shutdownExecutorWhenProcessDies()
   {
      logger.finest( "Waiting for process death..." );
      while( process.isAlive() )
      {
         try
         {
            process.waitFor();
         }
         catch ( InterruptedException ex )
         {
            Thread.currentThread().interrupt();
            logger.warning( "Process stream consumer was interrupted !" );
         }
      }
      logger.finest( "Process has died. Shutting down..." );
      executorService.shutdownNow();
      logger.finest( "Shutdown completed." );
   }

   private void consumeAndLogSubProcessStream( InputStream streamToConsume, Level logLevel)
   {
      try ( BufferedReader subProcessStreamReader = new BufferedReader( new InputStreamReader(streamToConsume ) ) )
      {
         logger.finest( "Monitoring Stream: " + streamToConsume );
         while( ! executorService.isTerminated() )
         {
            // Thread blocks here until some data arrives...
            while ( subProcessStreamReader.ready() )
            {
               logger.finest("New data has arrived." );
               final String readLine = subProcessStreamReader.readLine();
               logger.log( logLevel, readLine );
            }
         }
      }
      catch( Exception ex )
      {
         logger.warning( "Failed to read subprocess stream. Exception message: '" + ex.getMessage() + "'." );
      }
   }

/*- Nested Classes -----------------------------------------------------------*/

}
