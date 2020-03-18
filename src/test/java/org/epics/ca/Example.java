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
      System.out.println( "NOTE: To run this example you need to first start the CAJ Test Server running..." );

      final Properties properties = new Properties();
      properties.setProperty( Context.Configuration.EPICS_CA_ADDR_LIST.toString(), "localhost" );

      try ( Context context = new Context ( properties ) )
      {
         final Channel<Double> adc = context.createChannel ("adc01", Double.class);

         // add connection listener
         final Listener cl = adc.addConnectionListener (( channel, state ) -> System.out.println (channel.getName () + " is connected? " + state));
         // remove listener, or use try-catch-resources
         cl.close();

         final  Listener cl2 = adc.addAccessRightListener (( channel, rights ) -> System.out.println (channel.getName () + " is rights? " + rights));
         // remove listener, or use try-catch-resources
         cl2.close();

         // wait until connected or TimeoutException
         adc.connectAsync().get();
         adc.putNoWait (3.11 );

         final CompletableFuture<Status> fp = adc.putAsync (12.8 );
         System.out.println (fp.get ());

         final CompletableFuture<Double> ffd = adc.getAsync ();
         System.out.println (ffd.get ());

         final CompletableFuture<Alarm<Double>> fts = adc.getAsync (Alarm.class);
         final Alarm<Double> da = fts.get ();
         System.out.println (da.getValue () + " " + da.getAlarmStatus () + " " + da.getAlarmSeverity ());

         final CompletableFuture<Timestamped<Double>> ftt = adc.getAsync (Timestamped.class);
         final Timestamped<Double> dt = ftt.get ();
         System.out.println (dt.getValue () + " " + dt.getAlarmStatus () + " " + dt.getAlarmSeverity () + " " + new Date (dt.getMillis ()));

         final CompletableFuture<Graphic<Double, Double>> ftg = adc.getAsync (Graphic.class);
         final Graphic<Double, Double> dg = ftg.get ();
         System.out.println (dg.getValue () + " " + dg.getAlarmStatus () + " " + dg.getAlarmSeverity ());

         final CompletableFuture<Control<Double, Double>> ftc = adc.getAsync (Control.class);
         final Control<Double, Double> dc = ftc.get ();
         System.out.println (dc.getValue () + " " + dc.getAlarmStatus () + " " + dc.getAlarmSeverity ());

         final Channel<double[]> adca = context.createChannel("adc01", double[].class).connectAsync().get();

         final CompletableFuture<double[]> ffda = adca.getAsync();
			System.out.println(Arrays.toString(ffda.get()));

         final CompletableFuture<Graphic<double[], Double>> ftga = adca.getAsync( Graphic.class );
         final Graphic<double[], Double> dga = ftga.get();
			System.out.println(Arrays.toString(dga.getValue()) + " " + dga.getAlarmStatus() + " " + dga.getAlarmSeverity());

         final Channel<Short> ec = context.createChannel ("enum", Short.class).connectAsync ().get ();
         final CompletableFuture<Short> fec = ec.getAsync ();
         System.out.println (fec.get ());

         final Short s = ec.get ();
         System.out.println (s);

         final CompletableFuture<GraphicEnum> ftec = ec.getAsync (GraphicEnum.class);
         final GraphicEnum dtec = ftec.get ();
         System.out.println (dtec.getValue () + " " + Arrays.toString (dtec.getLabels ()));

         final GraphicEnum ss = ec.get (GraphicEnum.class);
         System.out.println (Arrays.toString (ss.getLabels ()));
         ec.putNoWait ((short) (s + 1));

         final Monitor<Double> mon = adc.addValueMonitor( System.out::println );
         Thread.sleep(10000);
         mon.close();

         final Monitor<Timestamped<Double>> mon2 =
            adc.addMonitor (
                  Timestamped.class,
                  value -> {
                     if ( value != null )
                        System.out.println (new Date (value.getMillis ()) + " / " + value.getValue ());
                  }
            );
         Thread.sleep (5000);

         // sync create channel and connect
         final Channel<Double> adc4 = context.createChannel ("adc04", Double.class);

         // async wait
         // NOTE: thenAccept vs thenAcceptAsync
         adc4.connectAsync ().thenAccept ( ( channel ) -> System.out.println( channel.getName () + " connected"));

         final Channel<Integer> adc2 = context.createChannel ("adc02", Integer.class);
         final Channel<String> adc3 = context.createChannel ("adc03", String.class);

         // wait for all channels to connect
         CompletableFuture.allOf( adc2.connectAsync (), adc3.connectAsync () ).
               thenAccept (( v ) -> System.out.println ("all connected"));

         // sync get
         double dv = adc.get ();

         // sync get w/ timestamp
         final Timestamped<Double> ts = adc.get (Timestamped.class);
         double dvt = ts.getValue ();
         long millis = ts.getMillis ();

         // best-effort put
         adc.putNoWait (12.3 );

         // async get
         final CompletableFuture<Double> fd = adc.getAsync ();
         // ... in some other thread
         final double dv2 = fd.get ();

         final CompletableFuture<Timestamped<Double>> ftd = adc.getAsync (Timestamped.class);
         // ... in some other thread
         Timestamped<Double> td = ftd.get ();

         final CompletableFuture<Status> sf = adc.putAsync (12.8 );
         boolean putOK = sf.get ().isSuccessful ();

         // create monitor
         final Monitor<Double> monitor = adc.addValueMonitor (System.out::println);
         monitor.close ();   // try-catch-resource can be used

         final  Monitor<Timestamped<Double>> monitor2 =
            adc.addMonitor (
                  Timestamped.class,
                  System.out::println
            );
      }
      catch( Exception ex )
      {
         System.out.println ( "Oh dear, the example generated an exception: " + ex );
      }

      System.out.println( "The example program ran to completion." );
   }
}
