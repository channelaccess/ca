package org.epics.ca.data;

public class Data<T> {

	protected T value;
	
	public T getValue() { return value; }
	public void setValue(T value) { this.value = value; }
}