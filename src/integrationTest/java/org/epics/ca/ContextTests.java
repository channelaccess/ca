/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.time.StopWatch;

import org.epics.ca.impl.repeater.NetworkUtilities;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.fail;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

@TestMethodOrder( MethodOrderer.Alphanumeric.class)
public class ContextTests
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( ContextTests.class );

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Class methods ------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-private methods --------------------------------------------------*/

   @BeforeAll
   static void beforeAll()
   {
      // This is a guard condition. There is no point in checking the behaviour
      // of the CARepeaterStarterTest class if the network stack is not appropriately
      // configured for channel access.
      assertThat(NetworkUtilities.verifyTargetPlatformNetworkStackIsChannelAccessCompatible(), is(true ) );

      // Currently (2020-05-22) this test is not supported when the VPN connection is active on the local machine.
      if ( NetworkUtilities.isVpnActive() )
      {
         fail( "This test is not supported when a VPN connection is active on the local network interface." );
      }
   }

   /**
    * Q1: Can the context manual close feature be relied on to cleanup the created channels ?
    */

   @Test
   void q01()
   {
      logger.fine( "Performing Q1 Test: please wait...");

      assertTimeoutPreemptively( Duration.ofSeconds( 5L ), () ->
      {
         final Context caContext = new Context();
         final Channel<String> caChannel = caContext.createChannel("ca:test:counter01", String.class);
         caChannel.connect();
         Assertions.assertEquals(ConnectionState.CONNECTED, caChannel.getConnectionState());
         caContext.close();
         Assertions.assertEquals(ConnectionState.CLOSED, caChannel.getConnectionState());

         logger.info( "RESULTS:" );
         logger.info( "Q1: Can the context manual close feature be relied on to cleanup the created channels ? Answer: **YES**." );
      } );
   }

   /**
    * Q2: Can the context autoclose feature be relied on to cleanup the created channels ?
    */
   @Test
   void q02()
   {
      logger.fine( "Performing Q2 Test: please wait...");

      assertTimeoutPreemptively( Duration.ofSeconds( 5L ), () ->
      {
         final Channel<String> caChannel;
         final Context caContextCopy;
         try( final Context caContext = new Context() )
         {
            caContextCopy = caContext;

            caChannel = caContext.createChannel( "ca:test:counter01", String.class );
            caChannel.connect();
            Assertions.assertEquals(ConnectionState.CONNECTED, caChannel.getConnectionState());
         }

         // After the try-with-resources statement the context should have closed
         // the channel
         Assertions.assertEquals(ConnectionState.CLOSED, caChannel.getConnectionState());

         // And it should no longer be possible to createNext new channels
         try
         {
            caContextCopy.createChannel("ca:test:counter01", String.class);
         }
         catch ( Throwable t )
         {
            Assertions.assertTrue(t instanceof RuntimeException);
         }
         logger.info( "RESULTS:" );
         logger.info( "Q2: Can the context autoclose feature be relied on to cleanup the created channels ? Answer: **YES**." );
      } );
   }

   /**
    * Q3: How many contexts can be created ?
    * Q4: What is the creation cost ?
    * Q5: Do all contexts share the same returned object ?
    */
   @Test
   void q03q04q05()
   {
      logger.fine( "Performing Q3/Q4/Q5 Tests: please wait...");

      System.setProperty( "CA_LIBRARY_LOG_LEVEL", "WARNING" );

      // With default behaviour of ca-1.1.0 release we can only createNext ~200 contexts (as
      // each context starts 10 threads)
      // With default behaviour of ca-1.2.0 release we can only createNext ~100 contexts (as
      // each context starts 16 threads)
      final List<Integer> samplePoint = Arrays.asList( 1, 10, 50, 100, 150 );
      final Map<Integer,Long> resultMap = new LinkedHashMap<>();
      final Map<Integer,Context> contextObjectMap = new LinkedHashMap<>();
      final List<Context> contextList = new ArrayList<>();

      final StopWatch stopWatch = StopWatch.createStarted();
      int loopCounter = 0;

      while ( loopCounter < samplePoint.get( samplePoint.size() - 1 ) )
      {
         loopCounter++;
         final Context caContext;
         try
         {
            caContext= new Context( );
            contextList.add( caContext );
         }
         catch( Throwable ex )
         {
            logger.fine( String.format( "Test terminated due to exception after creating %d contexts", loopCounter ) );
            break;
         }
         if ( samplePoint.contains( loopCounter ) )
         {
            resultMap.put( loopCounter, stopWatch.getTime() );
            contextObjectMap.put( loopCounter, caContext );
            logger.fine( String.format( "Created %d contexts.", loopCounter ) );
         }
      }

      logger.info( "RESULTS:" );
      logger.info( String.format( "Q3: How many contexts can be created ? Answer: **at least %d**.", loopCounter ) );
      logger.info( "Q4: What is the context creation cost ? Answer: **See below.**" );
      logger.info("```");
      for ( int result : resultMap.keySet() )
      {
         logger.info( String.format( "- Creating %d contexts took %d ms. Average: %.3f ms", result, resultMap.get( result ), (float) resultMap.get( result ) / result ) );
      }
      logger.info("```");

      final String result = resultMap.size() == contextObjectMap.size() ? "**NO**" : "**YES**.";
      logger.info( String.format( "Q5: Do all contexts share the same returned object ? Answer: %s.", result ) );
      logger.info( "Context object names were as follows:" );
      logger.info( "```" );
      for ( int sampleNumber : contextObjectMap.keySet() )
      {
         logger.info( String.format( "- Context object %d had name: %s", sampleNumber, contextObjectMap.get( sampleNumber ) ) );
      }
      logger.info("```");

      // Cleanup the contexts by closing them
      for ( Context caContext : contextList )
      {
         caContext.close();
      }
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
