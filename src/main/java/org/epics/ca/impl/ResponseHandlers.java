package org.epics.ca.impl;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.logging.Logger;

import org.epics.ca.Constants;
import org.epics.ca.Status;
import org.epics.ca.util.net.InetAddressUtil;

public class ResponseHandlers {

	// Get Logger
	private static final Logger logger = Logger.getLogger(ResponseHandlers.class.getName());

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

	// TODO beacon monitoring
	
	private static final ResponseHandler[] handlers = 
		{
			ResponseHandlers::noopResponse,	/* 0 */
			ResponseHandlers::notifyResponse,	/* 1 - monitor */
			ResponseHandlers::badResponse,	/* 2 */
			ResponseHandlers::badResponse,	/* 3 */
			ResponseHandlers::badResponse,	/* 4 */
			ResponseHandlers::badResponse,	/* 5 */
			ResponseHandlers::searchResponse,	/* 6 */
			ResponseHandlers::badResponse,	/* 7 */
			ResponseHandlers::badResponse,	/* 8 */
			ResponseHandlers::badResponse,	/* 9 */
			ResponseHandlers::badResponse,	/* 10 */
			ResponseHandlers::badResponse,	/* 11 */	// TODO
			ResponseHandlers::badResponse,	/* 12 */
			ResponseHandlers::noopResponse,	/* 13 */ // TODO
			ResponseHandlers::badResponse,	/* 14 */
			ResponseHandlers::notifyResponse,	/* 15 - read */
			ResponseHandlers::badResponse,	/* 16 */
			ResponseHandlers::repeaterConfirmResponse,	/* 17 */
			ResponseHandlers::channelCreateResponse,	/* 18 */
			ResponseHandlers::notifyResponse,	/* 19 - write */
			ResponseHandlers::badResponse,	/* 20 */
			ResponseHandlers::badResponse,	/* 21 */
			ResponseHandlers::accessRightsResponse,	/* 22 */
			ResponseHandlers::badResponse,	/* 23 */
			ResponseHandlers::badResponse,	/* 24 */
			ResponseHandlers::badResponse,	/* 25 */
			ResponseHandlers::badResponse,	/* 26 */
			ResponseHandlers::badResponse	/* 27 */
		};
	
	public static void handleResponse(InetSocketAddress responseFrom, Transport transport, Header header, ByteBuffer payloadBuffer)
	{
		if (header.command < 0 || header.command >= handlers.length)
		{
			logger.warning(() -> "Invalid response message (command = " + header.command + ") received from: " + responseFrom);
			return;
		}
		
		//logger.finest(() -> "Message " + header.command + " received from " + responseFrom + ", payload size " + header.payloadSize + "."); 
		
		handlers[header.command].handleResponse(responseFrom, transport, header, payloadBuffer);
	}

	public static void noopResponse(InetSocketAddress responseFrom, Transport transport, Header header, ByteBuffer payloadBuffer)
	{
	}

	public static void badResponse(InetSocketAddress responseFrom, Transport transport, Header header, ByteBuffer payloadBuffer)
	{
		logger.warning(() -> "Unexpected response message (command = " + header.command + ") received from: " + responseFrom);
	}

	public static void searchResponse(InetSocketAddress responseFrom, Transport transport, Header header, ByteBuffer payloadBuffer)
	{
		short minorVersion = Constants.CA_UNKNOWN_MINOR_PROTOCOL_REVISION;
		
		// Starting with CA V4.1 the minor version number is
		// appended to the end of each search reply.
		if (header.payloadSize >= 2 /* short size = 2 bytes */)
			minorVersion = payloadBuffer.getShort();
		
		// signed short conversion -> signed int 
		int port = header.dataType & 0xFFFF;

		// CA v4.8 or newer
		if (minorVersion >= 8)
		{
			InetAddress addr;
			
			// get address
			final int INADDR_BROADCAST = 0xFFFFFFFF; 
			if (header.parameter1 != INADDR_BROADCAST)
				addr = InetAddressUtil.intToIPv4Address(header.parameter1);
			else
				addr = responseFrom.getAddress(); 			
			
			responseFrom = new InetSocketAddress(addr, port);
		}
		// CA v4.5 - 4.7
		else if (minorVersion >= 5 )
		{
			responseFrom = new InetSocketAddress(responseFrom.getAddress(), port);
		}
		// CA v4.1 - 4.6
		else
		{
			responseFrom = new InetSocketAddress(responseFrom.getAddress(), transport.getContext().getServerPort());
		}

		// CA v4.2 or newer
		if (minorVersion >= 2)
		{
			/** cid, sid, type, count, minorVersion, serverAddress */
			transport.getContext().searchResponse(header.parameter2, header.parameter1,
					(short)-1, 0,
					minorVersion, responseFrom);
		}
		else
		{
			/** cid, sid, type, count, minorVersion, serverAddress */
			transport.getContext().searchResponse(header.parameter2, header.parameter1,
					header.dataType, header.dataCount,
					minorVersion, responseFrom);
		}
	}

	public static void accessRightsResponse(InetSocketAddress responseFrom, Transport transport, Header header, ByteBuffer payloadBuffer)
	{
		ChannelImpl<?> channel = transport.getContext().getChannel(header.parameter1);
		if (channel != null)
			channel.setAccessRights(header.parameter2);
	}

	public static void channelCreateResponse(InetSocketAddress responseFrom, Transport transport, Header header, ByteBuffer payloadBuffer)
	{
		ChannelImpl<?> channel = transport.getContext().getChannel(header.parameter1);
		if (channel != null)
			channel.connectionCompleted(header.parameter2, header.dataType, header.dataCount);
	}

	public static void notifyResponse(InetSocketAddress responseFrom, Transport transport, Header header, ByteBuffer payloadBuffer)
	{
		NotifyResponseRequest nrr = (NotifyResponseRequest)transport.getContext().getResponseRequest(header.parameter2);
		if (nrr == null)
			return;
				
		int status;
		if (transport.getMinorRevision() < 1)
			status = Status.NORMAL.getValue();
		else
			status = header.parameter1;
			
		nrr.response(status, header.dataType, header.dataCount, payloadBuffer);					
	}

	public static void repeaterConfirmResponse(InetSocketAddress responseFrom, Transport transport, Header header, ByteBuffer payloadBuffer)
	{
		transport.getContext().repeaterConfirm(responseFrom);
	}
}
