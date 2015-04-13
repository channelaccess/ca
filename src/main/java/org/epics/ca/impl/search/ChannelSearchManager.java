package org.epics.ca.impl.search;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.epics.ca.Constants;
import org.epics.ca.impl.BroadcastTransport;
import org.epics.ca.impl.ChannelImpl;
import org.epics.ca.impl.Messages;

/**
 * CA channel search manager.
 * @author msekoranja
 */
public class ChannelSearchManager {

	/**
	 * Minimum time between sending packets (ms).
	 */
	private final long minSendInterval;
	
	/**
	 * Maximum time between sending packets (ms).
	 */
	private final long maxSendInterval;

	/**
	 * Search interval multiplier, i.e. multiplier for exponential back-off. 
	 */
	private final int intervalMultiplier;

	private static final int MIN_SEND_INTERVAL_MS_DEFAULT = 100;
	private static final int MAX_SEND_INTERVAL_MS_DEFAULT = 30000;
	private static final int INTERVAL_MULTIPLIER_DEFAULT = 2;
	
	private static final int MESSAGE_COALESCENCE_TIME_MS = 3;
	
	private static final int MAX_NUMBER_IMMEDIATE_PACKETS = 5;
	private static final int IMMEDIATE_PACKETS_DELAY_MS = 10;
	
	private final SearchTimer timer = new SearchTimer();
	private final AtomicBoolean canceled = new AtomicBoolean();
	
	private final AtomicInteger immediatePacketCount = new AtomicInteger();
	
	private class ChannelSearchTimerTask extends SearchTimer.TimerTask
	{
		private final ChannelImpl<?> channel;
		
		ChannelSearchTimerTask(ChannelImpl<?> channel)
		{
			this.channel = channel;
		}
		
		public long timeout() {

			// send search message
			generateSearchRequestMessage(channel, true);
			
			if (!timer.hasNext(MESSAGE_COALESCENCE_TIME_MS))
			{
				flushSendBuffer();
				immediatePacketCount.set(0);
			}
			
			// reschedule
			long dT = getDelay();
			dT *= intervalMultiplier;
			if (dT > maxSendInterval)
				dT = maxSendInterval;
			if (dT < minSendInterval)
				dT = minSendInterval;
			
			return dT;
		}
		
	}
	
	/**
	 * Register channel.
	 * @param channel the channel to register.
	 * @return true if the channel was successfully registered.
 	 */
	public boolean registerChannel(ChannelImpl<?> channel)
	{
		if (canceled.get())
			return false;

		ChannelSearchTimerTask timerTask = new ChannelSearchTimerTask(channel);
		channel.setTimerId(timerTask);
		
		timer.executeAfterDelay(MESSAGE_COALESCENCE_TIME_MS, timerTask);
		
		channelCount.incrementAndGet();
		
		return true;
	}
	
	/**
	 * Unregister channel.
	 * @param channel
	 */
	public void unregisterChannel(ChannelImpl<?> channel)
	{
		if (canceled.get())
			return;

		Object timerTask = channel.getTimerId();
		if (timerTask != null)
		{
			SearchTimer.cancel(timerTask);
			channel.setTimerId(null);
		}
		
		channelCount.decrementAndGet();
	}

	private final AtomicInteger channelCount = new AtomicInteger();
	
	/**
	 * Get number of registered channels.
	 * @return number of registered channels.
	 */
	public int registeredChannelCount() {
		return channelCount.get();
	}
	
	/**
	 * Beacon anomaly detected.
	 * Boost searching of all channels.
	 */
	public void beaconAnomalyNotify()
	{
		if (canceled.get())
			return;
		
		timer.rescheduleAllAfterDelay(0);
	}
	
	/**
	 * Cancel.
	 */
	public void cancel()
	{
		if (canceled.getAndSet(true))
			return;
		
		timer.shutDown();
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	

	/**
	 * Broadcast transport.
	 */
	private BroadcastTransport broadcastTransport;

	/**
	 * Search (datagram) sequence number.
	 */
	private volatile int sequenceNumber = 0;

	/**
	 * Send byte buffer (frame)
	 */
	private ByteBuffer sendBuffer;
	
    /**
	 * Constructor.
	 * @param context
	 */
	public ChannelSearchManager(BroadcastTransport broadcastTransport)
	{
		this.broadcastTransport = broadcastTransport;

		minSendInterval = MIN_SEND_INTERVAL_MS_DEFAULT;
		maxSendInterval = MAX_SEND_INTERVAL_MS_DEFAULT;
		intervalMultiplier = INTERVAL_MULTIPLIER_DEFAULT;

		// create and initialize send buffer
		sendBuffer = ByteBuffer.allocateDirect(Constants.MAX_UDP_SEND);
		initializeSendBuffer();
	}	
	
	/**
	 * Initialize send buffer.
	 */
	private void initializeSendBuffer()
	{
		sendBuffer.clear();
		
		// put version message
		sequenceNumber++;
		Messages.generateVersionRequestMessage(broadcastTransport, sendBuffer, (short)0, sequenceNumber, true);
	}

	/**
	 * Flush send buffer.
	 */
	private synchronized void flushSendBuffer()
	{
		if (immediatePacketCount.incrementAndGet() >= MAX_NUMBER_IMMEDIATE_PACKETS)
		{
			try {
				Thread.sleep(IMMEDIATE_PACKETS_DELAY_MS);
			} catch (InterruptedException e) {
				// noop
			}
			immediatePacketCount.set(0);
		}
		
		broadcastTransport.send(sendBuffer);
		initializeSendBuffer();
	}

	/**
	 * Generate (put on send buffer) search request 
	 * @param channel 
	 * @param allowNewFrame flag indicating if new search request message is allowed to be put in new frame.
	 * @return <code>true</code> if new frame was sent.
	 */
	private synchronized boolean generateSearchRequestMessage(ChannelImpl<?> channel, boolean allowNewFrame)
	{
		boolean success = channel.generateSearchRequestMessage(broadcastTransport, sendBuffer);
		// buffer full, flush
		if (!success)
		{
			flushSendBuffer();
			if (allowNewFrame)
				channel.generateSearchRequestMessage(broadcastTransport, sendBuffer);
			return true;
		}
		
		return false;
	}
	
	/**
	 * Search response received notification.
	 * @param channel found channel.
	 */
	public void searchResponse(ChannelImpl<?> channel)
	{
		unregisterChannel(channel);
		
		// TODO we could destroy timer thread when there is no channel to search
	}

}
