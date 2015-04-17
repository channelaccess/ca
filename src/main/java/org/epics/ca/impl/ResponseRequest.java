package org.epics.ca.impl;

/**
 * An interface each request expecting response must implement.
 */
public interface ResponseRequest {

	/**
	 * Get I/O ID.
	 * @return ioid
	 */
	public int getIOID();

	/**
	 * Cancel response request (always to be called to complete/destroy).
	 */
	public void cancel();
	
	/**
	 * Exception response notification.
	 * @param errorCode	exception code.
	 * @param errorMessage	received detailed message.
	 */
	public void exception(int errorCode, String errorMessage);
	
}
