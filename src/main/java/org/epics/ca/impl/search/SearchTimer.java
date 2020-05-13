package org.epics.ca.impl.search;

import java.util.Comparator;

/**
 * Timer. Based on <code>EDU.oswego.cs.dl.util.concurrent</code>. Timer tasks
 * should complete quickly. If a timer task takes excessive time to complete, it
 * "hogs" the timer's task execution thread. This can, in turn, delay the
 * execution of subsequent tasks, which may "bunch up" and execute in rapid
 * succession when (and if) the offending task finally completes.
 */
public class SearchTimer extends Thread
{

   /**
    * Tasks are maintained in a standard priority queue.
    **/
   protected final Heap heap = new Heap(64);

   protected final RunLoop runLoop = new RunLoop ();

   /**
    * Protected constructor (singleton pattern).
    */
   public SearchTimer()
   {
   }


   public static abstract class TimerTask implements Comparable<TimerTask>
   {
      private long timeToRun; // The time to run command
      private long delay; // The delay

      // Cancellation does not immediately remove node, it just
      // sets up lazy deletion bit, so is thrown away when next
      // encountered in run loop
      private boolean cancelled = false;

      // Access to cancellation status and and run time needs sync
      // since they can be written and read in different threads

      synchronized void setCancelled()
      {
         cancelled = true;
      }

      synchronized boolean getCancelled()
      {
         return cancelled;
      }

      // rt = now + d
      synchronized void setTimeToRun( long d, long rt )
      {
         delay = d;
         timeToRun = rt;
      }

      public synchronized long getTimeToRun()
      {
         return timeToRun;
      }

      public synchronized long getDelay()
      {
         return delay;
      }

      public int compareTo( TimerTask other )
      {
         long a = getTimeToRun ();
         long b = other.getTimeToRun ();
         return Long.compare(a, b);
      }

      /**
       * Method invoked by timer at requested time.
       *
       * @return delay (in ms) after which to reschedule,
       * not rescheduled if &lt;= 0.
       */
      public abstract long timeout();
   }

   /**
    * Execute the given command after waiting for the given delay.
    *
    * @param millisecondsToDelay -- the number of milliseconds from now to run the command.
    * @param task                -- timer task
    **/
   public synchronized void executeAfterDelay(
         long millisecondsToDelay,
         TimerTask task
   )
   {
      long runtime = System.currentTimeMillis () + millisecondsToDelay;
      task.setTimeToRun (millisecondsToDelay, runtime);
      heap.insert (task);
      restart ();
   }

   public synchronized void rescheduleAllAfterDelay( long millisecondsToDelay )
   {
      long timeToRun = System.currentTimeMillis () + millisecondsToDelay;

      synchronized ( heap )
      {
         Object[] nodes = heap.getNodes ();
         int count = heap.size ();
         for ( int i = 0; i < count; i++ )
            ((TimerTask) nodes[ i ]).setTimeToRun (millisecondsToDelay, timeToRun);
      }

      restart ();
   }

   /**
    * Cancel a scheduled task that has not yet been run. The task will be
    * cancelled upon the <em>next</em> opportunity to run it. This has no
    * effect if this is a one-shot task that has already executed. Also, if an
    * execution is in progress, it will complete normally. (It may however be
    * interrupted via getThread().interrupt()). But if it is a periodic task,
    * future iterations are cancelled.
    *
    * @param taskID -- a task reference returned by one of the execute commands
    * @throws ClassCastException if the taskID argument is not of the type returned by an
    *                            execute command.
    **/
   public static void cancel( Object taskID )
   {
      ((TimerTask) taskID).setCancelled ();
   }

   /**
    * The thread used to process commands
    **/
   protected Thread thread;

   /*
    * Return the thread being used to process commands, or null if there is no
    * such thread. You can use this to invoke any special methods on the
    * thread, for example, to interrupt it.
    */
   public synchronized Thread getThread()
   {
      return thread;
   }

   /**
    * set thread to null to indicate termination
    **/
   protected synchronized void clearThread()
   {
      thread = null;
   }

   /**
    * Start (or restart) a thread to process commands, or wake up an existing
    * thread if one is already running. This method can be invoked if the
    * background thread crashed due to an unrecoverable exception in an
    * executed command.
    **/

   public synchronized void restart()
   {
      if ( thread == null )
      {
         thread = new Thread (runLoop, this.getClass ().getName ());
         thread.start ();
      }
      else
         notify ();
   }

   /**
    * Cancel all tasks and interrupt the background thread executing the
    * current task, if any. A new background thread will be started if new
    * execution requests are encountered. If the currently executing task does
    * not respond to interrupts, the current thread may persist, even if a new
    * thread is started via restart().
    **/
   public synchronized void shutDown()
   {
      heap.clear ();
      if ( thread != null )
         thread.interrupt ();
      thread = null;
   }

   /**
    * Return the next task to execute, or null if thread is interrupted.
    *
    * @param blockAndExtract block and extract
    * @param dt dt
    * @return the timer task.
    **/
   protected synchronized TimerTask nextTask( boolean blockAndExtract, long dt )
   {
      // Note: This code assumes that there is only one run loop thread
      try
      {
         while ( !Thread.interrupted () )
         {

            // Using peek simplifies dealing with spurious wakeups

            TimerTask task = (TimerTask) (heap.peek ());

            if ( task == null )
            {
               if ( !blockAndExtract )
                  return null;
               wait ();
            }
            else
            {
               long now = System.currentTimeMillis ();
               long when = task.getTimeToRun ();

               if ( (when - dt) > now )
               { // false alarm wakeup
                  if ( !blockAndExtract )
                     return null;
                  wait (when - now);
               }
               else
               {
                  if ( !blockAndExtract )
                     return task;

                  task = (TimerTask) (heap.extract ());

                  if ( !task.getCancelled () )
                  { // Skip if cancelled by
                     return task;
                  }
               }
            }
         }
      }
      catch ( InterruptedException ignored )
      {
      } // fall through

      return null; // on interrupt
   }

