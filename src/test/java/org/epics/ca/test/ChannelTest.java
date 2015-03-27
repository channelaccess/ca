package org.epics.ca.test;

import java.util.concurrent.CompletableFuture;

import org.epics.ca.Channel;
import org.epics.ca.Context;
import org.epics.ca.Listener;
import org.epics.ca.Monitor;
import org.epics.ca.Status;
import org.epics.ca.data.Timestamped;


public class ChannelTest {

	@SuppressWarnings({ "unused" })
	public static void main(String[] args) throws Throwable {

		try (Context context = new Context())
		{
			Channel<Double> adc = context.createChannel("adc01", Double.class);
			
			// add connection listener
			Listener cl = adc.addConnectionListener((channel, state) -> System.out.println(channel.getName() + "is connected? " + state));
			// remove listener, or use try-catch-resources
			cl.close();	
			
			// wait until connected
			adc.connect().get();
			
			// sync create channel and connect
			Channel<Double> adc4 = context.createChannel("adc02", Double.class).connect().get();
			
			// async wait
			// NOTE: thenAccept vs thenAcceptAsync
			adc.connect().thenAccept((channel) -> System.out.println(channel.getName() + " connected"));
			
			Channel<Integer> adc2 = context.createChannel("adc02", Integer.class);
			Channel<String> adc3 = context.createChannel("adc03", String.class);
			
			// wait for all channels to connect
			CompletableFuture.allOf(adc.connect(), adc2.connect(), adc3.connect()).
				thenAccept((v) -> System.out.println("all connected"));
			
			// sync get
			double dv = adc.get();
			
			// sync get w/ timestamp 
			Timestamped<Double> ts = adc.get(Timestamped.class);
			dv = ts.getValue();
			long millis = ts.getTimeStamp();
			
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
