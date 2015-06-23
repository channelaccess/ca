package org.epics.ca.impl.reactor.lf;

import java.nio.channels.SelectionKey;

import org.epics.ca.impl.reactor.Reactor;
import org.epics.ca.impl.reactor.ReactorHandler;

/**
 * Decorator pattern to 'decorate' application-specific event processing handler
 * to work with Leader/Followers deisgn pattern. This decorator promotes a new leader
 * thread and disables/enables the handler. 
 */
public class LeaderFollowersHandler implements ReactorHandler, Runnable {
    
    /**
     * Reactor to serve.
     */
    protected Reactor reactor;

    /**
     * Application-specific event processing handler.
     */
    protected ReactorHandler handler;
    
    /**
     * Leader-followers thread pool.
     */
    protected LeaderFollowersThreadPool threadPool;

    /**
     * Constructor.
     * @param reactor reactor to handle.
     * @param handler application-specific event processing handler.
     * @param threadPool leader-followers thread pool.
     */
    public LeaderFollowersHandler(Reactor reactor, ReactorHandler handler, LeaderFollowersThreadPool threadPool) {
        this.reactor = reactor;
        this.handler = handler;
        this.threadPool = threadPool;
    }
    
    /**
     * @see org.epics.ca.impl.reactor.ReactorHandler#handleEvent(java.nio.channels.SelectionKey)
     */
    public void handleEvent(SelectionKey key)
    {
    	// if not valid, just promote new leader
    	if (!key.isValid())
    	{
	        threadPool.promoteLeader(this);
	        return;
    	}
    	
        reactor.disableSelectionKey(key);
        try
        {
	        // promote a follower thread to become a leader
	        threadPool.promoteLeader(this);
//System.err.println("[processing] " + Thread.currentThread().getName());
	        // dispatch application-specific event processing code
	        handler.handleEvent(key);
        }
        finally
        {
            // enable the handle in the reactor
            reactor.enableSelectionKey(key);
        }
    }

    /**
     * @see java.lang.Runnable#run()
     */
    public void run()
    {
    	reactor.process();
    }
    
}
