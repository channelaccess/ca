/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import static java.util.stream.Collectors.joining;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.epics.ca.impl.LibraryConfiguration;
import org.epics.ca.impl.repeater.CARepeaterStarter;
import org.epics.ca.impl.repeater.NetworkUtilities;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

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

   @BeforeEach()
   void beforeEach()
   {
      threadWatcher = ThreadWatcher.start();
      
      // Currently (2020-05-22) this test is not supported when the VPN connection is active on the local machine.
      if ( NetworkUtilities.isVpnActive() )
      {
         fail( "This test is not supported when a VPN connection is active on the local network interface." );
      }
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
         fail ( "creation of a channel on closed context must fail" );
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
   void testCreateContext_doesNotStartRepeater_whenDisabled() throws InterruptedException
   {
      System.setProperty( LibraryConfiguration.PropertyNames.CA_REPEATER_DISABLE.toString(), "true" );
      try ( final Context ignored = new Context() )
      {
         Thread.sleep( 1500 );
         assertThat( CARepeaterStarter.isRepeaterRunning( 5065 ), is( false ) );
      }
   }

   @Test
   void testCreateContext_startsRepeaterByDefault() throws InterruptedException
   {
      try ( final Context ignored = new Context() )
      {
         Thread.sleep( 1500 );
         assertThat( CARepeaterStarter.isRepeaterRunning( 5065 ), is( true ) );
      }
      Thread.sleep( 1500 );
      assertThat( CARepeaterStarter.isRepeaterRunning( 5065 ), is( false ) );
   }


   @Test
   void testCreateContext_startsRepeaterWhenEnabled_thenShutsItDownAgainWhenContextGoesOutOfScope() throws InterruptedException
   {
      System.setProperty( LibraryConfiguration.PropertyNames.CA_REPEATER_DISABLE.toString(), "false" );
      try ( final Context ignored = new Context() )
      {
         Thread.sleep( 1500 );
         assertThat( CARepeaterStarter.isRepeaterRunning( 5065 ), is( true ) );
      }
      Thread.sleep( 1500 );
      assertThat( CARepeaterStarter.isRepeaterRunning( 5065 ), is( false ) );
   }

   @Test
   void testCreateMultipleContexts_repeaterShutsdownOnlyAfterLastContextIsClosed() throws InterruptedException
   {
      //System.setProperty( LibraryConfiguration.PropertyNames.CA_LIBRARY_LOG_LEVEL.toString(), "ALL" );
      System.setProperty( LibraryConfiguration.PropertyNames.CA_REPEATER_DISABLE.toString(), "false" );
      logger.info( "CREATING FIRST CONTEXT..." );
      final Context ignored1 = new Context();
      Thread.sleep( 1500 );

      logger.info( "CHECKING REPEATER IS RUNNING..." );
      assertThat( CARepeaterStarter.isRepeaterRunning( 5065 ), is( true ) );
      logger.info( "OK" );

      logger.info( "CREATING SECOND CONTEXT..." );
      try ( final Context ignored2 = new Context() )
      {
         Thread.sleep( 1500 );
         logger.info( "CHECKING REPEATER IS RUNNING..." );
         assertThat( CARepeaterStarter.isRepeaterRunning( 5065 ), is( true ) );
         logger.info( "OK" );
         logger.info( "CLOSING SECOND CONTEXT..." );
      }

      Thread.sleep( 1500 );
      logger.info( "CHECKING REPEATER IS STILL RUNNING..." );
      assertThat( CARepeaterStarter.isRepeaterRunning( 5065 ), is( true ) );
      logger.info( "OK" );

      logger.info( "CLOSING FIRST CONTEXT..." );
      ignored1.close();

      Thread.sleep( 1500 );
      logger.info( "CHECKING REPEATER IS NOW STOPPED..." );
      assertThat( CARepeaterStarter.isRepeaterRunning( 5065 ), is( false ) );
      logger.info( "OK" );
   }


   @Test
   void testRepeaterRegistration() throws InterruptedException
   {
      // The logging for this context is set to verbose so that the log messages can be examined
      // by eye to see whether the repeater successfully registered. There should
      // be messages in the log saying that one client was registered.
      final Properties properties = new Properties();
      properties.setProperty( LibraryConfiguration.PropertyNames.CA_LIBRARY_LOG_LEVEL.toString(), Level.FINER.toString() );
      properties.setProperty( LibraryConfiguration.PropertyNames.CA_REPEATER_LOG_LEVEL.toString(), Level.ALL.toString() );
      properties.setProperty( LibraryConfiguration.PropertyNames.CA_REPEATER_OUTPUT_CAPTURE.toString(), "true");
      try ( Context ignored = new Context( properties ) )
      {
         // This needs to be longer than the CA_REPEATER_INITIAL_DELAY value of currently 500ms.
         Thread.sleep( 600 );
      }
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
