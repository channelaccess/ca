package org.epics.ca.impl.reactor.lf;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LF thread pool implementation.
 */
public class LeaderFollowersThreadPool {
	
	// Get Logger
	private static final Logger logger = Logger.getLogger(LeaderFollowersThreadPool.class.getName());
	
	/**
	 * Default thread pool size.
	 */
	public static final int DEFAULT_THREADPOOL_SIZE = 5;
	
    /**
     * Shutdown status flag.
     */
    private volatile boolean shutdown = false;
    
    /**
     * Executor.
     */
    private ThreadPoolExecutor executor;

    /**
     * Constructor.
     */
    public LeaderFollowersThreadPool() {
        
        int threadPoolSize = DEFAULT_THREADPOOL_SIZE;
        String strVal = System.getProperty(this.getClass().getName()+".thread_pool_size", String.valueOf(threadPoolSize));
        if (strVal != null)
        {
        	try	{
        	    // minimum are two threads (leader and one follower)
        		threadPoolSize = Math.max(2, Integer.parseInt(strVal));
        	}
        	catch (NumberFormatException nfe) { /* noop */ }
        }

        // TODO consider using LIFO ordering of threads (to maximize CPU cache affinity)
        // unbounded queue is OK, since its naturally limited (threadPoolSize + # of transports (used for flushing))
        executor = new ThreadPoolExecutor(threadPoolSize, threadPoolSize,
        								  Long.MAX_VALUE, TimeUnit.NANOSECONDS,
        								  new LinkedBlockingQueue<Runnable>());
        executor.prestartAllCoreThreads();
    }

    /**
     * Promote a new leader.
     * @param task task to execute by a new leader.
     */
    public void promoteLeader(Runnable task) 
    {
        //System.err.println("[promoteLeader] by " + Thread.currentThread().getName());
        execute(task);
    }
    
    /**
     * Execute given task.
     * @param task task to execute.
     */
    public void execute(Runnable task) 
    {
        try
        {
            executor.execute(task);
        } catch (Throwable th) { 
        	/* noop */
        	logger.log(Level.SEVERE, "Unexpected exception caught in one of the LF thread-pool thread.", th);
        }
    }
    
    /**
     * Shutdown.
     */
    public synchronized void shutdown()
    {
    	if (shutdown)
    		return;
    	shutdown = true;

    	executor.shutdown();
        try {
        	// NOTE: if thead pool is shutdown from one of its threads, this will always block for 1s
            if (!executor.awaitTermination(1, TimeUnit.SECONDS))
            	executor.shutdownNow();
        } catch (InterruptedException ie) { /* noop */ } 
    }
    
}
