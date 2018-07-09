package org.epics.ca;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.epics.ca.data.Metadata;
import org.epics.ca.util.Holder;

import com.lmax.disruptor.dsl.Disruptor;

public interface Channel<T> extends AutoCloseable
{
   public String getName();

   public ConnectionState getConnectionState();

   public AccessRights getAccessRights();

   public Channel<T> connect();

   public CompletableFuture<Channel<T>> connectAsync();


   //
   // listeners
   //

   public Listener addConnectionListener( BiConsumer<Channel<T>, Boolean> handler );

   public Listener addAccessRightListener( BiConsumer<Channel<T>, AccessRights> handler );


   //
   // sync methods, exception is thrown on failure
   //

   // NOTE: reusable get methods
   //	public T get(T reuse);
   // public CompletableFuture<T> getAsync(T reuse);
   // public <MT extends Metadata<T>> MT get(Class<? extends Metadata> clazz, T reuse);
   // public <MT extends Metadata<T>> CompletableFuture<MT> getAsync(Class<? extends MT> clazz, T reuse);

   public T get();

   public void put( T value );

   public void putNoWait( T value ); // best-effort put

   //
   // async methods, exception is reported via CompletableFuture
   //
   public CompletableFuture<T> getAsync();

   public CompletableFuture<Status> putAsync( T value );

   // NOTE: "public <MT extends Metadata<T>> MT get(Class<MT> clazz)" would
   // be a better definition, however it raises unchecked warnings in the code
   // and requires explicit casts for monitor APIs
   // the drawback of the signature below is that type of "?" can be different than "MT"
   // (it will raise ClassCastException if not properly used)

   @SuppressWarnings( "rawtypes" )
   public <MT extends Metadata<T>> MT get( Class<? extends Metadata> clazz );

   @SuppressWarnings( "rawtypes" )
   public <MT extends Metadata<T>> CompletableFuture<MT> getAsync( Class<? extends Metadata> clazz );

   //
   // monitors
   //

   // Value-only monitor. Default, value-change, notification mask.
   default Monitor<T> addValueMonitor( Consumer<? super T> handler )
   {
      return addValueMonitor (handler, Monitor.VALUE_MASK);
   }

   // Value-only monitor. User-specified notification mask.
   public Monitor<T> addValueMonitor( Consumer<? super T> handler, int mask );

   // Metadata monitor.  Default, value-change, notification mask.
   @SuppressWarnings( "rawtypes" )
   default  <MT extends Metadata<T>> Monitor<MT> addMonitor( Class<? extends Metadata> clazz, Consumer<MT> handler )
   {
      return addMonitor( clazz, handler, Monitor.VALUE_MASK );
   }

   // Metadata monitor.  User-specified notification mask.
   @SuppressWarnings( "rawtypes" )
   public <MT extends Metadata<T>> Monitor<MT> addMonitor( Class<? extends Metadata> clazz, Consumer<MT> handler, int mask );


   //
   // misc
   //

   // get channel properties, e.g. native type, host, etc.
   Map<String, Object> getProperties();

   // suppresses AutoCloseable.close() exception
   @Override
   void close();
}
