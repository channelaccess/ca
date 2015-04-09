package org.epics.ca.impl;

import java.nio.ByteBuffer;

import org.epics.ca.Constants;

public final class Messages {
	
	/**
	 * Calculate aligned message size.
	 * @param align				alignment to be used.
	 * @param nonAlignedSize	current non-aligned size.
	 * @return aligned size.
	 */
	public static int calculateAlignedSize(int align, int nonAlignedSize)
	{
		return ((nonAlignedSize+align-1)/align)*align;
	}

	/**
	 * Start CA message.
	 * @param transport	transport to be used when sending.
	 * @return	filled buffer, if given buffer size is less that header size,
	 * 			then new buffer is allocated and returned.
	 */
	public static ByteBuffer startCAMessage(Transport transport,
											short command, int payloadSize,
											short dataType, int dataCount,
											int parameter1, int parameter2)
	{ 
		boolean useExtendedHeader = payloadSize >= 0xFFFF || dataCount >= 0xFFFF;
		
		// check if supported by current transport protocol revision
		if (useExtendedHeader && transport != null && transport.getMinorRevision() < 9) 
			throw new IllegalArgumentException("Out of bounds.");

		int requiredSize = useExtendedHeader ? 
								Constants.CA_EXTENDED_MESSAGE_HEADER_SIZE :
								Constants.CA_MESSAGE_HEADER_SIZE;
			
		ByteBuffer buffer = transport.acquireSendBuffer(requiredSize);
			
		// standard header
		if (!useExtendedHeader)
		{
			buffer.putShort(command);
			// conversion int -> unsigned short is done right
			buffer.putShort((short)payloadSize);
			buffer.putShort(dataType);
			// conversion int -> unsigned short is done right
			buffer.putShort((short)dataCount);
			buffer.putInt(parameter1);
			buffer.putInt(parameter2);
		}
		// extended header 
		else
		{
			buffer.putShort(command);
			buffer.putShort((short)0xFFFF);
			buffer.putShort(dataType);
			buffer.putShort((short)0x0000);
			buffer.putInt(parameter1);
			buffer.putInt(parameter2);
			buffer.putInt(payloadSize);
			buffer.putInt(dataCount);
		}

		return buffer;
	}
	
	/**
	 * Generate search request message.
	 * A special case implementation since message is sent via UDP.
	 * @param transport
	 * @param requestMessage
	 * @param name
	 * @param cid
	 */
	public static final boolean generateSearchRequestMessage(Transport transport, ByteBuffer buffer,
			String name, int cid)
	{
		// name length was already validated at channel creation time

		int unalignedMessageSize = Constants.CA_MESSAGE_HEADER_SIZE + name.length() + 1;
		int alignedMessageSize = calculateAlignedSize(8, unalignedMessageSize);
		if (buffer.remaining() < alignedMessageSize)
			return false;
		
		buffer.putShort((short)6);
		// conversion int -> unsigned short is done right
		buffer.putShort((short)(alignedMessageSize - Constants.CA_MESSAGE_HEADER_SIZE));
		buffer.putShort(Constants.CA_SEARCH_DONTREPLY);
		// conversion int -> unsigned short is done right
		buffer.putShort((short)transport.getMinorRevision());
		buffer.putInt(cid);
		buffer.putInt(cid);

		// append zero-terminated string and align message
		buffer.put(name.getBytes());
		// terminate with 0 and pad
        for (int i = alignedMessageSize - unalignedMessageSize + 1; i > 0; i--)
            buffer.put((byte)0);
		
		return true;
	}
	
	/**
	 * Generate version request message.
	 * @param transport
	 * @param buffer
	 * @param priority
	 * @param sequenceNumber
	 * @param isSequenceNumberValid
	 * @return generated version message buffer.
	 */
	public static final void generateVersionRequestMessage(
			Transport transport, ByteBuffer buffer, short priority, 
			int sequenceNumber, boolean isSequenceNumberValid)
	{
		short isSequenceNumberValidCode = isSequenceNumberValid ? (short)1 : (short)0;
		
		buffer.putShort((short)0);
		// conversion int -> unsigned short is done right
		buffer.putShort((short)0);
		buffer.putShort(isSequenceNumberValid ? isSequenceNumberValidCode : priority);
		// conversion int -> unsigned short is done right
		buffer.putShort((short)transport.getMinorRevision());
		buffer.putInt(sequenceNumber);
		buffer.putInt(0);
	}
	
}
