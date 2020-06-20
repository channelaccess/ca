/*- Package Declaration ------------------------------------------------------*/

package org.epics.ca.impl.search;

/*- Imported packages --------------------------------------------------------*/

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.epics.ca.Constants;
import org.epics.ca.impl.BroadcastTransport;
import org.epics.ca.impl.ChannelImpl;
import org.epics.ca.impl.Messages;
import org.epics.ca.util.logging.LibraryLogManager;

/*- Interface Declaration ----------------------------------------------------*/
/*- Class Declaration --------------------------------------------------------*/

/**
 * CA channel search manager.
 *
 * @author msekoranja
 */
public class ChannelSearchManager
{

/*- Public attributes --------------------------------------------------------*/
/*- Private attributes -------------------------------------------------------*/

   private static final int MIN_SEND_INTERVAL_MS_DEFAULT = 100;
   private static final int MAX_SEND_INTERVAL_MS_DEFAULT = 30_000;
   private static final int INTERVAL_MULTIPLIER_DEFAULT = 2;

   private static final int MESSAGE_COALESCENCE_TIME_MS = 3;

   private static final int MAX_NUMBER_IMMEDIATE_PACKETS = 5;
   private static final int IMMEDIATE_PACKETS_DELAY_MS = 10;

   private static final Logger logger = LibraryLogManager.getLogger( ChannelSearchManager.class );

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

   private final SearchTimer timer = new SearchTimer();
   private final AtomicBoolean canceled = new AtomicBoolean();

   private final AtomicInteger immediatePacketCount = new AtomicInteger();
   private final AtomicInteger channelCount = new AtomicInteger();

   /**
    * Broadcast transport.
    */
   private final BroadcastTransport broadcastTransport;

   /**
    * Search (datagram) sequence number.
    */
   private final AtomicInteger sequenceNumber = new AtomicInteger( 0 );

   /**
    * Send byte buffer (frame)
    */
   private final ByteBuffer sendBuffer;


/*- Main ---------------------------------------------------------------------*/
/*- Constructor --------------------------------------------------------------*/

   /**
    * Constructor.
    *
    * @param broadcastTransport transport
    */
   public ChannelSearchManager( BroadcastTransport broadcastTransport )
   {
      this.broadcastTransport = broadcastTransport;

      minSendInterval = MIN_SEND_INTERVAL_MS_DEFAULT;
      maxSendInterval = MAX_SEND_INTERVAL_MS_DEFAULT;
      intervalMultiplier = INTERVAL_MULTIPLIER_DEFAULT;

      // create and initialize send buffer
      sendBuffer = ByteBuffer.allocateDirect( Constants.MAX_UDP_SEND );
      initializeSendBuffer ();
   }


/*- Public methods -----------------------------------------------------------*/

   /**
    * Register channel.
    *
    * @param channel the channel to register.
    * @return true if the channel was successfully registered.
    */
   public boolean registerChannel( ChannelImpl<?> channel )
   {
      if ( canceled.get() )
      {
         return false;
      }

      final ChannelSearchTimerTask timerTask = new ChannelSearchTimerTask( channel );
      channel.setTimerId( timerTask );

      timer.executeAfterDelay( MESSAGE_COALESCENCE_TIME_MS, timerTask );
      channelCount.incrementAndGet();
      return true;
   }

   /**
    * Unregister channel.
    *
    * @param channel channel to unregister
    */
   public void unregisterChannel( ChannelImpl<?> channel )
   {
      if ( canceled.get() )
      {
         return;
      }

      final Object timerTask = channel.getTimerId();
      if ( timerTask != null )
      {
         SearchTimer.cancel( timerTask );
         channel.setTimerId( null );
      }
      channelCount.decrementAndGet();
   }

   /**
    * Get number of registered channels.
    *
    * @return number of registered channels.
    */
   public int registeredChannelCount()
   {
      return channelCount.get();
   }

   /**
    * Beacon anomaly detected.
    * Boost searching of all channels.
    */
   public void beaconAnomalyNotify()
   {
      logger.fine( "A beacon anomaly has been detected." );
      if ( canceled.get() )
      {
         return;
      }
      logger.fine( "Reinstigating channel search." );
      timer.rescheduleAllAfterDelay(0 );
   }

   /**
    * Cancel.
    */
   public void cancel()
   {
      if ( canceled.getAndSet(true) )
      {
         return;
      }
      timer.shutDown();
   }


/*- Private methods ----------------------------------------------------------*/

   /**
    * Initialize send buffer.
    */
   private void initializeSendBuffer()
   {
      sendBuffer.clear ();

      // put version message
      sequenceNumber.incrementAndGet();
      Messages.generateVersionRequestMessage( broadcastTransport, sendBuffer, (short) 0, sequenceNumber.get(), true );
   }

   /**
    * Flush send buffer.
    */
   private synchronized void flushSendBuffer()
   {
      if ( immediatePacketCount.incrementAndGet() >= MAX_NUMBER_IMMEDIATE_PACKETS )
      {
         try
         {
            Thread.sleep( IMMEDIATE_PACKETS_DELAY_MS );
         }
         catch ( InterruptedException ex )
         {
            // noop
         }
         immediatePacketCount.set( 0 );
      }

      broadcastTransport.send( sendBuffer );
      initializeSendBuffer();
   }

   /**
    * Generate (put on send buffer) search request
    *
    * @param channel       channel
    * @param allowNewFrame flag indicating if new search request message is allowed to be put in new frame.
    * @return <code>true</code> if new frame was sent.
    */
   private synchronized boolean generateSearchRequestMessage( ChannelImpl<?> channel, @SuppressWarnings( "SameParameterValue" ) boolean allowNewFrame )
   {
      boolean success = channel.generateSearchRequestMessage (broadcastTransport, sendBuffer);
      // buffer full, flush
      if ( !success )
      {
         flushSendBuffer();
         if ( allowNewFrame )
         {
            channel.generateSearchRequestMessage( broadcastTransport, sendBuffer );
         }
         return true;
      }

      return false;
   }

   /**
    * Search response received notification.
    *
    * @param channel found channel.
    */
   public void searchResponse( ChannelImpl<?> channel )
   {
      unregisterChannel (channel);
   }


/*- Nested Classes -----------------------------------------------------------*/

   private class ChannelSearchTimerTask extends SearchTimer.TimerTask
   {
      private final ChannelImpl<?> channel;

      ChannelSearchTimerTask( ChannelImpl<?> channel )
      {
         this.channel = channel;
      }

      public long timeout()
      {
         // send search message
         generateSearchRequestMessage( channel, true );

         if ( !timer.hasNext( MESSAGE_COALESCENCE_TIME_MS ) )
         {
            flushSendBuffer();
            immediatePacketCount.set( 0 );
         }

         // reschedule
         long dT = getDelay();

         dT *= intervalMultiplier;
         if ( dT > maxSendInterval )
         {
            dT = maxSendInterval;
         }
         if ( dT < minSendInterval )
         {
            dT = minSendInterval;
         }

         return dT;
      }
   }

}
