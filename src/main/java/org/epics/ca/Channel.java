package org.epics.ca;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.epics.ca.data.Metadata;

import com.lmax.disruptor.dsl.Disruptor;

public interface Channel<T> extends AutoCloseable {
	
	public String getName();
	
	public CompletableFuture<Channel<T>> connect();
	
	//
	// listeners
	//
	
	public Listener addConnectionListener(BiConsumer<Channel<T>, Boolean> handler);
	public Listener addAccessRightListener(BiConsumer<Channel<T>, AccessRights> handler);
	
	
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
	
	// NOTE: "public <MT extends Metadata<T>> MT get(Class<MT> clazz)" would
	// be a better definition, however it raises unchecked warnings in the code
	// and requires explicit casts for monitor APIs
	// the drawback of the signature below is that type of "?" can be different than "MT"
	// (it will raise ClassCastException if not properly used)
	
	@SuppressWarnings("rawtypes")
	public <MT extends Metadata<T>> MT get(Class<? extends Metadata> clazz);
	public <MT extends Metadata<T>> CompletableFuture<MT> getAsync(Class<? extends MT> clazz);
	
	//
	// monitors
	//

	// value only, queueSize = 2, called from its own thread
	public Monitor<T> addValueMonitor(Consumer<? extends T> handler); 

	// queueSize = 2, called from its own thread
	public <MT extends Metadata<T>> Monitor<MT> addMonitor(Class<? extends MT> clazz, Consumer<? extends MT> handler); 

	// advanced monitor, user provides its own Disruptor
	public Monitor<T> addValueMonitor(Disruptor<T> disruptor); 
	public <MT extends Metadata<T>> Monitor<MT> addMonitor(Class<? extends MT> clazz, Disruptor<? extends MT> disruptor);

	//
	// misc
	//
	
    // get channel properties, e.g. native type, host, etc.
	Map<String, Object> getProperties();

}
