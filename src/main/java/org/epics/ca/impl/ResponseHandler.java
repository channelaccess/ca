package org.epics.ca.impl;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * Interface defining response handler.
 */
public interface ResponseHandler {

	/**
	 * Handle response.
	 * @param responseFrom	remove address of the responder, <code>null</code> if unknown. 
	 * @param transport	response source transport.
	 * @param response	array of response messages to handle.
	 * 					First buffer in array has to contain whole (extended) message header.
	 */
	public void handleResponse(InetSocketAddress responseFrom, Transport transport, ByteBuffer[] response);

}
