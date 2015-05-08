package org.epics.ca.util;

public class Holder<T> {
	public T value;
	
	public Holder() {}
	public Holder(T value) { this.value = value; }
}
