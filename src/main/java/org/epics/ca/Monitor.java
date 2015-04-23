package org.epics.ca;

import com.lmax.disruptor.dsl.Disruptor;

public interface Monitor<T> extends AutoCloseable {
	Disruptor<T> getDisruptor();

	// suppresses AutoCloseable.close() exception
	@Override
	void close();
}