   /**
    * Check whether there is a task scheduled in next "dT" ms.
    *
    * @param dT dT
    * @return boolean
    */
   public boolean hasNext( long dT )
   {
      return (nextTask (false, dT) != null);
   }

   /**
    * The run loop is isolated in its own Runnable class just so that the main
    * class need not implement Runnable, which would allow others to directly
    * invoke run, which is not supported.
    **/
   protected class RunLoop implements Runnable
   {
      public void run()
      {
         try
         {
            for ( ; ; )
            {
               TimerTask task = nextTask (true, 0);
               if ( task != null )
               {
                  long millisecondsToDelay = task.timeout ();
                  if ( millisecondsToDelay > 0 )
                  {
                     long runtime = System.currentTimeMillis ()
                           + millisecondsToDelay;
                     task.setTimeToRun (millisecondsToDelay, runtime);
                     heap.insert (task);
                  }
               }
               else
                  break;
            }
         }
         finally
         {
            clearThread ();
         }
      }
   }


   /**
    * A heap-based priority queue.
    * The class currently uses a standard array-based heap, as described
    * in, for example, Sedgewick's Algorithms text. All methods are fully synchronized.
    **/
   @SuppressWarnings( { "unchecked", "rawtypes" } )
   public static class Heap
   {

      /**
       * The tree nodes, packed into an array.
       */
      protected Object[] nodes_;

      /**
       * Number of used slots.
       */
      protected int count_ = 0; // number of used slots

      /**
       * Ordering comparator.
       */
      protected final Comparator cmp_;

      /**
       * Create a Heap with the given initial capacity and comparator
       *
       * @param capacity initial capacity.
       * @param cmp      comparator instance.
       * @throws IllegalArgumentException if capacity less or equal to zero
       **/
      public Heap( int capacity, Comparator cmp ) throws IllegalArgumentException
      {
         if ( capacity <= 0 )
            throw new IllegalArgumentException ();
         nodes_ = new Object[ capacity ];
         cmp_ = cmp;
      }

      /**
       * Create a Heap with the given capacity, and relying on natural ordering.
       *
       * @param capacity initial capacity.
       **/
      public Heap( int capacity )
      {
         this (capacity, null);
      }

      /**
       * Perform element comparisons using comparator or natural ordering.
       * @param a the first object to compare.
       * @param b the second object to compare.
       * @return the comparison result.
       **/
      protected int compare( Object a, Object b )
      {
         if ( cmp_ == null )
            return ((Comparable) a).compareTo (b);
         else
            return cmp_.compare (a, b);
      }

      /**
       * Get parent index.
       *
       * @param k k
       * @return number
       */
      protected final int parent( int k )
      {
         return (k - 1) / 2;
      }

      /**
       * Get left child.
       *
       * @param k k
       * @return number
       */
      protected final int left( int k )
      {
         return 2 * k + 1;
      }

      /**
       * Get right child.
       *
       * @param k k
       * @return number
       */
      protected final int right( int k )
      {
         return 2 * (k + 1);
      }

      /**
       * Insert an element, resize if necessary.
       *
       * @param x object to insert.
       **/
      public synchronized void insert( Object x )
      {
         if ( count_ >= nodes_.length )
         {
            int newcap = 3 * nodes_.length / 2 + 1;
            Object[] newnodes = new Object[ newcap ];
            System.arraycopy (nodes_, 0, newnodes, 0, nodes_.length);
            nodes_ = newnodes;
         }

         int k = count_;
         ++count_;
         while ( k > 0 )
         {
            int par = parent (k);
            if ( compare (x, nodes_[ par ]) < 0 )
            {
               nodes_[ k ] = nodes_[ par ];
               k = par;
            }
            else
               break;
         }
         nodes_[ k ] = x;
      }

      /**
       * Return and remove least element, or null if empty.
       *
       * @return extracted least element.
       **/
      public synchronized Object extract()
      {
         if ( count_ < 1 )
            return null;

         int k = 0; // take element at root;
         Object least = nodes_[ k ];
         --count_;
         Object x = nodes_[ count_ ];
         nodes_[ count_ ] = null;
         for ( ; ; )
         {
            int l = left (k);
            if ( l >= count_ )
               break;
            else
            {
               int r = right (k);
               int child = (r >= count_ || compare (nodes_[ l ], nodes_[ r ]) < 0) ? l
                     : r;
               if ( compare (x, nodes_[ child ]) > 0 )
               {
                  nodes_[ k ] = nodes_[ child ];
                  k = child;
               }
               else
                  break;
            }
         }
         nodes_[ k ] = x;
         return least;
      }

      /**
       * Return least element without removing it, or null if empty.
       *
       * @return least element.
       **/
      public synchronized Object peek()
      {
         if ( count_ > 0 )
            return nodes_[ 0 ];
         else
            return null;
      }

      /**
       * Return number of elements.
       *
       * @return number of elements.
       **/
      public synchronized int size()
      {
         return count_;
      }

      /**
       * Remove all elements.
       **/
      public synchronized void clear()
      {
         for ( int i = 0; i < count_; ++i )
            nodes_[ i ] = null;
         count_ = 0;
      }

      public synchronized Object[] getNodes()
      {
         return nodes_;
      }

   }

}
