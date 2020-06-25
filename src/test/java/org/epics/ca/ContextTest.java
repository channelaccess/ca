/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import static java.util.stream.Collectors.joining;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.apache.commons.lang3.time.StopWatch;
import org.epics.ca.impl.LibraryConfiguration;
import org.epics.ca.impl.ProtocolConfiguration;
import org.epics.ca.impl.repeater.CARepeaterStarter;
import org.epics.ca.impl.repeater.NetworkUtilities;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

class ContextTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( ContextTest.class );
   private ThreadWatcher threadWatcher;
   
   private static final String TEST_CHANNEL_NAME = "test01";

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level access methods ---------------------------------------------*/

   @BeforeAll
   static void beforeAll()
   {
      // This is a guard condition. There is no point in running the tests
      // if the network stack is not appropriately configured for channel access.
      assertThat( NetworkUtilities.verifyTargetPlatformNetworkStackIsChannelAccessCompatible(), is( true ) );

      // Currently (2020-05-22) this test is not supported when the VPN connection is active on the local machine.
      if ( NetworkUtilities.isVpnActive() )
      {
         fail( "This test is not supported when a VPN connection is active on the local network interface." );
      }
   }

   @BeforeEach()
   void beforeEach()
   {
      threadWatcher = ThreadWatcher.start();
   }
   
   @AfterEach()
   void afterEach()
   {
      threadWatcher.verify();
   }
   
   @Test
   void testConstructor_withNoProperties_doesNotThrow()
   {
      assertDoesNotThrow( () -> {
         try ( Context ignored = new Context() )
         {
            logger.info("The new context was successfully created." );
         }
      } );
   }

   @Test
   void testConstructor_withEmptyProperties_doesNotThrow()
   {
      assertDoesNotThrow( () -> {
         try ( Context ignored = new Context( new Properties()) )
         {
            logger.info("The new context was successfully created." );
         }
      } );
   }

   @Test
   void testConstructor_withNullProperties_doesThrow()
   {
      final Exception ex = assertThrows( NullPointerException.class, () -> {
         try ( Context ignored = new Context( null ) )
         {
            logger.warning("The new context was created but shouldn't have been." );
         }
      } );
      assertThat( ex.getMessage(), is( "null properties" ) );
   }

   @Test
   void testCreateChannel()
   {
      try ( Context context = new Context() )
      {
         try ( Channel<?> ignored = context.createChannel(null, null) )
         {
            fail ("null name/type accepted");
         }
         catch ( NullPointerException npe )
         {
            // expected
         }

         try ( Channel<?> ignored = context.createChannel(null, Double.class) )
         {
            fail( "null name accepted" );
         }
         catch ( NullPointerException npe )
         {
            // expected
         }

         try ( Channel<?> ignored = context.createChannel( TEST_CHANNEL_NAME, null) )
         {
            fail( "null type accepted" );
         }
         catch ( NullPointerException npe )
         {
            // expected
         }

         final String tooLongName = Stream.generate (() -> "a").limit( 10000 ).collect (joining () );
         try ( Channel<?> ignored = context.createChannel( tooLongName, Double.class) )
         {
            fail ("too long name accepted");
         }
         catch ( IllegalArgumentException iae )
         {
            // expected
         }

         try ( Channel<?> ignored = context.createChannel( TEST_CHANNEL_NAME, Context.class) )
         {
            fail ("invalid type accepted");
         }
         catch ( IllegalArgumentException iae )
         {
            // expected
         }

         try ( Channel<?> ignored = context.createChannel( TEST_CHANNEL_NAME, Double.class, Constants.CHANNEL_PRIORITY_MIN - 1) )
         {
            fail ("priority out of range accepted");
         }
         catch ( IllegalArgumentException iae )
         {
            // expected
         }

         try ( Channel<?> ignored = context.createChannel( TEST_CHANNEL_NAME, Double.class, Constants.CHANNEL_PRIORITY_MAX + 1) )
         {
            fail ("priority out of range accepted");
         }
         catch ( IllegalArgumentException iae )
         {
            // expected
         }

         try ( Channel<?> c1 = context.createChannel( TEST_CHANNEL_NAME, Double.class );
               Channel<?> c2 = context.createChannel( TEST_CHANNEL_NAME, Double.class )
         )
         {
            assertNotNull( c1 );
            assertNotNull( c2 );
            assertNotSame( c1, c2 );

            assertEquals( TEST_CHANNEL_NAME, c1.getName () );
         }

         try ( Channel<?> c = context.createChannel( TEST_CHANNEL_NAME, Double.class, Constants.CHANNEL_PRIORITY_DEFAULT ) )
         {
            assertNotNull( c );
         }
      }
   }

   @Test
   void testOperationsOnClosedContext()
   {
      final Context context = new Context();
      context.close();

      // duplicate close() is OK
      context.close();

      try
      {
         context.createChannel( TEST_CHANNEL_NAME, Double.class );
         fail( "creation of a channel on closed context must fail" );
      }
      catch ( RuntimeException rt )
      {
         // expected
      }

      try
      {
         context.createChannel( TEST_CHANNEL_NAME, Double.class, Constants.CHANNEL_PRIORITY_DEFAULT );
         fail( "creation of a channel on closed context must fail" );
      }
      catch ( RuntimeException rt )
      {
         // expected
      }
   }

   @Test
   void integrationTestCreateContext_doesNotStartRepeater_whenDisabled() throws InterruptedException
   {
      System.setProperty( LibraryConfiguration.PropertyNames.CA_REPEATER_DISABLE.toString(), "true" );
      try ( final Context ignored = new Context() )
      {
         Thread.sleep( 1500 );
         assertThat( CARepeaterServiceManager.isRepeaterRunning( 5065 ), is( false ) );
      }
      System.setProperty( LibraryConfiguration.PropertyNames.CA_REPEATER_DISABLE.toString(), String.valueOf( LibraryConfiguration.CA_REPEATER_DISABLE_DEFAULT ) );
   }

   @Test
   void integrationTestCreateContext_startsRepeaterByDefault() throws InterruptedException
   {
      try ( final Context ignored = new Context() )
      {
         Thread.sleep( 1500 );
         assertThat( CARepeaterServiceManager.isRepeaterRunning( 5065 ), is( true ) );
      }
      Thread.sleep( 1500 );
      assertThat( CARepeaterServiceManager.isRepeaterRunning( 5065 ), is( false ) );
   }

   @Test
   void integrationTestCreateContext_startsRepeaterWhenEnabled_thenShutsItDownAgainWhenContextGoesOutOfScope() throws InterruptedException
   {
      System.setProperty( LibraryConfiguration.PropertyNames.CA_REPEATER_DISABLE.toString(), "false" );
      try ( final Context ignored = new Context() )
      {
         Thread.sleep( 1500 );
         assertThat( CARepeaterServiceManager.isRepeaterRunning( 5065 ), is( true ) );
      }
      Thread.sleep( 1500 );
      assertThat( CARepeaterServiceManager.isRepeaterRunning( 5065 ), is( false ) );
      System.setProperty( LibraryConfiguration.PropertyNames.CA_REPEATER_DISABLE.toString(), String.valueOf( LibraryConfiguration.CA_REPEATER_DISABLE_DEFAULT ) );
   }

   @Test
   void integrationTestCreateMultipleContexts_repeaterShutdownOccursOnlyAfterLastContextIsClosed() throws InterruptedException
   {
      System.setProperty( LibraryConfiguration.PropertyNames.CA_REPEATER_DISABLE.toString(), "false" );
      logger.info( "CREATING FIRST CONTEXT..." );
      final Context ignored1 = new Context();
      Thread.sleep( 1500 );

      logger.info( "CHECKING REPEATER IS RUNNING..." );
      assertThat( CARepeaterServiceManager.isRepeaterRunning( 5065 ), is( true ) );
      logger.info( "OK" );

      logger.info( "CREATING SECOND CONTEXT..." );
      try ( final Context ignored2 = new Context() )
      {
         Thread.sleep( 1500 );
         logger.info( "CHECKING REPEATER IS RUNNING..." );
         assertThat( CARepeaterServiceManager.isRepeaterRunning( 5065 ), is( true ) );
         logger.info( "OK" );
         logger.info( "CLOSING SECOND CONTEXT..." );
      }

      Thread.sleep( 1500 );
      logger.info( "CHECKING REPEATER IS STILL RUNNING..." );
      assertThat( CARepeaterServiceManager.isRepeaterRunning( 5065 ), is( true ) );
      logger.info( "OK" );

      logger.info( "CLOSING FIRST CONTEXT..." );
      ignored1.close();

      Thread.sleep( 1500 );
      logger.info( "CHECKING REPEATER IS NOW STOPPED..." );
      assertThat( CARepeaterServiceManager.isRepeaterRunning( 5065 ), is( false ) );
      logger.info( "OK" );
      System.setProperty( LibraryConfiguration.PropertyNames.CA_REPEATER_DISABLE.toString(), String.valueOf( LibraryConfiguration.CA_REPEATER_DISABLE_DEFAULT ) );
   }

   @Test
   void integrationTestCreateMultipleContexts_withMultipleRepeaters_runningOnMultiplePorts() throws InterruptedException
   {
      final Properties ctx1Props = new Properties();
      ctx1Props.setProperty( ProtocolConfiguration.PropertyNames.EPICS_CA_REPEATER_PORT.toString(), "1111" );
      final Context ignored1 = new Context( ctx1Props );
      Thread.sleep( 1500 );

      assertThat( CARepeaterServiceManager.isRepeaterRunning( 1111 ), is( true ) );

      final Properties ctx2Props = new Properties();
      ctx2Props.setProperty(ProtocolConfiguration.PropertyNames.EPICS_CA_REPEATER_PORT.toString(), "2222" );

      try ( final Context ignored2 = new Context( ctx2Props ) )
      {
         Thread.sleep( 1500 );
         assertThat( CARepeaterServiceManager.isRepeaterRunning( 2222 ), is( true ) );
      }

      Thread.sleep( 1500 );
      assertThat( CARepeaterServiceManager.isRepeaterRunning( 2222 ), is( false ) );

      ignored1.close();

      Thread.sleep( 1500 );
      assertThat( CARepeaterServiceManager.isRepeaterRunning( 1111 ), is( false ) );
   }

   @Test
   void integrationTestRepeaterRegistration() throws InterruptedException
   {
      // The logging for this context is set to verbose so that the log messages can be examined
      // by eye to see whether the repeater successfully registered. There should
      // be messages in the log saying that one client was registered.
      System.setProperty( LibraryConfiguration.PropertyNames.CA_LIBRARY_LOG_LEVEL.toString(), Level.FINER.toString() );
      System.setProperty( LibraryConfiguration.PropertyNames.CA_REPEATER_LOG_LEVEL.toString(), Level.ALL.toString() );
      System.setProperty( LibraryConfiguration.PropertyNames.CA_REPEATER_OUTPUT_CAPTURE.toString(), "true");
      try ( Context ignored = new Context() )
      {
         // This needs to be longer than the CA_REPEATER_INITIAL_REGISTRATION_DELAY value of currently 500ms.
         Thread.sleep( 600 );
      }

      // Restore old settings so as not to interfere with other tests.
      System.setProperty( LibraryConfiguration.PropertyNames.CA_LIBRARY_LOG_LEVEL.toString(), LibraryConfiguration.CA_LIBRARY_LOG_LEVEL_DEFAULT.toString() );
      System.setProperty( LibraryConfiguration.PropertyNames.CA_REPEATER_LOG_LEVEL.toString(), LibraryConfiguration.CA_REPEATER_LOG_LEVEL_DEFAULT.toString() );
      System.setProperty( LibraryConfiguration.PropertyNames.CA_REPEATER_OUTPUT_CAPTURE.toString(), String.valueOf( LibraryConfiguration.CA_REPEATER_OUTPUT_CAPTURE_DEFAULT ) );
   }

   @CsvSource( { "false, 10000, 15000", "true, 0, 2000" } )
   @ParameterizedTest
   // Note: this is an important test that could be called CHECK REPEATER ACTUALLY WORKS !!
   void integrationTestTimeToConnectToNewlyStartedChannelAccessServer( boolean repeaterEnable, int minConnectTimeInMillis, int maxConnectTimeInMillis ) throws InterruptedException, ExecutionException
   {
      // Set the required state of enablement of the CA Repeater
      System.setProperty( LibraryConfiguration.PropertyNames.CA_REPEATER_DISABLE.toString(), String.valueOf( ! repeaterEnable ) );

      final StopWatch stopWatch = new StopWatch();
      try ( Context context = new Context() )
      {
         // Attempt to connect to a channel that is not yet on the network.
         final Channel<String> channel = context.createChannel( "adc01", String.class );
         final CompletableFuture<Channel<String>> future = channel.connectAsync();

         // Need to allow time for the channel search messages to become less frequent.
         // The periodic search time reptition rate is determined by parameters
         // used in the algorithm in class SearchTimer.  After 30 seconds the
         // repetition rate should have stabilised to its maximum which is
         // 30s.
         //
         // Currently, the parameters are as follows:
         //
         // MIN_SEND_INTERVAL_MS_DEFAULT = 100;
         // MAX_SEND_INTERVAL_MS_DEFAULT = 30_000;
         // INTERVAL_MULTIPLIER_DEFAULT = 2;
         //
         // This gives rise to the following search intervals / request time:
         // Intervals: 100ms, 200ms, 400ms, 800ms, 1.6s, 3.2s, 6.4s, 12.8s, 25.6s, 30s
         // Elapsed Time: 100, 300, 700, 1500, 3100, 6300, 12700, 25500, 51100, 81100..
         //
         // => If we wait 12.8 seconds it will be another ~12 seconds before the next
         // search request. UNLESS a beacon messages is received which should
         // cause the search request to be initiated sooner.
         Thread.sleep(13_000 );

         // Now start the test server with the channel that is being searched for
         logger.info( "Starting Test Server..." );
         EpicsChannelAccessTestServer.start();

         // Measure the time between starting the test server and when the channel
         // connects. If the CA Repeater is running then this time should be considerably
         // reduced because the beacon messages from the newly started server will trigger
         // the CA library to start searching immediately. If the CA Repeater is not running
         // then the connection time will be longer since the CA library only sends out
         // search requests infrequently.
         stopWatch.start();
         future.get();

         // Record the moment when the CA library finally connects the channel.
         stopWatch.stop();
         final String repeaterState = repeaterEnable ? "ENABLED" : "DISABLED";
         logger.info( "TIME TO CONNECT WITH REPEATER " + repeaterState +
                            " = " + stopWatch.getTime( TimeUnit.MILLISECONDS ) + " ms." );
      }
      finally
      {
         System.setProperty( LibraryConfiguration.PropertyNames.CA_REPEATER_DISABLE.toString(), String.valueOf( LibraryConfiguration.CA_REPEATER_DISABLE_DEFAULT ) );
         EpicsChannelAccessTestServer.shutdown();
      }

      assertThat( (int) stopWatch.getTime( TimeUnit.MILLISECONDS), greaterThan( minConnectTimeInMillis ) );
      assertThat( (int) stopWatch.getTime( TimeUnit.MILLISECONDS), lessThan( maxConnectTimeInMillis ) );

   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
