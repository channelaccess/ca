package org.epics.ca.data;

// TODO
public interface TimeStamped<T> extends Metadata<T> {
	public long getTimeStamp();
}