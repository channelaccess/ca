package org.epics.ca.impl.reactor;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of reactor pattern using <code>java.nio.channels.Selector</code>.
 */
public class Reactor
{

   // Get Logger
   private static final Logger logger = Logger.getLogger (Reactor.class.getName ());

   /**
    * Simple internal request interface.
    */
   private interface InternalRequest
   {
      /**
       * Process the request.
       */
      public void process();
   }

   /**
    * Registration request to selector.
    */
   private class RegistrationRequest implements InternalRequest
   {
      private SelectableChannel selectableChannel;
      private int interestOps;
      private ReactorHandler handler;

      private SelectionKey key = null;
      private ClosedChannelException exception = null;
      private boolean done = false;

      /**
       * Constructor.
       *
       * @param selectableChannel
       * @param interestOps
       * @param handler
       */
      public RegistrationRequest( SelectableChannel selectableChannel, int interestOps, ReactorHandler handler )
      {
         this.selectableChannel = selectableChannel;
         this.interestOps = interestOps;
         this.handler = handler;
      }

      /**
       * Process the registration resulting set <code>key</code> or <code>exception</code> fields.
       */
      public synchronized void process()
      {
         try
         {
            // do the registration...
            key = selectableChannel.register (selector, interestOps, handler);
         }
         catch ( ClosedChannelException cce )
         {
            exception = cce;
         }
         finally
         {
            // notify about completed registration process
            done = true;
            notifyAll ();
         }
      }

      /**
       * Cancel process of registration.
       */
      public synchronized void cancelRegistration()
      {
         // notify about canceled registration process
         done = true;
         notifyAll ();
      }

      /**
       * Exception thrown during registration, <code>null</code> if none.
       *
       * @return exception thrown during registration.
       */
      public ClosedChannelException getException()
      {
         return exception;
      }

      /**
       * Obtained key, <code>null</code> on failure.
       *
       * @return obtained key.
       */
      public SelectionKey getKey()
      {
         return key;
      }

      /**
       * Checks if registration is done (or canceled).
       * Note: note synced to this.
       *
       * @return
       */
      public boolean isDone()
      {
         return done;
      }

   }

   /**
    * Registration request to selector.
    */
   private class DeregistrationRequest implements InternalRequest
   {
      private SelectionKey key = null;

      /**
       * Constructor.
       *
       * @param key
       */
      public DeregistrationRequest( SelectionKey key )
      {
         this.key = key;
      }

      /**
       * Unregisters and closes.
       */
      public void process()
      {
         key.cancel ();
         try
         {
            key.channel ().close ();
         }
         catch ( IOException e )
         { /* noop */ }
      }
   }

   /**
    * Changing operation of interests request.
    */
   private class InterestOpsChangeRequest implements InternalRequest
   {
      private SelectionKey selectionKey;
      private int interestOps;

      /**
       * @param selectionKey
       * @param interestOps
       */
      public InterestOpsChangeRequest( SelectionKey selectionKey, int interestOps )
      {
         this.selectionKey = selectionKey;
         this.interestOps = interestOps;
      }

      /**
       * Process change of interests ops.
       */
      public void process()
      {
         selectionKey.interestOps (interestOps);
      }
   }

   /**
    * IO selector (e.g. UNIX select() implementation).
    */
   private Selector selector;

   /**
    * Estimated (max) number of requests (aka connections).
    */
   private final int DEFAULT_ESTIMATED_MAX_REQUESTS = 1024;

   /**
    * List of pending registration request(s).
    */
   private Deque<RegistrationRequest> registrationRequests = new ArrayDeque<RegistrationRequest> (DEFAULT_ESTIMATED_MAX_REQUESTS);

   /**
    * List of pending registration request(s).
    */
   private Deque<DeregistrationRequest> unregistrationRequests = new ArrayDeque<DeregistrationRequest> (DEFAULT_ESTIMATED_MAX_REQUESTS);

   /**
    * List of pending registration request(s).
    */
   private Deque<InterestOpsChangeRequest> interestOpsChangeRequests = new ArrayDeque<InterestOpsChangeRequest> (DEFAULT_ESTIMATED_MAX_REQUESTS);

   /**
    * Map of disabled keys, pairs (SelectionKey key, Integer interestOps).
    */
   private Map<SelectionKey, Integer> disabledKeys = new HashMap<SelectionKey, Integer> (DEFAULT_ESTIMATED_MAX_REQUESTS);

   /**
    * Selector status.
    */
   private AtomicInteger selectorPending = new AtomicInteger (0);

   /**
    * Shutdown status.
    */
   private volatile boolean shutdown = false;

   /**
    * Shutdown monitor (condition variable).
    */
   private volatile Object shutdownMonitor = new Object ();

   /**
    * Creates a new instance of reactor.
    *
    * @throws IOException IO Exception
    */
   public Reactor() throws IOException
   {
      initialize ();
   }

