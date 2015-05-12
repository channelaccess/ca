package org.epics.ca.impl;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.epics.ca.AccessRights;
import org.epics.ca.Channel;
import org.epics.ca.ConnectionState;
import org.epics.ca.Listener;
import org.epics.ca.Monitor;
import org.epics.ca.Status;
import org.epics.ca.data.Metadata;
import org.epics.ca.impl.requests.MonitorRequest;
import org.epics.ca.impl.requests.ReadNotifyRequest;
import org.epics.ca.impl.requests.WriteNotifyRequest;
import org.epics.ca.impl.types.TypeSupport;
import org.epics.ca.impl.types.TypeSupports;
import org.epics.ca.util.Holder;
import org.epics.ca.util.IntHashMap;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

public class ChannelImpl<T> implements Channel<T>, TransportClient
{
	// Get Logger
	private static final Logger logger = Logger.getLogger(ChannelImpl.class.getName());

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
	
	protected volatile int nativeElementCount = 0;
	
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

	public int getSID() {
		return sid;
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
			throw new RuntimeException("Failed to do get.", th);
		}
	}

	@Override
	public void put(T value) {
		
		TCPTransport t = connectionRequiredCheck();
		
		// check read access
		AccessRights currentRights = getAccessRights();
		if (currentRights != AccessRights.WRITE &&
			currentRights != AccessRights.READ_WRITE)
			throw new IllegalStateException("No write rights.");
		
		int count = typeSupport.getForcedElementCount();
		if (count == 0)
			count = Array.getLength(value);
		
		Messages.writeMessage(t, sid, cid, typeSupport, value, count);
		transport.flush();		// TODO auto-flush
	}

	@Override
	public CompletableFuture<T> getAsync() {
		
		TCPTransport t = connectionRequiredCheck();
		
		// check read access
		AccessRights currentRights = getAccessRights();
		if (currentRights != AccessRights.READ &&
			currentRights != AccessRights.READ_WRITE)
			throw new IllegalStateException("No read rights.");
		
		return new ReadNotifyRequest<T>(this, t, sid, typeSupport);
	}

	@Override
	public CompletableFuture<Status> putAsync(T value) {
		
		TCPTransport t = connectionRequiredCheck();
		
		// check read access
		AccessRights currentRights = getAccessRights();
		if (currentRights != AccessRights.WRITE &&
			currentRights != AccessRights.READ_WRITE)
			throw new IllegalStateException("No write rights.");
		
		int count = typeSupport.getForcedElementCount();
		if (count == 0)
			count = Array.getLength(value);
		
		return new WriteNotifyRequest<T>(this, t, sid, typeSupport, value, count);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <MT extends Metadata<T>> MT get(@SuppressWarnings("rawtypes") Class<? extends Metadata> clazz) {
		try {
			return (MT)getAsync(clazz).get();
		} catch (Throwable th) {
			throw new RuntimeException("Failed to do get.", th);
		}
	}

	@SuppressWarnings("rawtypes")
	@Override
	public <MT extends Metadata<T>> CompletableFuture<MT> getAsync(
			Class<? extends Metadata> clazz) {
		
		TCPTransport t = connectionRequiredCheck();

		TypeSupport metaTypeSupport = TypeSupports.getTypeSupport(clazz, channelType);
		if (metaTypeSupport == null)
			throw new RuntimeException("unsupported channel metadata type " + clazz + "<" + channelType + ">");
		
		// check read access
		AccessRights currentRights = getAccessRights();
		if (currentRights != AccessRights.READ &&
			currentRights != AccessRights.READ_WRITE)
			throw new IllegalStateException("No read rights.");
		
		return new ReadNotifyRequest<MT>(this, t, sid, metaTypeSupport);
	}

	static class HolderEventFactory<TT> implements EventFactory<Holder<TT>> {

		private final TypeSupport typeSupport;
		
		public HolderEventFactory(TypeSupport typeSupport)
		{
			this.typeSupport = typeSupport;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public Holder<TT> newInstance() {
			return new Holder<TT>((TT)typeSupport.newInstance());
		}
		
	};
	
	@SuppressWarnings("unchecked")
	@Override
	public Monitor<T> addValueMonitor(Consumer<? super T> handler, int queueSize, int mask) {

		// Executor that will be used to construct new threads for consumers
        Executor executor = Executors.newCachedThreadPool();

        // NOTE: queueSize specifies the size of the ring buffer, must be power of 2.
		EventFactory<Holder<T>> eventFactory = new HolderEventFactory<T>(typeSupport);
        
        Disruptor<Holder<T>> disruptor = 
         new Disruptor<>(eventFactory, queueSize, executor,
	    		ProducerType.SINGLE, new SleepingWaitStrategy(10));
        disruptor.handleEventsWith((event, sequence, endOfBatch) -> handler.accept(event.value));
        disruptor.start();
        
        return addValueMonitor(disruptor, mask);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public <MT extends Metadata<T>> Monitor<MT> addMonitor(
			Class<? extends Metadata> clazz, Consumer<? super Metadata> handler, int queueSize, int mask) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Monitor<T> addValueMonitor(Disruptor<Holder<T>> disruptor, int mask) {
		TCPTransport t = connectionRequiredCheck();
        return new MonitorRequest<T>(this, t, typeSupport, mask, disruptor);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public <MT extends Metadata<T>> Monitor<MT> addMonitor(
			Class<? extends Metadata> clazz, Disruptor<Holder<? extends Metadata>> disruptor, int mask) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public Map<String, Object> getProperties() {
		// NOTE: could use Collections.unmodifiableMap(m) here, but leave it writable
		// in case some code needs to tag channels
		return properties;
	}
	
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
				this.nativeElementCount = elementCount;
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
	
    protected TCPTransport connectionRequiredCheck()
    {
    	TCPTransport t = getTransport();
        if (connectionState.get() != ConnectionState.CONNECTED || t == null)
            throw new IllegalStateException("Channel not connected.");
        return t;
    }

    public void resubscribeSubscriptions(Transport transport)
	{
		ResponseRequest[] requests;
		synchronized (responseRequests) {
			int count = responseRequests.size();
			if (count == 0)
				return;
			requests = new ResponseRequest[count];
			requests = responseRequests.toArray(requests);
		}

		for (int i = 0; i < requests.length; i++)
		{
			try
			{
				if (requests[i] instanceof MonitorRequest<?>)
					((MonitorRequest<?>)requests[i]).resubscribe(transport);				
			} catch(Throwable th) {
				logger.log(Level.WARNING, "Unexpected exception caught during resubscription notification.", th);
			}
        }
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
		{
			this.sid = sid;
			this.nativeElementCount = elementCount;
			properties.put("nativeType", typeCode);
			properties.put("nativeElementCount", elementCount);
		}

		// user might create monitors in listeners, so this has to be done before this can happen
		// however, it would not be nice if events would come before connection event is fired
		// but this cannot happen since transport (TCP) is serving in this thread 
		resubscribeSubscriptions(transport);
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
	
	public int getNativeElementCount() {
		return nativeElementCount;
	}

	@Override
	public void transportClosed() {
		disconnect(true);
	}

	public synchronized void disconnect(boolean reconnect)
	{
		if (connectionState.get() != ConnectionState.CONNECTED && transport == null)
			return;

		setConnectionState(ConnectionState.DISCONNECTED);
		
        disconnectPendingIO(false);

        // release transport
        if (transport != null)
        {
            transport.release(this);
            transport = null;
        }

        if (reconnect)
            initiateSearch();
		
	}
	
	private void disconnectPendingIO(boolean destroy)
	{
		Status status = destroy ? Status.CHANDESTROY : Status.DISCONN;

		ResponseRequest[] requests;
		synchronized (responseRequests) {
			requests = new ResponseRequest[responseRequests.size()];
			requests = responseRequests.toArray(requests);
		}

		for (int i = 0; i < requests.length; i++)
		{
			try
			{
				requests[i].exception(status.getStatusCode(), null);
			} catch(Throwable th) {
				logger.log(Level.WARNING, "Unexpected exception caught during disconnect/destroy notification.", th);
			}
        }
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
