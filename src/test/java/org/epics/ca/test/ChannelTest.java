package org.epics.ca.test;

import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

import org.epics.ca.Channel;
import org.epics.ca.Context;
import org.epics.ca.Monitor;
import org.epics.ca.Status;
import org.epics.ca.data.Alarm;
import org.epics.ca.data.Control;
import org.epics.ca.data.Graphic;
import org.epics.ca.data.GraphicEnum;
import org.epics.ca.data.Timestamped;


public class ChannelTest {

	@SuppressWarnings({ "unused" })
	public static void main(String[] args) throws Throwable {

		try (Context context = new Context())
		{
			Channel<Double> adc = context.createChannel("adc01", Double.class);

			/*
			// add connection listener
			Listener cl = adc.addConnectionListener((channel, state) -> System.out.println(channel.getName() + "is connected? " + state));
			// remove listener, or use try-catch-resources
			cl.close();	
			*/
			
			// wait until connected
			adc.connect().get();
			
			CompletableFuture<Double> ffd = adc.getAsync();
			System.out.println(ffd.get());
			
			CompletableFuture<Alarm<Double>> fts = adc.getAsync(Alarm.class);
			Alarm<Double> da = fts.get();
			System.out.println(da.getValue() + " " + da.getAlarmStatus() + " " + da.getAlarmSeverity());
		
			CompletableFuture<Timestamped<Double>> ftt = adc.getAsync(Timestamped.class);
			Timestamped<Double> dt = ftt.get();
			System.out.println(dt.getValue() + " " + dt.getAlarmStatus() + " " + dt.getAlarmSeverity() + " " + new Date(dt.getMillis()));

			CompletableFuture<Graphic<Double, Double>> ftg = adc.getAsync(Graphic.class);
			Graphic<Double, Double> dg = ftg.get();
			System.out.println(dg.getValue() + " " + dg.getAlarmStatus() + " " + dg.getAlarmSeverity());

			CompletableFuture<Control<Double, Double>> ftc = adc.getAsync(Control.class);
			Control<Double, Double> dc = ftc.get();
			System.out.println(dc.getValue() + " " + dc.getAlarmStatus() + " " + dc.getAlarmSeverity());

			
			/*
			Channel<double[]> adca = context.createChannel("msekoranjaHost:compressExample", double[].class).connect().get();

			CompletableFuture<double[]> ffda = adca.getAsync();
			System.out.println(Arrays.toString(ffda.get()));

			CompletableFuture<Graphic<double[], Double>> ftga = adca.getAsync(Graphic.class);
			Graphic<double[], Double> dga = ftga.get();
			System.out.println(Arrays.toString(dga.getValue()) + " " + dga.getAlarmStatus() + " " + dga.getAlarmSeverity());
			*/
			
			Channel<Short> ec = context.createChannel("enum", Short.class).connect().get();

			CompletableFuture<Short> fec = ec.getAsync();
			System.out.println(fec.get());

			Short s = ec.get();
			System.out.println(s);
			
			CompletableFuture<GraphicEnum> ftec = ec.getAsync(GraphicEnum.class);
			GraphicEnum dtec = ftec.get();
			System.out.println(dtec.getValue() + " " + Arrays.toString(dtec.getLabels()));

			GraphicEnum ss = ec.get(GraphicEnum.class);
			System.out.println(Arrays.toString(ss.getLabels()));
			
			if (true)
				return;

			// sync create channel and connect
			Channel<Double> adc4 = context.createChannel("adc02", Double.class).connect().get();
			
			// async wait
			// NOTE: thenAccept vs thenAcceptAsync
			adc.connect().thenAccept((channel) -> System.out.println(channel.getName() + " connected"));
			
			Channel<Integer> adc2 = context.createChannel("adc02", Integer.class);
			Channel<String> adc3 = context.createChannel("adc03", String.class);
			
			// wait for all channels to connect
			CompletableFuture.allOf(adc2.connect(), adc3.connect()).
				thenAccept((v) -> System.out.println("all connected"));
			
			// sync get
			double dv = adc.get();
			
			// sync get w/ timestamp 
			Timestamped<Double> ts = adc.get(Timestamped.class);
			dv = ts.getValue();
			long millis = ts.getMillis();
			
			// best-effort put
			adc.put(12.3);
			
			// async get
			CompletableFuture<Double> fd = adc.getAsync();
			// ... in some other thread
			dv = fd.get();
			
			CompletableFuture<Timestamped<Double>> ftd = adc.getAsync(Timestamped.class);
			// ... in some other thread
			Timestamped<Double> td = ftd.get();
	
			
			CompletableFuture<Status> sf = adc.putAsync(12.8);
			boolean putOK = sf.get().isSuccessful();
			
			// create monitor
			Monitor<Double> monitor = adc.addValueMonitor(value -> System.out.println(value));
			monitor.close();	// try-catch-resource can be used
			
			Monitor<Timestamped<Double>> monitor2 =
					adc.addMonitor(
									Timestamped.class, 
									value -> System.out.println(value)
									);
			
		}
	}
}
