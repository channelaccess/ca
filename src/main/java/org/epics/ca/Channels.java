package org.epics.ca;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.epics.ca.annotation.CaChannel;

/**
 * Utility class to create and operate on channel
 */
public class Channels
{

   public static <T> void waitForValue( Channel<T> channel, T value )
   {
      try
      {
         waitForValueAsync (channel, value).get ();
      }
      catch ( InterruptedException | ExecutionException e )
      {
         throw new RuntimeException (e);
      }
   }

   public static <T> void waitForValue( Channel<T> channel, T value, Comparator<T> comparator )
   {
      try
      {
         waitForValueAsync (channel, value, comparator).get ();
      }
      catch ( InterruptedException | ExecutionException e )
      {
         throw new RuntimeException (e);
      }
   }

   public static <T> CompletableFuture<T> waitForValueAsync( Channel<T> channel, T value )
   {
      // Default comparator checking for equality
      Comparator<T> comparator = new Comparator<T> ()
      {
         @Override
         public int compare( T o, T o2 )
         {
            if ( o.equals (o2) )
            {
               return 0;
            }
            return -1;
         }
      };
      return waitForValueAsync (channel, value, comparator);
   }

   public static <T> CompletableFuture<T> waitForValueAsync( Channel<T> channel, T value, Comparator<T> comparator )
   {
      CompletableFuture<T> future = new CompletableFuture<> ();

      final Monitor<T> monitor = channel.addValueMonitor (newValue -> {
         if ( comparator.compare (newValue, value) == 0 )
         {
            future.complete (newValue);
         }
      });
      return future.whenComplete (( v, exception ) -> monitor.close ());
//		return future;
   }

   public static Channel<?> create( Context context, String name )
   {
      return create (context, name, Object.class);
   }

   public static <T> Channel<T> create( Context context, String name, Class<T> type )
   {
      Channel<T> channel = context.createChannel (name, type);
      channel.connect ();
      return channel;
   }

   public static <T> Channel<T> create( Context context, ChannelDescriptor<T> descriptor )
   {
      Channel<T> channel = context.createChannel (descriptor.getName (), descriptor.getType ());
      channel.connect ();
      return channel;
   }

   public static List<Channel<?>> create( Context context, List<ChannelDescriptor<?>> descriptors )
   {
      List<Channel<?>> channels = new ArrayList<> (descriptors.size ());
      List<CompletableFuture<?>> futures = new ArrayList<> (descriptors.size ());
      for ( ChannelDescriptor<?> descriptor : descriptors )
      {
         Channel<?> channel = context.createChannel (descriptor.getName (), descriptor.getType ());
         channels.add (channel);
         futures.add (channel.connectAsync ());
      }
      try
      {
         CompletableFuture.allOf (futures.toArray (new CompletableFuture<?>[ futures.size () ])).get ();
      }
      catch ( InterruptedException | ExecutionException e )
      {
         throw new RuntimeException (e);
      }

      return channels;
   }


   /**
    * Create annotate channels within object
    *
    * @param context Context to create channels with
    * @param object  Object with annotated channels
    */
   public static void create( Context context, Object object )
   {
      create (context, object, new HashMap<String, String> ());

   }

