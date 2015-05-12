package org.epics.ca.impl.types;

import java.nio.ByteBuffer;

import com.lmax.disruptor.EventFactory;

@SuppressWarnings("rawtypes")
public interface TypeSupport extends EventFactory {
	//public Object newInstance();
	public int getDataType();
	public default int getForcedElementCount() { return 0; }
	public default void serialize(ByteBuffer buffer, Object object, int count) { throw new UnsupportedOperationException(); };
	public default int serializeSize(Object object, int count) { throw new UnsupportedOperationException(); };
	public Object deserialize(ByteBuffer buffer, Object object, int count);
}