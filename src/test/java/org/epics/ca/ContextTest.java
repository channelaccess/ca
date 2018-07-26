package org.epics.ca;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;
import java.util.stream.Stream;


import org.epics.ca.Channel;
import org.epics.ca.Constants;
import org.epics.ca.Context;
import org.junit.jupiter.api.Test;

class ContextTest
{

   private final String TEST_CHANNEL_NAME = "test01";

   @Test
   void testContext()
   {
      try ( Context context = new Context () )
      {
         assertNotNull (context);
      }
   }

   @Test
   void testContextProperties()
   {
      try ( Context context = new Context (null) )
      {
         fail ("null properties accepted");
      }
      catch ( IllegalArgumentException iae )
      {
         // expected
      }

      Properties props = new Properties ();
      try ( Context context = new Context (props) )
      {
         assertNotNull (context);
      }
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

      Context context = new Context ();
      context.close ();

      // duplicate close() is OK
      context.close ();

      try
      {
         context.createChannel (TEST_CHANNEL_NAME, Double.class);
         fail ("creation of a channel on closed context must fail");
      }
      catch ( RuntimeException rt )
      {
         // expected
      }

      try
      {
         context.createChannel (TEST_CHANNEL_NAME, Double.class, Constants.CHANNEL_PRIORITY_DEFAULT);
         fail ("creation of a channel on closed context must fail");
      }
      catch ( RuntimeException rt )
      {
         // expected
      }
   }
}
