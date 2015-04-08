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
	 * @param transport		response source transport.
	 * @param header		CA message header.
	 * @param response		payload buffer.
	 */
	public void handleResponse(InetSocketAddress responseFrom, Transport transport, Header header, ByteBuffer payloadBuffer);

}
