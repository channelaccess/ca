/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca;

import org.epics.ca.annotation.CaChannel;
import org.epics.ca.impl.repeater.NetworkUtilities;
import org.epics.ca.util.logging.LibraryLogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

class ChannelsTest
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final Logger logger = LibraryLogManager.getLogger( ChannelsTest.class );

   private Context context;
   private EpicsChannelAccessTestServer channelAccessTestServer;

/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/
/*- Public methods -----------------------------------------------------------*/
/*- Package-level methods ----------------------------------------------------*/

   @BeforeEach
   void beforeEach()
   {
      // Currently (2020-05-22) this test is not supported when the VPN connection is active on the local machine.
      if ( NetworkUtilities.isVpnActive() )
      {
         fail( "This test is not supported when a VPN connection is active on the local network interface." );
      }

      // Start up the test server.
      channelAccessTestServer = EpicsChannelAccessTestServer.start();
      
      // Start up the context which should start up a repeater instance which will receive beacons
      // from the test server.
      context = new Context();
   }

   @AfterEach
   void afterEach()
   {
      // Shutdown the test server. Should stop it emitting beacons etc.
      logger.info( "Shutting down EPICSChannelAccessTestServer." );
      channelAccessTestServer.shutdown();
      
      // Shut down the context.
      logger.info( "Closing context." );
      context.close ();
   }

   @Test
   void testWaitForValue()
   {
      try ( final Channel<Integer> channel = context.createChannel ("simple", Integer.class) )
      {
         channel.connect();
         channel.put( 0 );

         final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
         try ( Channel<Integer> channelAlias = context.createChannel ("simple", Integer.class) )
         {
            channelAlias.connect();

            executorService.schedule( () -> {
               channel.put( 12 );
               logger.info("Value set to 12");
            }, 2, TimeUnit.SECONDS);
            
            final long start = System.currentTimeMillis();
            Channels.waitForValue( channelAlias, 12 );
            final long end = System.currentTimeMillis ();
            logger.info("value reached within " + (end - start));
            final long elapsedTime = end - start;
            // Check whether the time waited was approximately 2 seconds
            assertTrue( elapsedTime > 1900 && elapsedTime < 2100 );
         }
         finally
         {
            executorService.shutdown();
         }
      }
   }

   @Test
   void testCreateChannelsWithDescriptors()
   {
      final List<ChannelDescriptor<?>> descriptors = new ArrayList<>();
      descriptors.add( new ChannelDescriptor<>( "simple", String.class ) );
      descriptors.add( new ChannelDescriptor<>( "adc01", Double.class ) );
      final List<Channel<?>> channels = Channels.create( context, descriptors );
      assertEquals( 2, channels.size () );
   }

   @Test
   void testAnnotations()
   {
      final AnnotatedClass annotatedTestClass = new AnnotatedClass();

      // Connect annotated channels
      Channels.create( context, annotatedTestClass );

      annotatedTestClass.getDoubleChannel().put (1.0 );
      annotatedTestClass.getDoubleChannel().put (10.0 );
      // Use of startsWith as it is a double channel and will return something like 10.0 or 10.000 depending on the precision
      assertTrue( annotatedTestClass.getStringChannel ().get ().startsWith ("10") );
      assertEquals(2, annotatedTestClass.getStringChannels ().size() );

      // Close annotated channels
      Channels.close( annotatedTestClass );
   }

   @Test
   void testAnnotationsMacro()
   {
      final Map<String, String> macros = new HashMap<>();
      macros.put( "MACRO1", "01");
      final AnnotatedClassMacros object = new AnnotatedClassMacros ();
      Channels.create (context, object, macros);

      object.getDoubleChannel ().get ();
      assertEquals (ConnectionState.CONNECTED, object.getDoubleChannel ().getConnectionState ());

      // Close annotated channels
      final Channel<Double> channel = object.getDoubleChannel (); // we have to buffer the channel object as the close will null the objects attribute
      Channels.close (object);

      assertEquals (ConnectionState.CLOSED, channel.getConnectionState ());
   }

   static class AnnotatedClass
   {
      @CaChannel( name = "adc01", type = Double.class )
      private Channel<Double> doubleChannel;

      @CaChannel( name = "adc01", type = String.class )
      private Channel<String> stringChannel;

      @CaChannel( name = { "adc01", "simple" }, type = String.class )
      private List<Channel<String>> stringChannels;

      Channel<Double> getDoubleChannel()
      {
         return doubleChannel;
      }

      Channel<String> getStringChannel()
      {
         return stringChannel;
      }

      List<Channel<String>> getStringChannels()
      {
         return stringChannels;
      }
   }

   static class AnnotatedClassMacros
   {
      @CaChannel( name = "adc${MACRO1}", type = Double.class )
      private Channel<Double> doubleChannel;

      Channel<Double> getDoubleChannel()
      {
         return doubleChannel;
      }
   }

/*- Private methods ---------------------------------------------------------*/
/*- Nested Classes -----------------------------------------------------------*/

}
