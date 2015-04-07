package org.epics.ca.impl;

import java.net.InetSocketAddress;

/**
 * Interface defining transport (connection).
 */
public interface Transport {

	/**
	 * Get remote address.
	 * @return remote address.
	 */
	public InetSocketAddress getRemoteAddress();

	/**
	 * Get context transport is living in.
	 * @return context transport is living in.
	 */
	public ContextImpl getContext();
	
	/**
	 * Transport protocol minor revision.
	 * @return protocol minor revision.
	 */
	public short getMinorRevision();

}
