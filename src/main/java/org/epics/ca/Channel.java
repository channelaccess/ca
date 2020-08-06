package org.epics.ca;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.epics.ca.data.Metadata;


public interface Channel<T> extends AutoCloseable
{
   String getName();

   ConnectionState getConnectionState();

   AccessRights getAccessRights();

   Channel<T> connect();

   CompletableFuture<Channel<T>> connectAsync();


   //
   // listeners
   //

   Listener addConnectionListener( BiConsumer<Channel<T>, Boolean> handler );

   Listener addAccessRightListener( BiConsumer<Channel<T>, AccessRights> handler );


   //
   // sync methods, exception is thrown on failure
   //

   // NOTE: reusable get methods
   //	T get(T reuse);
   // CompletableFuture<T> getAsync(T reuse);
   // <MT extends Metadata<T>> MT get(Class<? extends Metadata> clazz, T reuse);
   // <MT extends Metadata<T>> CompletableFuture<MT> getAsync(Class<? extends MT> clazz, T reuse);

   T get();

   void put( T value );

   void putNoWait( T value ); // best-effort put

   //
   // async methods, exception is reported via CompletableFuture
   //
   CompletableFuture<T> getAsync();

   CompletableFuture<Status> putAsync( T value );

   // NOTE: "public <MT extends Metadata<T>> MT get(Class<MT> clazz)" would
   // be a better definition, however it raises unchecked warnings in the code
   // and requires explicit casts for monitor APIs
   // the drawback of the signature below is that type of "?" can be different than "MT"
   // (it will raise ClassCastException if not properly used)

   @SuppressWarnings( "rawtypes" )
   <MT extends Metadata<T>> MT get( Class<? extends Metadata> clazz );

   @SuppressWarnings( "rawtypes" )
   <MT extends Metadata<T>> CompletableFuture<MT> getAsync( Class<? extends Metadata> clazz );

   //
   // monitors
   //

   // Value-only monitor. Default, value-change, notification mask.
   default Monitor<T> addValueMonitor( Consumer<? super T> handler )
   {
      return addValueMonitor (handler, Monitor.VALUE_MASK);
   }

   // Value-only monitor. User-specified notification mask.
   Monitor<T> addValueMonitor( Consumer<? super T> handler, int mask );

   // Metadata monitor.  Default, value-change, notification mask.
   @SuppressWarnings( "rawtypes" )
   default  <MT extends Metadata<T>> Monitor<MT> addMonitor( Class<? extends Metadata> clazz, Consumer<MT> handler )
   {
      return addMonitor( clazz, handler, Monitor.VALUE_MASK );
   }

   // Metadata monitor.  User-specified notification mask.
   @SuppressWarnings( "rawtypes" )
   <MT extends Metadata<T>> Monitor<MT> addMonitor( Class<? extends Metadata> clazz, Consumer<MT> handler, int mask );

   //
   // misc
   //
   // get channel properties, e.g. native type, host, etc.
   Map<String, Object> getProperties();

   //Number of elements to read.
   //Default: 0 (defined by type or protocol)
   //If negative then requests the native element count.
   void setElementsToRead( int elementsToRead );

   int getElementsToRead();

   int getNativeElementCount();

   // suppresses AutoCloseable.close() exception
   @Override
   void close();
}
