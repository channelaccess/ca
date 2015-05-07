package org.epics.ca.impl;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.epics.ca.AccessRights;
import org.epics.ca.Channel;
import org.epics.ca.ConnectionState;
import org.epics.ca.Listener;
import org.epics.ca.Monitor;
import org.epics.ca.Status;
import org.epics.ca.data.Metadata;
import org.epics.ca.impl.TypeSupports.TypeSupport;
import org.epics.ca.impl.requests.ReadNotifyRequest;
import org.epics.ca.util.IntHashMap;

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

	protected final AtomicReference<ConnectionState> connectionState =
			new AtomicReference<ConnectionState>(ConnectionState.NEVER_CONNECTED);
	
	protected final AtomicReference<AccessRights> accessRights =
			new AtomicReference<AccessRights>(AccessRights.NO_RIGHTS);

	protected final IntHashMap<ResponseRequest> responseRequests = new IntHashMap<ResponseRequest>();

	protected final TypeSupport typeSupport;
	
	protected final AtomicBoolean connectIssueed = new AtomicBoolean(false);
	protected final AtomicReference<CompletableFuture<Channel<T>>> connectFuture = new AtomicReference<>();
	
	public ChannelImpl(ContextImpl context, String name, Class<T> channelType, int priority)
	{
		this.context = context;
		this.name = name;
		this.channelType = channelType;
		this.priority = priority;
		
		this.cid = context.generateCID();

		typeSupport = TypeSupports.getTypeSupport(channelType);
		if (typeSupport == null)
			throw new RuntimeException("unsupported channel data type " + channelType);
		
		// register before issuing search request
		context.registerChannel(this);
	}
	
	@Override
	public void close() {
		// TODO Auto-generated method stub
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public ConnectionState getConnectionState() {
		return connectionState.get();
	}

	@Override
	public AccessRights getAccessRights() {
		return accessRights.get();
	}

	public int getCID() {
		return cid;
	}

	@Override
	public CompletableFuture<Channel<T>> connect() {
		if (!connectIssueed.getAndSet(true))
		{
			// this has to be submitted immediately
			initiateSearch();
			
			CompletableFuture<Channel<T>> future = new CompletableFuture<>();
			connectFuture.set(future);
			return future;
		}
		else
			throw new IllegalStateException("Connect already issued on this channel instance.");
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
		try {
			return getAsync().get();
		} catch (Throwable th) {
			// TODO is this OK, or should we rather throws exceptions
			throw new RuntimeException("Failed to do get.", th);
		}
	}

	@Override
	public void put(T value) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public CompletableFuture<T> getAsync() {
		
		connectionRequiredCheck();
		
		// check read access
		AccessRights currentRights = getAccessRights();
		if (currentRights != AccessRights.READ &&
			currentRights != AccessRights.READ_WRITE)
			throw new IllegalStateException("No read rights.");
		
		TCPTransport t = getTransport();
		if (t != null)
		{
			return new ReadNotifyRequest<T>(this, t, sid, typeSupport);
		}
		else
			throw new IllegalStateException("Channel not connected.");
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
		
		connectionRequiredCheck();

		TypeSupport metaTypeSupport = TypeSupports.getTypeSupport(clazz, channelType);
		if (metaTypeSupport == null)
			throw new RuntimeException("unsupported channel metadata type " + clazz + "<" + channelType + ">");
		
		// check read access
		AccessRights currentRights = getAccessRights();
		if (currentRights != AccessRights.READ &&
			currentRights != AccessRights.READ_WRITE)
			throw new IllegalStateException("No read rights.");
		
		TCPTransport t = getTransport();
		if (t != null)
		{
			return new ReadNotifyRequest<MT>(this, t, sid, metaTypeSupport);
		}
		else
			throw new IllegalStateException("Channel not connected.");
		
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
	public void createChannel(TCPTransport transport, int sid, short typeCode, int elementCount) 
	{

		synchronized (this)
		{
			// do not allow duplicate creation to the same transport
			if (!allowCreation)
				return;
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
				return;
			}
			
			this.transport = transport;
			
			// revision < v4.4 supply this info already now
			if (transport.getMinorRevision() < 4)
			{
				this.sid = sid;
				properties.put("nativeType", typeCode);
				properties.put("nativeElementCount", elementCount);
			}

			properties.put("remoteAddress", transport.getRemoteAddress());
			
			// do not submit CreateChannelRequest here, connection loss while submitting and lock
			// on this channel instance may cause deadlock
		}

		try {
			Messages.createChannelMessage(transport, name, cid);
			// flush immediately
			transport.flush();
		}
		catch (Throwable th) {
			createChannelFailed();
		}
	}
	
	public void setAccessRights(int rightsCode)
	{
		// code matches enum ordinal
		setAccessRights(AccessRights.values()[rightsCode]);
	}

	public void setAccessRights(AccessRights rights)
	{
		AccessRights previousRights = accessRights.getAndSet(rights);
		if (previousRights != rights)
		{
			// TODO notify change
		}
	}

	public void setConnectionState(ConnectionState state)
	{
		ConnectionState previousCS = connectionState.getAndSet(state);
		if (previousCS != state)
		{
			CompletableFuture<Channel<T>> cf = connectFuture.getAndSet(null);
			if (cf != null)
				cf.complete(this);
			
			
			// TODO notify change
		}
	}
	
    protected void connectionRequiredCheck()
    {
        if (connectionState.get() != ConnectionState.CONNECTED)
            throw new IllegalStateException("Channel not connected.");
    }

    public void resubscribeSubscriptions()
	{
		// TODO
	}

	/**
	 * Called when channel created succeeded on the server.
	 * <code>sid</code> might not be valid, this depends on protocol revision.
	 * @param sid
	 * @param typeCode
	 * @param elementCount
	 * @throws IllegalStateException
	 */
	public synchronized void connectionCompleted(int sid, short typeCode, int elementCount) 
		throws IllegalStateException
	{
		// do this silently
		if (connectionState.get() == ConnectionState.CLOSED)
			return;
		
		// revision < v4.1 do not have access rights, grant all
		if (transport.getMinorRevision() < 1)
			setAccessRights(AccessRights.READ_WRITE);

		// revision < v4.4 supply this info already now
		if (transport.getMinorRevision() >= 4)
			this.sid = sid;

		// set properties
		properties.put("nativeType", typeCode);
		properties.put("nativeElementCount", elementCount);

		// user might create monitors in listeners, so this has to be done before this can happen
		// however, it would not be nice if events would come before connection event is fired
		// but this cannot happen since transport (TCP) is serving in this thread 
		resubscribeSubscriptions();
		setConnectionState(ConnectionState.CONNECTED);

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

	// TODO consider different sync maybe
	public synchronized TCPTransport getTransport() {
		return transport;
	}

	public int getPriority() {
		return priority;
	}

	@Override
	public void transportClosed() {
		// TODO Auto-generated method stub
		
	}

	/** 
	 * Register a response request.
	 * @param responseRequest response request to register.
	 */
	public void registerResponseRequest(ResponseRequest responseRequest)
	{
		synchronized (responseRequests)
		{
			responseRequests.put(responseRequest.getIOID(), responseRequest);
		}
	}

	/**
	 * Unregister a response request.
	 * @param responseRequest response request to unregister.
	 */
	public void unregisterResponseRequest(ResponseRequest responseRequest)
	{
		synchronized (responseRequests)
		{
			responseRequests.remove(responseRequest.getIOID());
		}
	}

	public TypeSupport getTypeSupport() {
		return typeSupport;
	}
	
	
}
