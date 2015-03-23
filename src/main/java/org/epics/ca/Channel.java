package org.epics.ca;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.epics.ca.data.Metadata;

public interface Channel<T> extends AutoCloseable {
	
	public String getName();
	
	public CompletableFuture<Channel<T>> connect();
	
	// TODO connection listener, ACL listener
	
	//
	// sync methods, exception is thrown on failure
	// 
	
	public T get();
	public void put(T value); // best-effort put
	
	//
	// async methods, exception is reported via CompletableFuture
	//
	public CompletableFuture<T> getAsync();
	public CompletableFuture<Status> putAsync(T value);
	
	public <MT extends Metadata<T>> MT get(Class<MT> clazz);
	public <MT extends Metadata<T>> CompletableFuture<MT> getAsync(Class<MT> clazz);
	
	//
	// monitors
	//

	// value only, queueSize = 2, called from its own thread
	public Monitor<T> createMonitor(final Consumer<? extends T> handler); 

	// queueSize = 2, called from its own thread
	public <MT extends Metadata<T>> Monitor<MT> createMonitor(Class<MT> clazz, final Consumer<? extends MT> handler); 
	
	//
	// misc
	//
	
    // get channel properties, e.g. native type, host, etc.
	Map<String, Object> getProperties();

}
