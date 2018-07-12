package org.epics.ca;

import org.epics.ca.annotation.CaChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class ChannelsTest
{
   private Context context;
   private CAJTestServer server;

   @BeforeEach
   void setUp()
   {
      server = new CAJTestServer ();
      server.runInSeparateThread ();
      context = new Context ();
   }

   @AfterEach
   void tearDown()
   {
      context.close ();
      server.destroy ();
   }

   @Test
   void testWait()
   {
      try ( Channel<Integer> channel1 = context.createChannel ("simple", Integer.class) )
      {
         channel1.connect ();
         channel1.put (0);

         try ( Channel<Integer> channel = context.createChannel ("simple", Integer.class) )
         {
            channel.connect ();

            Executors.newSingleThreadScheduledExecutor ().schedule (() -> {
               channel1.put (12);
               System.out.println ("Value set to 12");
            }, 2, TimeUnit.SECONDS);
            long start = System.currentTimeMillis ();
            Channels.waitForValue (channel, 12);
            long end = System.currentTimeMillis ();
            System.out.println ("value reached within " + (end - start));
            long time = end - start;
            // Check whether the time waited was approximately 2 seconds
            assertTrue (time > 1900 && time < 2100);
         }
      }
   }

   @Test
   void testCreateChannels()
   {
      List<ChannelDescriptor<?>> descriptors = new ArrayList<> ();
      descriptors.add (new ChannelDescriptor<> ("simple", String.class));
      descriptors.add (new ChannelDescriptor<> ("adc01", Double.class));
      List<Channel<?>> channels = Channels.create (context, descriptors);
      assertEquals (2, channels.size () );
   }

   @Test
   void testAnnotations()
   {
      AnnotatedClass test = new AnnotatedClass ();

      // Connect annotated channels
      Channels.create (context, test);

      test.getDoubleChannel ().put (1.0);
      test.getDoubleChannel ().put (10.0);
      // Use of startsWith as it is a double channel and will return something like 10.0 or 10.000 depending on the precision
      assertTrue( test.getStringChannel ().get ().startsWith ("10") );
      assertEquals(2, test.getStringChannels ().size () );

      // Close annotated channels
      Channels.close (test);
   }

   @Test
   void testAnnotationsMacro()
   {
      Map<String, String> macros = new HashMap<> ();
      macros.put ("MACRO1", "01");
      AnnotatedClassMacros object = new AnnotatedClassMacros ();
      Channels.create (context, object, macros);

      object.getDoubleChannel ().get ();
      assertEquals (ConnectionState.CONNECTED, object.getDoubleChannel ().getConnectionState ());

      // Close annotated channels
      Channel<Double> channel = object.getDoubleChannel (); // we have to buffer the channel object as the close will null the objects attribute
      Channels.close (object);

      assertEquals (ConnectionState.CLOSED, channel.getConnectionState ());
   }

   class AnnotatedClass
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

   class AnnotatedClassMacros
   {
      @CaChannel( name = "adc${MACRO1}", type = Double.class )
      private Channel<Double> doubleChannel;

      Channel<Double> getDoubleChannel()
      {
         return doubleChannel;
      }

   }
}
