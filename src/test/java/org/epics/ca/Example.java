package org.epics.ca;

import java.util.Arrays;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import org.epics.ca.data.*;


public class Example
{
   public static void main( String[] args )
   {
      // Note on Java network stack configuration
      // The Java network documentation states that the network configuration settings are
      // read by the JVM only once on JVM startup. To avoid strange startup dependencies
      // the properties should be set either outside the JVM (ideally) or at the very
      // beginning of the program entry point.
      System.setProperty( "java.net.preferIPv4Stack", "true" );
      System.setProperty( "java.net.preferIPv6Stack", "false" );

      // Start the Channel Access Test Server that is used to demonstrate capabilities
      // of the EPICS CA client library.
      final EpicsChannelAccessTestServer epicsChannelAccessTestServer = EpicsChannelAccessTestServer.start();

      // Configure the CA library context and start it.
      final Properties properties = new Properties();
      properties.setProperty( Context.Configuration.EPICS_CA_ADDR_LIST.toString(), "127.0.0.1" );
      properties.setProperty( Context.Configuration.EPICS_CA_AUTO_ADDR_LIST.toString(), "NO" );
      try ( Context context = new Context( properties ) )
      {
         // 1.0 Create a Channel
         System.out.print( "Creating a DOUBLE channel... " );
         final Channel<Double> dblCh = context.createChannel( "adc01", Double.class );
         System.out.println( "OK." );

         // 2.0 Add a ConnectionListener
         System.out.print( "Adding a connection listener... " );
         final Listener connectionListener = dblCh.addConnectionListener (( channel, state ) -> System.out.println (channel.getName () + " is connected? " + state));
         System.out.println( "OK." );

         // 2.1 Remove a ConnectionListener.
         // Note: this is achieved automatically if the listener is created using a try-catch-resources construct.
         System.out.print( "Removing a connection listener... " );
         connectionListener.close();
         System.out.println( "OK." );

         // 2.2 Add an AccessRightsListener
         System.out.print( "Adding an access rights listener... " );
         final  Listener accessRightsListener = dblCh.addAccessRightListener (( channel, rights ) -> System.out.println (channel.getName () + " is rights? " + rights));
         System.out.println( "OK." );

         // 2.3 Remove an AccessRightsListener.
         // Note: this is achieved automatically if the listener is created using a try-catch-resources construct.
         System.out.print( "Removing an access rights listener... " );
         accessRightsListener.close();
         System.out.println( "OK." );

         // 3.0 Connect asynchronously to the channel.
         // Wait until connected or TimeoutException
         System.out.print( "Connecting asynchronously... " );
         dblCh.connectAsync().get();
         System.out.println( "OK." );

         // 4.0 Asynchronously put a DOUBLE value to the channel. Don't wait for confirmation.
         System.out.print( "Putting DOUBLE value asynchronously, without waiting for completion... " );
         dblCh.putNoWait( 3.11 );
         System.out.println( "OK." );

         // 4.1 Asynchronously put a floating point value to the channel. Wait for confirmation.
         System.out.print( "Putting DOUBLE value asynchronously, then waiting for completion... " );
         final CompletableFuture<Status> fp = dblCh.putAsync( 12.8 );
         System.out.println( "OK. Data returned: '" + fp.get() + "'." );

         // 5.0 Asynchronously get a DOUBLE from the channel.
         System.out.print( "Getting DOUBLE value asynchronously, then waiting for completion... " );
         final CompletableFuture<Double> fd = dblCh.getAsync();
         System.out.println( "OK. Data returned: '" + fd.get() + "'." );

         // 5.1 Asynchronously get a DOUBLE with ALARM metadata.
         System.out.print( "Getting DOUBLE value asynchronously with ALARM metadata, then waiting for completion... " );
         final CompletableFuture<Alarm<Double>> fal = dblCh.getAsync( Alarm.class );
         final Alarm<Double> dal = fal.get();
         System.out.println( "OK. Data returned: '" + dal.getValue() + ", " + dal.getAlarmStatus() + ", " + dal.getAlarmSeverity () + "'." );

         // 5.2 Asynchronously get a DOUBLE with TIMESTAMP metadata.
         System.out.print( "Getting DOUBLE value asynchronously with TIMESTAMP metadata, then waiting for completion... " );
         final CompletableFuture<Timestamped<Double>> fts = dblCh.getAsync( Timestamped.class );
         final Timestamped<Double> dts = fts.get();
         System.out.println( "OK. Data returned: '" + dts.getValue() + ", " + dts.getAlarmStatus() + ", " + dts.getAlarmSeverity() + ", " + new Date( dts.getMillis ()) + "'." );

         // 5.3 Asynchronously get a DOUBLE with GRAPHIC metadata.
         System.out.print( "Getting DOUBLE value asynchronously with GRAPHIC metadata, then waiting for completion... " );
         final CompletableFuture<Graphic<Double, Double>> fgr = dblCh.getAsync( Graphic.class );
         final Graphic<Double, Double> dgr = fgr.get();
         System.out.println( "OK. Data returned: '" + dgr.getValue() + ", " + dgr.getAlarmStatus() + ", " + dgr.getAlarmSeverity() + ", " + dgr.getLowerDisplay() + ", " + dgr.getUpperDisplay() + "'." );

         // 5.4 Asynchronously get a DOUBLE with CONTROL metadata.
         System.out.print( "Getting DOUBLE value asynchronously with CONTROL metadata, then waiting for completion... " );
         final CompletableFuture<Control<Double, Double>> fc = dblCh.getAsync( Control.class );
         final Control<Double, Double> dc = fc.get();
         System.out.println( "OK. Data returned: '" + dc.getValue () + ", " + dc.getAlarmStatus() + ", " + dc.getAlarmSeverity() + ", " + dc.getLowerControl()  + ", " + dc.getUpperControl() + "'." );

         // 6.0 Asynchronously get a DOUBLE ARRAY from a newly created channel.
         System.out.print( "Getting DOUBLE ARRAY value asynchronously, then waiting for completion... " );
         final Channel<double[]> dblArrCh = context.createChannel( "adc01", double[].class).connectAsync().get();
         final CompletableFuture<double[]> fda = dblArrCh.getAsync();
			System.out.println( "OK. Data returned: '" + Arrays.toString( fda.get()) + "'." );

         // 6.1 Asynchronously get a DOUBLE ARRAY with GRAPHIC metadata.
         System.out.print( "Getting DOUBLE ARRAY value asynchronously, with GRAPHIC metadata, then waiting for completion... " );
         final CompletableFuture<Graphic<double[], Double>> fdagr = dblArrCh.getAsync( Graphic.class );
         final Graphic<double[], Double> dagr = fdagr.get();
			System.out.println( "OK. Data returned: '" + Arrays.toString( dagr.getValue()) + ", " + dagr.getAlarmStatus() + ", " + dagr.getAlarmSeverity() + ", " + dagr.getLowerDisplay() + " " + dagr.getUpperDisplay() + "'." );

         // 7.1 Asynchronously get a GRAPHIC ENUM from channel of underlying type 'DBR_Enum.type'.
         System.out.print( "Getting GRAPHIC ENUM value asynchronously, then waiting for completion... " );
         final Channel<Short> enumCh = context.createChannel( "enum", Short.class ).connectAsync().get();
         final CompletableFuture<GraphicEnum> fge = enumCh.getAsync( GraphicEnum.class );
         final GraphicEnum ge = fge.get();
         System.out.println( "OK. Data returned: '" + ge.getValue() + ", " + Arrays.toString( ge.getLabels() ) + "'." );

         // 7.2 Asynchronously get a GRAPHIC ENUM ARRAY from channel of underlying type 'DBR_Enum.type'.
         System.out.print( "Getting GRAPHIC ENUM ARRAY value asynchronously, then waiting for completion... " );
         final Channel<short[]> enumArrCh = context.createChannel( "enum", short[].class ).connectAsync().get();
         final CompletableFuture<GraphicEnumArray> fgea = enumArrCh.getAsync( GraphicEnumArray.class );
         final GraphicEnumArray gea = fgea.get();
         System.out.println( "OK. Data returned: '" + Arrays.toString( gea.getValue() ) + ", " + Arrays.toString( gea.getLabels () ) + "'." );

         // 8.0 Synchronously get a GRAPHIC ENUM from channel of underlying type 'DBR_Enum.type'.
         System.out.print( "Getting GRAPHIC ENUM value synchronously, then waiting for completion... " );
         final GraphicEnum ges = enumCh.get( GraphicEnum.class );
         System.out.println( "OK. Data returned: '" + Arrays.toString( ges.getLabels() ) + "'." );

         // 9.0 Asynchronously put a SHORT value to channel of underlying type 'DBR_Enum.type'. Don't wait for confirmation.
         System.out.print( "Putting SHORT value asynchronously, without waiting for completion... " );
         enumCh.putNoWait( (short) 99 );
         System.out.println( "OK." );

         // 10.0 Monitor a DOUBLE.
         System.out.print( "Monitoring DOUBLE, waiting for notifications..." );
         final Monitor<Double> mon = dblCh.addValueMonitor(x -> System.out.println( "OK. Data returned: '" + x + "'." ) );
         // Sleep here to allow monitor information to be posted before moving on to other examples.
         Thread.sleep(100);

         // 10.1 Close a Monitor.
         System.out.print( "Closing Monitor... " );
         mon.close();
         System.out.println( "OK." );

         // 10.2 Monitor a DOUBLE with ALARM information.
         System.out.print( "Monitoring DOUBLE, requesting ALARM information, waiting for notifications... " );
         final Monitor<Alarm<Double>> mon3 = dblCh.addMonitor ( Alarm.class, value -> {
               if ( value != null )
               {
                  System.out.println( "OK. Data returned: '" + value.getAlarmStatus() + ", " + value.getAlarmSeverity() + ", " + value.getValue () + "'."  );
               }
            }
         );
         // Sleep here to allow monitor information to be posted before moving on to other examples.
         Thread.sleep(100);

         // 10.3 Monitor a DOUBLE with TIMESTAMP information.
         System.out.print( "Monitoring DOUBLE, requesting TIMESTAMP information, waiting for notifications... " );
         final Monitor<Timestamped<Double>> mon2 = dblCh.addMonitor ( Timestamped.class, value -> {
               if ( value != null )
               {
                  System.out.println( "OK. Data returned: '" + value.getAlarmStatus() + ", " + value.getAlarmSeverity() + ", " + new Date (value.getMillis ()) + ", " + value.getValue () + "'."  );
               }
            }
         );
         // Sleep here to allow monitor information to be posted before moving on to other examples.
         Thread.sleep(100);

         // 11.0 Create a channel, asynchronously connect to it.
         System.out.print( "Creating DOUBLE channel... " );
         final Channel<Double> dblSyncCh = context.createChannel ("adc04", Double.class );
         System.out.println( "OK." );
         System.out.print( "Using connectAsync followed by ThenAccept... " );
         dblSyncCh.connectAsync ().thenAccept( (chan) -> System.out.println( "OK. Channel: '" + chan.getName () + ", connection state: " + chan.getConnectionState() + "." ) );
         // Sleep here to allow information to be posted before moving on to other examples.
         Thread.sleep(100);

         // 12.0 Create channels of mixed types and wait for them all to connect.
         System.out.print( "Creating an INTEGER channel... " );
         final Channel<Integer> intCh = context.createChannel("adc02", Integer.class );
         System.out.println( "OK." );

         System.out.print( "Creating a STRING channel... " );
         final Channel<String> strCh = context.createChannel("adc03", String.class );
         System.out.println( "OK." );

         System.out.print( "Waiting for MULTIPLE channels of MIXED types to connect... " );
         CompletableFuture<?> f = CompletableFuture.allOf( intCh.connectAsync (), strCh.connectAsync () ).thenAccept (( v ) -> System.out.println ( "OK: ALL channels of MIXED types connected." ) );
         f.get();

         // 13.0 Synchronously get a DOUBLE from the channel.
         System.out.print( "Getting DOUBLE synchronously... " );
         double dv = dblCh.get ();
         System.out.println( "OK. Data returned: '" + dv + "'." );

         // 13.1 Synchronously get a DOUBLE from the channel with ALARM information
         System.out.print( "Getting DOUBLE synchronously with ALARM information... " );
         final Alarm<Double> dval = dblCh.get( Alarm.class );
         System.out.println( "OK. Data returned: '" + dval.getValue() + ", " + dval.getAlarmStatus() + ", " + dval.getAlarmSeverity() + "'." );

         // 13.1 Synchronously get a DOUBLE from the channel with TIMESTAMP information
         System.out.print( "Getting DOUBLE synchronously with TIMESTAMP information... " );
         final Timestamped<Double> dvts = dblCh.get( Timestamped.class );
         System.out.println( "OK. Data returned: '" + dvts.getValue() + ", " + dvts.getAlarmStatus() + ", " + dvts.getAlarmSeverity() + ", " + new Date( dvts.getMillis ()) + "'." );

         // 14.0 Monnitor a DOUBLE using try-with-resourced
         System.out.print( "Monitoring DOUBLE using try-with-resources... "  );
         try ( final Monitor<Double> monitor = dblCh.addValueMonitor( v -> System.out.println( "OK. Data returned: '" + v + "'." ) ) )
         {
            // Sleep here to allow monitor information to be posted before moving on to other examples.
            Thread.sleep(100);
         }

         // In the future the library should probably introduce some way of checking that the
         // monitor is closed, but currently the interface has nothing to support this.
      }
      catch( Exception ex )
      {
         System.out.println ( "\nThe example program FAILED, with the following exception: " + ex );
      }
      finally
      {
         System.out.println();
         epicsChannelAccessTestServer.shutdown();
      }
      System.out.println( "\nThe example program SUCCEEDED, and ran to completion." );
   }
}
