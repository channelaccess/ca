package org.epics.ca.test;

import java.util.concurrent.CompletableFuture;

import org.epics.ca.Channel;
import org.epics.ca.Monitor;
import org.epics.ca.data.TimeStamped;


public class ChannelTest {

	@SuppressWarnings({ "unused", "null" })
	public static void main(String[] args) throws Throwable {
		
		Channel<Double> adc = null; // = new Channel<Double>("adc01");
		
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

		Monitor<TimeStamped<Double>> monitor2 =
				(Monitor<TimeStamped<Double>>) adc.createMonitor(
								TimeStamped.class, 
								value -> System.out.println(value)
								);
		
	}
}