   /**
    * Initialize reactor.
    *
    * @throws IOException IO Exception
    */
   private void initialize() throws IOException
   {
      // create an instance of selector
      selector = Selector.open ();
   }

   /**
    * Processes internal request list.
    * Also takes care of sync.
    *
    * @param list list of internal requests to be processed.
    */
   private static void processInternalRequest( Deque<? extends InternalRequest> list )
   {
      if ( !list.isEmpty () )
      {
         synchronized ( list )
         {
            while ( !list.isEmpty () )
            {
               try
               {
                  list.removeFirst ().process ();
               }
               catch ( Throwable th )
               {
                  // noop (just not to lose control)
               }
            }
         }
      }
   }

   /**
    * Process requests.
    * NOTE: this method has to be called until <code>false</code> is returned.
    *
    * @return <code>true</code> if selector is still active, <code>false</code> when shutdown.
    */
   public boolean process()
   {
      // do while reactor is open
      if ( selector.isOpen () && !shutdown )
      {
         processInternal ();
      }

      // if closed, do the cleanup
      if ( !selector.isOpen () || shutdown )
      {
         // cancel pending registration requests
         if ( !registrationRequests.isEmpty () )
         {
            synchronized ( registrationRequests )
            {
               while ( !registrationRequests.isEmpty () )
                  ((RegistrationRequest) registrationRequests.removeFirst ()).cancelRegistration ();
            }
         }

         // check pending unregistration requests (not to forget to close channels)
         processInternalRequest (unregistrationRequests);

         synchronized ( shutdownMonitor )
         {
            shutdownMonitor.notifyAll ();
         }

         //System.out.println();
         //System.out.println("[Selector closed.]");

         return false;
      }
      else
         return true;
   }

   /**
    * Process requests (internal method).
    * NOTE: Selector objects are thread-safe, but the key sets they contain are not. The key sets
    * returned by the keys( ) and selectedKeys( ) methods are direct references to private
    * Set objects inside the Selector object. These sets can change at any time.
    */
   private void processInternal()
   {
      //System.err.println("[processInternal] " + Thread.currentThread().getName());

      try
      {

         int numSelectedKeys = 0;
         while ( numSelectedKeys == 0 && !shutdown )
         {

            // check pending unregistration requests
            processInternalRequest (unregistrationRequests);

            // check pending interestOps change requests
            processInternalRequest (interestOpsChangeRequests);

            //System.err.println("[select] " + Thread.currentThread().getName());

            selectorPending.incrementAndGet ();
            try
            {
               // possible improvement: do the low-latency trick (busy-wait for a moment, and then sleep)

               // wait for selection, but only if necessary
               /* int */
               numSelectedKeys = selector.selectedKeys ().size ();
               if ( numSelectedKeys == 0 )
                  numSelectedKeys = selector.select ();
            }
            finally
            {
               selectorPending.decrementAndGet ();
            }

            // check shutdown status
            if ( shutdown )
            {
               selector.close ();
               return;
            }

            // check pending registration requests
            processInternalRequest (registrationRequests);

         }

         // wake-up or forced selectNow()
         if ( numSelectedKeys == 0 || shutdown )
            return;

         //System.out.println();
         //System.out.println("[Selector selected # keys: " + numSelectedKeys + "]");

         Iterator<SelectionKey> selectedKeys = selector.selectedKeys ().iterator ();

         // process only one request per select (to support concurrent processing)
         SelectionKey selectedKey = selectedKeys.next ();

         // the definition of OP_WRITE in select agrees with the Unix definition, ie. not edge triggered like Win32
         // this means that you must add and remove OP_WRITE from the interestOps depending on the actual ability to write
         // clear SelectionKey.OP_WRITE here...
         int ops = 0;
         try
         {
            ops = selectedKey.interestOps ();
            if ( (ops & SelectionKey.OP_WRITE) == SelectionKey.OP_WRITE )
               selectedKey.interestOps (ops & (~SelectionKey.OP_WRITE));
         }
         catch ( CancelledKeyException cke )
         {
            // noop
         }

         // get handler as attachment
         ReactorHandler handler = (ReactorHandler) selectedKey.attachment ();

         // request accepted, remove key from set
         // NOTE: this has to be done before processing (to support concurrent processing)
         selectedKeys.remove ();

         try
         {
            // process request
            handler.handleEvent (selectedKey);
         }
         catch ( CancelledKeyException cke )
         {
            // noop
         }

      }
      catch ( Throwable th )
      {
         logger.log (Level.SEVERE, "Unexpected exception caught while processing selection keys.", th);
      }

      //System.err.println("[processInternal done] " + Thread.currentThread().getName());
   }

