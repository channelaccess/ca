package org.epics.ca;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Utility class to create and operate on channel
 */
public class Channels {
	
	public static <T> void waitForValue(Channel<T> channel, T value) {
		try {
			waitForValueAsync(channel, value).get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static <T> void waitForValue(Channel<T> channel, T value, Comparator<T> comparator) {
		try {
			waitForValueAsync(channel, value, comparator).get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	public static <T> CompletableFuture<T> waitForValueAsync(Channel<T> channel, T value){
		// Default comparator checking for equality
		Comparator<T> comparator = new Comparator<T>() {
			@Override
			public int compare(T o, T o2) {
				if (o.equals(o2)) {
					return 0;
				}
				return -1;
			}
		};
		return waitForValueAsync(channel, value, comparator);
	}
	
	public static <T> CompletableFuture<T> waitForValueAsync(Channel<T> channel, T value, Comparator<T> comparator){
		CompletableFuture<T> future = new CompletableFuture<>();
		
		final Monitor<T> monitor = channel.addValueMonitor( newValue -> {
			if(comparator.compare(newValue, value)==0){
				future.complete(newValue);
			}
		});
		return future.whenComplete((v,exception)->monitor.close());
//		return future;
	}
	
	public static <T> Channel<T> create(Context context, String name, Class<T> type){
		Channel<T> channel = context.createChannel(name, type);
		channel.connect();
		return channel;
	}
	
	public static <T> Channel<T> create(Context context, ChannelDescriptor<T> descriptor){
		Channel<T> channel = context.createChannel(descriptor.getName(), descriptor.getType());
		channel.connect();
		return channel;
	}
	
	public static List<Channel<?>> create(Context context, List<ChannelDescriptor<?>> descriptors){
		List<Channel<?>> channels = new ArrayList<>(descriptors.size());
		List<CompletableFuture<?>> futures = new ArrayList<>(descriptors.size());
		for(ChannelDescriptor<?> descriptor: descriptors){
			Channel<?> channel = context.createChannel(descriptor.getName(), descriptor.getType());
			channels.add(channel);
			futures.add(channel.connectAsync());
		}
		try {
			CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[futures.size()])).get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
		
		return channels;
	}

}