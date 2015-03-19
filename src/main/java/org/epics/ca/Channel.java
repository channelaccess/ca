package org.epics.ca;

import java.util.concurrent.Future;

public interface Channel<T> {
	
	// non-finished, placeholder definitions
	public class Status {};
	public interface Data<T> { public T getValue(); };
	public interface Metadata<T> extends Data<T> {};
	public interface TimeStamp<T> extends Metadata<T> { public long getTimeStamp(); };
	
	
	public Future<Void> connect();
	
	public T get();
	public void put(T value);
	
	public Future<T> getAsync();
	public Future<Status> putAsync();
	
	public <VT extends Metadata<T>> VT get(Class<VT> clazz);
	public <VT extends Metadata<T>> Future<VT> getAsync(Class<VT> clazz);
	
}
