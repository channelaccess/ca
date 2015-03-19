package org.epics.ca.test;

import java.util.concurrent.Future;

import org.epics.ca.Channel;
import org.epics.ca.Channel.TimeStamp;


public class ChannelTest {
	
	
	public static void main(String[] args) throws Throwable {
		
		Channel<Double> adc = null; // = new Channel<Double>("adc01");
		
		// sync get
		double value = adc.get();
		
		// sync get w/ timestamp 
		TimeStamp<Double> ts = adc.get(TimeStamp.class);
		value = ts.getValue();
		long millis = ts.getTimeStamp();
		
		// best-effort put
		adc.put(12.3);
		
		// async get
		Future<Double> fd = adc.getAsync();
		
		// in some other thread
		value = fd.get();
		
	}
}
