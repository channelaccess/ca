package org.epics.ca;

import java.util.concurrent.CompletableFuture;

public interface Channel<T> {
	
	// non-finished, placeholder definitions
	public class Status {};
	public interface Data<T> { public T getValue(); };
	public interface Metadata<T> extends Data<T> {};
	public interface TimeStamped<T> extends Metadata<T> { public long getTimeStamp(); };
	
	
	public CompletableFuture<Void> connect();
	
	public T get();
	public void put(T value);
	
	public CompletableFuture<T> getAsync();
	public CompletableFuture<Status> putAsync();
	
	public <VT extends Metadata<T>> VT get(Class<VT> clazz);
	public <VT extends Metadata<T>> CompletableFuture<VT> getAsync(Class<VT> clazz);
	
}
