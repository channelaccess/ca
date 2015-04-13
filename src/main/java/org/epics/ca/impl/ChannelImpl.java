package org.epics.ca.impl;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.epics.ca.AccessRights;
import org.epics.ca.Channel;
import org.epics.ca.Listener;
import org.epics.ca.Monitor;
import org.epics.ca.Status;
import org.epics.ca.data.Metadata;

import com.lmax.disruptor.dsl.Disruptor;

public class ChannelImpl<T> implements Channel<T>, TransportClient
{
	protected final ContextImpl context;
	protected final String name;
	protected final Class<T> channelType;
	protected final int priority;
	
	protected final int cid;
	
	protected final int INVALID_SID = 0xFFFFFFFF;
	protected int sid = INVALID_SID;
	
	protected TCPTransport transport;
	
	protected final Map<String, Object> properties = new HashMap<String, Object>();

	protected T value;
	
	public ChannelImpl(ContextImpl context, String name, Class<T> channelType, int priority)
	{
		this.context = context;
		this.name = name;
		this.channelType = channelType;
		this.priority = priority;
		
		this.cid = context.generateCID();

		/*
		// TODO
		// map channelType to base DBR type
		// meta DBR type = meta base + channel base
		TypeSupport ts = Util.getTypeSupport(channelType);
		System.out.println(ts.getClass().getName() + " code " + ts.getCode());
		*/
		
		// register before issuing search request
		context.registerChannel(this);

		// this has to be submitted immediately
		initiateSearch();
	}
	
	@Override
	public void close() {
		// TODO Auto-generated method stub
	}

	@Override
	public String getName() {
		return name;
	}

	public int getCID() {
		return cid;
	}

	@Override
	public CompletableFuture<Channel<T>> connect() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Listener addConnectionListener(
			BiConsumer<Channel<T>, Boolean> handler) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Listener addAccessRightListener(
			BiConsumer<Channel<T>, AccessRights> handler) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public T get() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void put(T value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public CompletableFuture<T> getAsync() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompletableFuture<Status> putAsync(T value) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <MT extends Metadata<T>> MT get(@SuppressWarnings("rawtypes") Class<? extends Metadata> clazz) {
		// TODO Auto-generated method stub
		return null;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public <MT extends Metadata<T>> CompletableFuture<MT> getAsync(
			Class<? extends Metadata> clazz) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Monitor<T> addValueMonitor(Consumer<? extends T> handler, int queueSize) {
		// TODO Auto-generated method stub
		return null;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public <MT extends Metadata<T>> Monitor<MT> addMonitor(
			Class<? extends Metadata> clazz, Consumer<? extends Metadata> handler, int queueSize) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Monitor<T> addValueMonitor(Disruptor<T> disruptor) {
		// TODO Auto-generated method stub
		return null;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public <MT extends Metadata<T>> Monitor<MT> addMonitor(
			Class<? extends Metadata> clazz, Disruptor<? extends Metadata> disruptor) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public Map<String, Object> getProperties() {
		// NOTE: could use Collections.unmodifiableMap(m) here, but leave it writable
		// in case some code needs to tag channels
		return properties;
		
	}

	/*
	public <VT extends Metadata<T>> Monitor<VT> createMonitor(@SuppressWarnings("rawtypes") Class<? extends Metadata> clazz)
	{
        // Executor that will be used to construct new threads for consumers
        Executor executor = Executors.newCachedThreadPool();

        // Specify the size of the ring buffer, must be power of 2.
        int bufferSize = 4;
        
        // Event factory.... get out of TypeSupport
        @SuppressWarnings("unchecked")
		EventFactory<VT> eventFactory = (EventFactory<VT>)Util.getEventFactory(clazz);
        
        //Disruptor<VT> disruptor = 
         new Disruptor<>(eventFactory, bufferSize, executor,
	    		ProducerType.SINGLE, new SleepingWaitStrategy(10));
        
        return null;
	}

	*/
	
	protected final AtomicReference<Object> timerIdRef = new AtomicReference<Object>();
	
	public void setTimerId(Object timerId)
	{
		timerIdRef.set(timerId);
	}
	
	public Object getTimerId()
	{
		return timerIdRef.get();
	}
	
	// TODO
	boolean allowCreation = false;
	
	/**
	 * Initiate search (connect) procedure.
	 */
	public synchronized void initiateSearch()
	{
		// TODO synced?!!
		allowCreation = true;
		context.getChannelSearchManager().registerChannel(this);
	}

	/**
	 * Create a channel, i.e. submit create channel request to the server.
	 * This method is called after seatch is complete.
	 * <code>sid</code>, <code>typeCode</code>, <code>elementCount</code> might not be
	 * valid, this depends on protocol revision.
	 * @param transport
	 * @param sid
	 * @param typeCode
	 * @param elementCount
	 */
	public synchronized boolean createChannel(TCPTransport transport, int sid, short typeCode, int elementCount) 
	{

		// do not allow duplicate creation to the same transport
		if (!allowCreation)
			return false;
		allowCreation = false;
		
		// check existing transport
		if (this.transport != null && this.transport != transport)
		{
			// TODO disconnectPendingIO(false);
			this.transport.release(this);
		}
		else if (this.transport == transport)
		{
			// request to sent create request to same transport, ignore
			// this happens when server is slower (processing search requests) than client generating it
			return false;
		}
		
		this.transport = transport;
		
		// revision < v4.4 supply this info already now
		if (transport.getMinorRevision() < 4)
		{
			this.sid = sid;
			properties.put("nativeType", typeCode);
			properties.put("nativeElementCount", elementCount);
		}

		// TODO !!!
		// do not submit CreateChannelRequest here, connection loss while submitting and lock
		// on this channel instance may cause deadlock
		return true;
	}

	public void createChannelFailed()
	{
		// ... and search again
		initiateSearch();
	}

	/**
	 * Send search message.
	 * @return success status.  
	 */
	public boolean generateSearchRequestMessage(Transport transport, ByteBuffer buffer)
	{
		return Messages.generateSearchRequestMessage(transport, buffer, name, cid);
	}

	public TCPTransport getTransport() {
		return transport;
	}

	public int getPriority() {
		return priority;
	}

	@Override
	public void transportClosed() {
		// TODO Auto-generated method stub
		
	}
	
	
}
