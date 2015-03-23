package org.epics.ca.test;

import java.util.concurrent.CompletableFuture;

import org.epics.ca.Channel;
import org.epics.ca.Monitor;
import org.epics.ca.data.TimeStamped;


public class ChannelTest {

	@SuppressWarnings({ "unused", "null" })
	public static void main(String[] args) throws Throwable {
		
		Channel<Double> adc = null; // = new Channel<Double>("adc01");
		
		// wait until connected
		adc.connect().get();
		
		// async wait
		// NOTE: thenAccept vs thenAcceptAsync
		adc.connect().thenAccept((channel) -> System.out.println(channel.getName() + " connected"));
		
		Channel<Double> adc2 = null; // = new Channel<Double>("adc02");
		Channel<Double> adc3 = null; // = new Channel<Double>("adc03");
		
		// wait for all channels to connect
		CompletableFuture.allOf(adc.connect(), adc2.connect(), adc3.connect()).
			thenAccept((v) -> System.out.println("all connected"));
		
		// sync get
		double dv = adc.get();
		
		// sync get w/ timestamp 
		TimeStamped<Double> ts = adc.get(TimeStamped.class);
		dv = ts.getValue();
		long millis = ts.getTimeStamp();
		
		// best-effort put
		adc.put(12.3);
		
		// async get
		CompletableFuture<Double> fd = adc.getAsync();
		// ... in some other thread
		dv = fd.get();
		
		// create monitor
		Monitor<Double> monitor = adc.createMonitor(value -> System.out.println(value));
		monitor.close();	// try-catch-resoirce can be used
		
		// TODO that cast is really annoying here
		Monitor<TimeStamped<Double>> monitor2 =
				(Monitor<TimeStamped<Double>>) adc.createMonitor(
								TimeStamped.class, 
								value -> System.out.println(value)
								);
		
	}
}
