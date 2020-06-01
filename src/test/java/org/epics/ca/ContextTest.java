/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca;

/*- Imported packages --------------------------------------------------------*/

import static java.util.stream.Collectors.joining;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.epics.ca.impl.repeater.NetworkUtilities;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

class ContextTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( ContextTest.class );
   private final String TEST_CHANNEL_NAME = "test01";

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level access methods ---------------------------------------------*/

   @BeforeEach()
   void beforeEach()
   {
      // Currently (2020-05-22) this test is not supported when the VPN connection is active on the local machine.
      if ( NetworkUtilities.isVpnActive() )
      {
         fail( "This test is not supported when a VPN connection is active on the local network interface." );
      }
   }

   @Test
   void testConstructor_withNoProperties_doesNotThrow()
   {
      assertDoesNotThrow( () -> {
         try ( Context context = new Context() )
         {
            logger.info("The new context was successfully created." );
         }
      } );
   }

   @Test
   void testConstructor_withEmptyProperties_doesNotThrow()
   {
      assertDoesNotThrow( () -> {
         try ( Context context = new Context( new Properties()) )
         {
            logger.info("The new context was successfully created." );
         }
      } );
   }

   @Test
   void testConstructor_withNullProperties_doesThrow()
   {
      final Exception ex = assertThrows( IllegalArgumentException.class, () -> {
         try ( Context context = new Context( null ) )
         {
            logger.warning("The new context was created but shouldn't have been." );
         }
      } );
      assertThat( ex.getMessage(), is( "null properties" ) );
   }

   @Test
   void testCreateChannel()
   {
      try ( Context context = new Context () )
      {
         try ( Channel<?> c = context.createChannel (null, null) )
         {
            fail ("null name/type accepted");
         }
         catch ( IllegalArgumentException iae )
         {
            // expected
         }

         try ( Channel<?> c = context.createChannel (null, Double.class) )
         {
            fail ("null name accepted");
         }
         catch ( IllegalArgumentException iae )
         {
            // expected
         }

         try ( Channel<?> c = context.createChannel (TEST_CHANNEL_NAME, null) )
         {
            fail ("null type accepted");
         }
         catch ( IllegalArgumentException iae )
         {
            // expected
         }

         String tooLongName = Stream.generate (() -> "a").limit (10000).collect (joining ());
         try ( Channel<?> c = context.createChannel (tooLongName, Double.class) )
         {
            fail ("too long name accepted");
         }
         catch ( IllegalArgumentException iae )
         {
            // expected
         }

         try ( Channel<?> c = context.createChannel (TEST_CHANNEL_NAME, Context.class) )
         {
            fail ("invalid type accepted");
         }
         catch ( IllegalArgumentException iae )
         {
            // expected
         }

         try ( Channel<?> c = context.createChannel (TEST_CHANNEL_NAME, Double.class, Constants.CHANNEL_PRIORITY_MIN - 1) )
         {
            fail ("priority out of range accepted");
         }
         catch ( IllegalArgumentException iae )
         {
            // expected
         }

         try ( Channel<?> c = context.createChannel (TEST_CHANNEL_NAME, Double.class, Constants.CHANNEL_PRIORITY_MAX + 1) )
         {
            fail ("priority out of range accepted");
         }
         catch ( IllegalArgumentException iae )
         {
            // expected
         }

         try ( Channel<?> c1 = context.createChannel (TEST_CHANNEL_NAME, Double.class);
               Channel<?> c2 = context.createChannel (TEST_CHANNEL_NAME, Double.class)
         )
         {
            assertNotNull (c1);
            assertNotNull (c2);
            assertNotSame (c1, c2);

            assertEquals (TEST_CHANNEL_NAME, c1.getName ());
         }

         try ( Channel<?> c = context.createChannel (TEST_CHANNEL_NAME, Double.class, Constants.CHANNEL_PRIORITY_DEFAULT) )
         {
            assertNotNull (c);
         }
      }
   }

   @Test
   void testClose()
   {
      final Context context = new Context ();
      context.close ();

      // duplicate close() is OK
      context.close ();

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
   void testRepeaterRegistration() throws InterruptedException
   {
      System.setProperty( "CA_DEBUG", "0");
      System.setProperty( "CA_REPEATER_DEBUG", "true");
      System.setProperty( "CA_REPEATER_OUTPUT_CAPTURE", "true");
      System.setProperty( "CA_REPEATER_SHUTDOWN_ON_CONTEXT_CLOSE", "true");

      try ( Context context = new Context() )
      {
         // This needs to be longer than the CA_REPEATER_INITIAL_DELAY value of currently 500ms.
         Thread.sleep( 600 );
      }
   }

/*- Private methods ----------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