   /**
    * Create annotate channels within object
    *
    * @param context Context to create channels with
    * @param object  Object with annotated channels
    * @param macros  Macros to use to apply to the channel name - macros are specified via ${MACRO}.
    */
   public static void create( Context context, Object object, Map<String, String> macros )
   {
      try
      {
         Class<?> c = object.getClass ();

         // Parse annotations
         List<Field> fieldList = new ArrayList<> ();
         Map<Field, Integer> sizeMap = new HashMap<> (); // Map holding the number of channels that are associated to the field
         List<ChannelDescriptor<?>> descriptorList = new ArrayList<> ();

         for ( Field field : c.getDeclaredFields () )
         {
            CaChannel annotation = field.getAnnotation (CaChannel.class);
            if ( annotation != null )
            {
               if ( annotation.name ().length == 1 && field.getType ().isAssignableFrom (Channel.class) )
               {
                  fieldList.add (field);
                  sizeMap.put (field, 1);
                  descriptorList.add (new ChannelDescriptor<> (format (annotation.name ()[ 0 ], macros), annotation.type (), annotation.monitor ()));
               }
               else if ( annotation.name ().length > 0 && field.getType ().isAssignableFrom (List.class) )
               {
                  fieldList.add (field);
                  sizeMap.put (field, annotation.name ().length);
                  for ( String n : annotation.name () )
                  {
                     descriptorList.add (new ChannelDescriptor<> (format (n, macros), annotation.type (), annotation.monitor ()));
                  }
               }
               else
               {
                  throw new RuntimeException ("Annotation @" + CaChannel.class.getSimpleName () + " not applicable for field '" + field.getName () + "' of type '" + field.getType ().getName () + "'");
               }
            }
         }

         // Create all channels
         List<Channel<?>> channelList = create (context, descriptorList);

         // Set channels
         int ccount = 0;
         for ( int fc = 0; fc < fieldList.size (); fc++ )
         {
            Field f = fieldList.get (fc);
            boolean accessible = f.isAccessible ();
            f.setAccessible (true);
            int fsize = sizeMap.get (f);
            if ( fsize == 1 && f.getType ().isAssignableFrom (Channel.class) )
            { // There might be a list of one element therefore we need the second check
               f.set (object, channelList.get (ccount));
               ccount++;
            }
            else
            {
               List<Channel<?>> list = new ArrayList<Channel<?>> ();
               for ( int i = 0; i < sizeMap.get (f); i++ )
               {
                  list.add (channelList.get (ccount));
                  ccount++;
               }
               f.set (object, list);
            }
            f.setAccessible (accessible);
         }
      }
      catch ( IllegalAccessException e )
      {
         throw new RuntimeException (e);
      }
   }

   public static void close( Object object )
   {
      try
      {
         Class<?> c = object.getClass ();

         for ( Field field : c.getDeclaredFields () )
         {
            CaChannel annotation = field.getAnnotation (CaChannel.class);
            if ( annotation != null )
            {
               if ( field.getType ().isAssignableFrom (Channel.class) )
               {
                  boolean accessible = field.isAccessible ();
                  field.setAccessible (true);
                  ((Channel<?>) field.get (object)).close ();
                  // Set field/attribute value to null
                  field.set (object, null);
                  field.setAccessible (accessible);

               }
               else if ( field.getType ().isAssignableFrom (List.class) )
               {
                  boolean accessible = field.isAccessible ();
                  field.setAccessible (true);
                  @SuppressWarnings( "unchecked" )
                  List<Channel<?>> l = ((List<Channel<?>>) field.get (object));
                  for ( Channel<?> b : l )
                  {
                     b.close ();
                  }
                  // Set field/attribute value to null
                  field.set (object, null);
                  field.setAccessible (accessible);
               }
               else
               {
                  throw new RuntimeException (
                        "Annotation @" + CaChannel.class.getSimpleName () + " not applicable for field '" + field.getName () + "' of type '" + field.getType ().getName () + "'");
               }
            }
         }
      }
      catch ( IllegalAccessException e )
      {
         throw new RuntimeException (e);
      }
   }

   private static String format( String formatString, Map<String, String> macros )
   {

      // Optimization - return formatString if no macros are specified
      if ( macros.isEmpty () )
      {
         return formatString;
      }

      final String fieldStart = "\\$\\{";
      final String fieldEnd = "\\}";

      final String regex = fieldStart + "([^}]+)" + fieldEnd;
      final Pattern pattern = Pattern.compile (regex);

      final Matcher m = pattern.matcher (formatString);
      String result = formatString;
      while ( m.find () )
      {
         String ma = m.group (1);
         String replacement = macros.get (ma);
         if ( replacement == null )
         { // use of an non existing macro
            replacement = fieldStart + ma + fieldEnd;
         }
         result = result.replaceFirst (fieldStart + ma + fieldEnd, replacement);
      }
      return result;
   }

}