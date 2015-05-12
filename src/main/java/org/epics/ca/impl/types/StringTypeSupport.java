package org.epics.ca.impl.types;

import java.nio.ByteBuffer;

final class StringTypeSupport implements TypeSupport {
	public static final StringTypeSupport INSTANCE = new StringTypeSupport();
	public static final String DUMMY_INSTANCE = "";
	private StringTypeSupport() {};
	@Override
	public Object newInstance() { return DUMMY_INSTANCE; }
	@Override
	public int getDataType() { return 0; }
	@Override
	public int getForcedElementCount() { return 1; }
	@Override
	public void serialize(ByteBuffer buffer, Object object, int count) { 
		buffer.put(((String)object).getBytes());
		buffer.put((byte)0);
	}
	@Override
	public Object deserialize(ByteBuffer buffer, Object object, int count) {
		
		int start = buffer.position();
		final int bufferEnd = buffer.limit();
		int end = start;
		
		// find zero char (string terminator)
		while (buffer.get(end) != 0 && end < bufferEnd)
			end++;

		// If the buffer is array backed, we can simply
		// use it directly. If not, we need to make a copy
		if (buffer.hasArray()) {
			// NOTE: rest of the bytes are left in the buffer
			return new String(buffer.array(), start, end-start);
		}
		else
		{
			int length = end-start;
			byte[] data = new byte[length];
			if (length < TypeSupports.OPTIMIZED_COPY_THRESHOLD) {
				for (int i = 0; i < length; i++)
					data[i] = buffer.get();
			} else {
				buffer.get(data, 0, length);
			}
			return new String(data, 0, length);
		}
	}
}