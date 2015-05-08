package org.epics.ca;

import org.epics.ca.util.Holder;

import com.lmax.disruptor.dsl.Disruptor;

public interface Monitor<T> extends AutoCloseable {
	
	public static final int VALUE_MASK 	  = 0x01;
	public static final int LOG_MASK   	  = 0x02;
	public static final int ALARM_MASK 	  = 0x04;
	public static final int PROPERTY_MASK = 0x08;

	Disruptor<Holder<T>> getDisruptor();

	// suppresses AutoCloseable.close() exception
	@Override
	void close();
}
