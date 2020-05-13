package org.epics.ca;

import java.util.Arrays;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import org.epics.ca.data.Alarm;
import org.epics.ca.data.Control;
import org.epics.ca.data.Graphic;
import org.epics.ca.data.GraphicEnum;
import org.epics.ca.data.Timestamped;


public class Example
{

   public static void main( String[] args )
   {
      System.out.println( "NOTE: To run this example you need to first start the Epics Channel Access Test Server running..." );

      final Properties properties = new Properties();
      properties.setProperty( "CA_DEBUG", "0" );

      properties.setProperty( Context.Configuration.EPICS_CA_ADDR_LIST.toString(), "127.0.0.1" );
      properties.setProperty( Context.Configuration.EPICS_CA_AUTO_ADDR_LIST.toString(), "NO" );

      try ( Context context = new Context ( properties ) )
      {
         // 1.0 Create a Channel
         System.out.print( "Creating a channel... " );
         final Channel<Double> adc = context.createChannel ("adc01", Double.class);
         System.out.println( "OK." );

         // 2.0 Add a ConnectionListener
         System.out.print( "Adding a connection listener... " );
         final Listener connectionListener = adc.addConnectionListener (( channel, state ) -> System.out.println (channel.getName () + " is connected? " + state));
         System.out.println( "OK." );

         // 2.1 Remove a ConnectionListener.
         // Note: this is achieved automatically if the listener is created using a try-catch-resources construct.
         System.out.print( "Removing a connection listener... " );
         connectionListener.close();
         System.out.println( "OK." );

         // 2.2 Add an AccessRightsListener
         System.out.print( "Adding an access rights listener... " );
         final  Listener accessRightsListener = adc.addAccessRightListener (( channel, rights ) -> System.out.println (channel.getName () + " is rights? " + rights));
         System.out.println( "OK." );

         // 2.3 Remove an AccessRightsListener.
         // Note: this is achieved automatically if the listener is created using a try-catch-resources construct.
         System.out.print( "Removing an access rights listener... " );
         accessRightsListener.close();
         System.out.println( "OK." );

         // 3.0 Connect asynchronously to the channel.
         // Wait until connected or TimeoutException
         System.out.print( "Connecting asynchronously... " );
         adc.connectAsync().get();
         System.out.println( "OK." );

         // 4.0 Asynchronously put a floating point value to the channel.
         System.out.print( "Putting without waiting for confirmation... " );
         adc.putNoWait (3.11 );
         System.out.println( "OK." );

         // 4.1 Asynchronously put a floating point value to the channel. Wait for completion.
         System.out.print( "Putting FLOAT asynchronously, then waiting for completion... " );
         final CompletableFuture<Status> fp = adc.putAsync (12.8 );
         System.out.println( "OK. Data returned: '" + fp.get () + "'." );

         // 5.0 Asynchronously get a floating point value from the channel.
         System.out.print( "Getting DOUBLE asynchronously, then waiting for completion... " );
         final CompletableFuture<Double> ffd = adc.getAsync();
         System.out.println( "OK. Data returned: '" + ffd.get() + "'." );

         System.out.print( "Getting DOUBLE asynchronously with ALARM information, then waiting for completion... " );
         final CompletableFuture<Alarm<Double>> fts = adc.getAsync( Alarm.class );
         final Alarm<Double> da = fts.get ();
         System.out.println( "OK. Data returned: '" + da.getValue() + " " + da.getAlarmStatus() + " " + da.getAlarmSeverity () + "'." );

         System.out.print( "Getting DOUBLE asynchronously with TIMESTAMP information, then waiting for completion... " );
         final CompletableFuture<Timestamped<Double>> ftt = adc.getAsync (Timestamped.class);
         final Timestamped<Double> dt = ftt.get ();
         System.out.println( "OK. Data returned: '" + dt.getValue() + " " + dt.getAlarmStatus() + " " + dt.getAlarmSeverity () + " " + new Date (dt.getMillis ()) + "'." );

         System.out.print( "Getting DOUBLE asynchronously with GRAPHIC information, then waiting for completion... " );
         final CompletableFuture<Graphic<Double, Double>> ftg = adc.getAsync( Graphic.class );
         final Graphic<Double, Double> dg = ftg.get ();
         System.out.println( "OK. Data returned: '" + dg.getValue() + " " + dg.getAlarmStatus() + " " + dg.getAlarmSeverity() + " " + dg.getLowerDisplay() + " " + dg.getUpperDisplay() + "'." );

         System.out.print( "Getting DOUBLE asynchronously with CONTROL information, then waiting for completion... " );
         final CompletableFuture<Control<Double, Double>> ftc = adc.getAsync( Control.class );
         final Control<Double, Double> dc = ftc.get ();
         System.out.println( "OK. Data returned: '" + dc.getValue () + " " + dc.getAlarmStatus() + " " + dc.getAlarmSeverity() + " " + dc.getLowerControl()  + " " + dc.getUpperControl() + "'." );

         System.out.print( "Getting DOUBLE ARRAY asynchronously, then waiting for completion... " );
         final Channel<double[]> adca = context.createChannel("adc01", double[].class).connectAsync().get();
         final CompletableFuture<double[]> ffda = adca.getAsync();
			System.out.println( "OK. Data returned: '" + Arrays.toString( ffda.get()) + "'." );

         System.out.print( "Getting DOUBLE ARRAY asynchronously, with GRAPHIC information, then waiting for completion... " );
         final CompletableFuture<Graphic<double[], Double>> ftga = adca.getAsync( Graphic.class );
         final Graphic<double[], Double> dga = ftga.get();
			System.out.println( "OK. Data returned: '" + Arrays.toString( dga.getValue()) + " " + dga.getAlarmStatus() + " " + dga.getAlarmSeverity() + " " + dg.getLowerDisplay() + " " + dg.getUpperDisplay() + "'." );

         System.out.print( "Getting SHORT asynchronously, then waiting for completion... " );
         final Channel<Short> ec = context.createChannel ("enum", Short.class ).connectAsync().get();
         final CompletableFuture<Short> fec = ec.getAsync ();
         System.out.println( "OK. Data returned: '" + fec.get () + " " + ec.get() + "'." );

         System.out.print( "Getting GRAPHIC ENUM asynchronously, then waiting for completion... " );
         final CompletableFuture<GraphicEnum> ftec = ec.getAsync( GraphicEnum.class );
         final GraphicEnum dtec = ftec.get();
         System.out.println( "OK. Data returned: '" + dtec.getValue() + " " + Arrays.toString( dtec.getLabels () ) + "'." );

         System.out.print( "Getting GRAPHIC ENUM synchronously, then waiting for completion... " );
         final GraphicEnum ss = ec.get( GraphicEnum.class );
         System.out.println( "OK. Data returned: '" + Arrays.toString( ss.getLabels() ) + "'." );

         System.out.print( "Putting SHORT without waiting for confirmation... " );
         ec.putNoWait( (short) (99 ) );
         System.out.println( "OK." );

         System.out.print( "Monitoring DOUBLE, waiting for notifications..." );
         final Monitor<Double> mon = adc.addValueMonitor(x -> System.out.println( "OK. Data returned: '" + x + "'." ) );
         Thread.sleep(2000);

         System.out.print( "Closing Monitor... " );
         mon.close();
         System.out.println( "OK." );

         System.out.print( "Monitoring DOUBLE, requesting TIMESTAMP information, waiting for notifications... " );
         final Monitor<Timestamped<Double>> mon2 =
            adc.addMonitor (
                  Timestamped.class,
                  value -> {
                     if ( value != null )
                     {
                        System.out.println( "OK. Data returned: '" + new Date (value.getMillis ()) + " / " + value.getValue () + "'."  );
                     }
                  }
            );
         Thread.sleep (2000);

         // sync create channel and connect
         System.out.print( "Creating DOUBLE channel... " );
         final Channel<Double> adc4 = context.createChannel ("adc04", Double.class );
         System.out.println( "OK." );

         // async wait
         // NOTE: thenAccept vs thenAcceptAsync
         System.out.print( "Using ThenAccept... " );
         adc4.connectAsync ().thenAccept ( ( channel ) -> System.out.println( "OK :'" + channel.getName () + "' connected." ) );
         System.out.println( "OK." );

         System.out.print( "Creating INTEGER channel..." );
         final Channel<Integer> adc2 = context.createChannel("adc02", Integer.class);
         System.out.println( "OK." );

         System.out.print( "Creating STRING channel..." );
         final Channel<String> adc3 = context.createChannel("adc03", String.class);
         System.out.println( "OK." );

         // wait for all channels to connect
         System.out.print( "Waiting for MULTIPLE heterogenuous channels to connect..." );
         CompletableFuture.allOf( adc2.connectAsync (), adc3.connectAsync () ).thenAccept (( v ) -> System.out.println ( "OK: ALL connected" ) );

         // sync get
         System.out.print( "Getting DOUBLE synchronously... " );
         double dv = adc.get ();
         System.out.println( "OK." );

         // sync get w/ timestamp
         System.out.print( "Getting DOUBLE with TIMESTAMP information... " );
         final Timestamped<Double> ts = adc.get (Timestamped.class);
         double dvt = ts.getValue ();
         long millis = ts.getMillis ();
         System.out.println( "OK." );

         // async get
         final CompletableFuture<Double> fd = adc.getAsync();
         // ... in some other thread
         final double dv2 = fd.get ();

         final CompletableFuture<Timestamped<Double>> ftd = adc.getAsync( Timestamped.class );
         // ... in some other thread
         Timestamped<Double> td = ftd.get ();

         final CompletableFuture<Status> sf = adc.putAsync (12.8 );
         boolean putOK = sf.get ().isSuccessful ();

         // create monitor
         System.out.print( "Monitoring DOUBLE using try-with-resources... "  );
         Monitor<Double> ref;
         try( Monitor<Double> monitor = adc.addValueMonitor( v -> System.out.println( "OK.  Data returned: '" + v + "'." ) ) )
         {
            ref = monitor;
         }
         // Should have way of checking that monitor closed here, but currently monitor has no method to check this.
      }
      catch( Exception ex )
      {
         System.out.println ( "\nThe example program FAILED, with the following exception: " + ex );
      }
      System.out.println( "\nThe example program SUCCEEDED, and ran to completion." );
   }
}
