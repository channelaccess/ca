package org.epics.ca.impl.reactor;

import java.nio.channels.SelectionKey;

/**
 * Reactor handler interface.
 */
public interface ReactorHandler {

	/**
	 * Process request of given <code>SelectionKey</code>.
	 * @param key key to be processed.
	 */
	public void handleEvent(SelectionKey key);

}
