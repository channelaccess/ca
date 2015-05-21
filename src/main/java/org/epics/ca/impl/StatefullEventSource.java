package org.epics.ca.impl;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class StatefullEventSource implements Runnable
{
	protected final AtomicBoolean enqueued = new AtomicBoolean();

	// returns true if event can be enqueue,
	// false if not (i.e. already enqueued or destroyed)
	public boolean allowEnqueue()
	{
		return enqueued.compareAndSet(false, true);
	}

	// dispatches the event
	// (and clears already enqueued flag)
	public void run()
	{
		enqueued.set(false);
		dispatch();
	}
	
	public abstract void dispatch();
}