   /**
    * Unregisters <code>SelectableChannel</code> from the reactor.
    *
    * @param selectableChannel channel to be unregistered.
    */
   public void unregisterAndClose( SelectableChannel selectableChannel )
   {
      SelectionKey key = selectableChannel.keyFor (selector);
      synchronized ( unregistrationRequests )
      {
         unregistrationRequests.add (new DeregistrationRequest (key));
      }
      selector.wakeup ();
   }

   /**
    * Registers <code>SelectableChannel</code> to the reactor.
    *
    * @param selectableChannel channel to be registered.
    * @param interestOps       operations supported by channel (i.e. operations of interest).
    * @param handler           handle to process requests.
    * @return selector selection key.
    * @throws ClosedChannelException Channel closed
    */
   public SelectionKey register( SelectableChannel selectableChannel, int interestOps, ReactorHandler handler )
         throws ClosedChannelException
   {
      RegistrationRequest rr = new RegistrationRequest (selectableChannel, interestOps, handler);
      synchronized ( rr )
      {
         // pend registration request...
         synchronized ( registrationRequests )
         {
            registrationRequests.add (rr);
         }

         // check for shutdown status
         // NOTE: this has to be done in synchronization block after registering request
         if ( shutdown )
            throw new IllegalStateException ("Reactor is already shutdown.");

         // ... and wake-up selector to have this registration valid immediately
         selector.wakeup ();

         // if already awake process request immediately,
         // this is race-condition proof, since wake-up guarantees
         // that thread will we awaken immediately and will not select
         if ( selectorPending.get () == 0 )
            rr.process ();
         else
         {
            // wait for completion
            try
            {
               while ( !rr.isDone () )
                  rr.wait ();
            }
            catch ( InterruptedException ie )
            { /* noop */ }
         }
      }

      // registration during shutdown
      if ( shutdown )
         throw new IllegalStateException ("Reactor is shutting down.");
         // exception occurred during registration
      else if ( rr.getException () != null )
         throw rr.getException ();
         // return obtained key
      else
         return rr.getKey ();
   }

   /**
    * Change <code>SelectionKey</code> operations of interest.
    *
    * @param selectionKey selection key
    * @param interestOps  interest ops
    */
   private void setInterestOpsInternal( SelectionKey selectionKey, int interestOps )
   {
      // NOTE: interestOps blocks in W2K if there is a select active on the key
      // selectionKey.interestOps(interestOps);
      synchronized ( interestOpsChangeRequests )
      {
         interestOpsChangeRequests.add (new InterestOpsChangeRequest (selectionKey, interestOps));
      }
      selector.wakeup ();
   }

   /**
    * Disable selection key - sets its interest ops to 0 and stores its current interest ops (to be restored).
    * NOTE: to be called only (otherwise it can block) when select is not pending
    *
    * @param key selection key to be disabled.
    */
   public void disableSelectionKey( SelectionKey key )
   {
      synchronized ( disabledKeys )
      {
         if ( disabledKeys.containsKey (key) )
            return;

         // save current operations of interest
         int interestOps = key.interestOps ();
         disabledKeys.put (key, interestOps);

         // disable the key
         // since this can be called only when select is not pending, we can do this
         key.interestOps (0);
      }
   }

   /**
    * Enable selection key - restore its interest ops.
    * NOTE: can be called when select is pending
    *
    * @param key selection key to be enabled.
    */
   public void enableSelectionKey( SelectionKey key )
   {
      synchronized ( disabledKeys )
      {
         Integer ops = disabledKeys.remove (key);
         if ( ops != null )
            setInterestOpsInternal (key, ops.intValue ());
      }
   }

   /**
    * Change <code>SelectionKey</code> operations of interest.
    *
    * @param channel     channel
    * @param interestOps interest ops
    */
   public void setInterestOps( AbstractSelectableChannel channel, int interestOps )
   {
      SelectionKey key = channel.keyFor (selector);
      if ( key != null )
         setInterestOps (key, interestOps);
   }

   /**
    * Change <code>SelectionKey</code> operations of interest.
    * If channel is disabled, it changes its stored interest ops.
    *
    * @param selectionKey
    * @param interestOps
    */
   private void setInterestOps( SelectionKey selectionKey, int interestOps )
   {
      synchronized ( disabledKeys )
      {
         Integer ops = disabledKeys.get (selectionKey);
         if ( ops != null )
            disabledKeys.put (selectionKey, new Integer (interestOps));
         else
            setInterestOpsInternal (selectionKey, interestOps);
      }
   }

   /**
    * Shutdown the reactor.
    */
   public void shutdown()
   {
      if ( shutdown )
         return;

      synchronized ( shutdownMonitor )
      {
         shutdown = true;
         selector.wakeup ();

         try
         {
            shutdownMonitor.wait ();
         }
         catch ( InterruptedException e )
         { /* noop */ }

      }
   }

   /**
    * Get shutdown status.
    *
    * @return shutdown status.
    */
   public boolean isShutdown()
   {
      return shutdown;
   }
}

