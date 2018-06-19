package org.epics.ca.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


import org.epics.ca.Channel;
import org.epics.ca.ChannelDescriptor;
import org.epics.ca.Channels;
import org.epics.ca.ConnectionState;
import org.epics.ca.Context;
import org.epics.ca.annotation.CaChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class ChannelsTest
{
   static final double DELTA = 1e-10;

   private Context context;
   private CAJTestServer server;

   @BeforeEach
   protected void setUp() throws Exception
   {
      server = new CAJTestServer ();
      server.runInSeparateThread ();
      context = new Context ();
   }

   @AfterEach
   protected void tearDown() throws Exception
   {
      context.close ();
      server.destroy ();
   }

   @Test
   public void testWait()
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
   public void testCreateChannels()
   {
      List<ChannelDescriptor<?>> descriptors = new ArrayList<> ();
      descriptors.add (new ChannelDescriptor<String> ("simple", String.class));
      descriptors.add (new ChannelDescriptor<Double> ("adc01", Double.class));
      List<Channel<?>> channels = Channels.create (context, descriptors);
      assertTrue (channels.size () == 2);
   }

   @Test
   public void testAnnotations()
   {
      AnnotatedClass test = new AnnotatedClass ();

      // Connect annotated channels
      Channels.create (context, test);

      test.getDoubleChannel ().put (1.0);
      test.getDoubleChannel ().put (10.0);
      // Use of startsWith as it is a double channel and will return something like 10.0 or 10.000 depending on the precision
      assertTrue (test.getStringChannel ().get ().startsWith ("10"));

      assertTrue (test.getStringChannels ().size () == 2);

      // Close annotated channels
      Channels.close (test);
   }

   @Test
   public void testAnnotationsMacro()
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

      public Channel<Double> getDoubleChannel()
      {
         return doubleChannel;
      }

      public Channel<String> getStringChannel()
      {
         return stringChannel;
      }

      public List<Channel<String>> getStringChannels()
      {
         return stringChannels;
      }

   }

   class AnnotatedClassMacros
   {
      @CaChannel( name = "adc${MACRO1}", type = Double.class )
      private Channel<Double> doubleChannel;

      public Channel<Double> getDoubleChannel()
      {
         return doubleChannel;
      }

   }
}
