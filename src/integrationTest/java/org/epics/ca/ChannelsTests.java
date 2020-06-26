/*- Package Declaration ------------------------------------------------------*/
package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import org.apache.commons.lang3.time.StopWatch;
import org.epics.ca.impl.ProtocolConfiguration;
import org.epics.ca.impl.repeater.NetworkUtilities;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.*;

import java.util.logging.Logger;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.fail;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

@TestMethodOrder( MethodOrderer.Alphanumeric.class)
public class ChannelsTests
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( ChannelsTests.class );
   private Context context;


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

   @BeforeEach
   void beforeEach() throws Exception
   {
      // The following sleep is sometimes useful to allow profiling with tools like
      // jvisualvm (provides some time for the tool to connect).
      // Thread.sleep( 10000 );

      // Every test involves the use of at least one context so it is better to
      // set them up and close them down outside the test.

      logger.info( "Creating CA context...");
      final Properties properties = new Properties();
      properties.setProperty( ProtocolConfiguration.PropertyNames.EPICS_CA_MAX_ARRAY_BYTES.toString(), "1000000" );
      //properties.setProperty( "CA_DEBUG", "1" );
      context = new Context( properties );
      logger.info( "Done.");

      // Check the database is online
      try
      {
         context.createChannel("ca:test:db_ok", String.class).connectAsync().get(5, TimeUnit.SECONDS );
      }
      catch ( TimeoutException ex ) {
         logger.warning( "The EPICS test database 'epics_tests.db' was not discoverable on the local network." );
         logger.warning( "Please ensure that it is running and available on the network before restarting these tests. ");
         throw new RuntimeException( "EPICS Test Database Not Available - can't run tests !" );
      }
   }

   @AfterEach
   public void afterEach()
   {
      logger.info( "Cleaning up context...");
      context.close();
      logger.info( "Done.");
   }

   /**
    *  Q31: What is the cost of synchronously connecting channels (using Channels class) ?
    */
   @Test
   void q31()
   {
      logger.fine( "Performing Q31 Test: please wait...");

      final List<Integer> samplePoints = Arrays.asList( 1, 10, 100, 500, 1_000, 2_000 );
      final int maxChannels = samplePoints.get( samplePoints.size() - 1 );

      final Map<Integer,Long> resultMap = new LinkedHashMap<>();
      final StopWatch stopWatch = StopWatch.createStarted();

      for ( int i =0; i < maxChannels; i++ )
      {
         try
         {
            Channels.create( context, "ca:test:counter01", String.class );

            if ( samplePoints.contains( i ) )
            {
               resultMap.put( i, stopWatch.getTime() );
               logger.fine( String.format( "Synchronously connected %d channels.", i ) );
            }
         }
         catch( Throwable ex )
         {
            logger.fine( "Test terminated due to exception after synchronously connecting {} channels" +  i );
            logger.fine( "The exception message was:" + ex );
         }
      }

      logger.info( "RESULTS:" );
      logger.info( "Q31: What is the cost of synchronously connecting channels (using Channels class) ? Answer: **See below.**" );
      logger.info("```");
      for ( int result : resultMap.keySet() )
      {
         logger.info( String.format( "- Synchronously connecting %d channels took %d ms. Average: %.3f ms", result, resultMap.get( result ), (float) resultMap.get( result ) / result ) );
      }
      logger.info("```");
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
