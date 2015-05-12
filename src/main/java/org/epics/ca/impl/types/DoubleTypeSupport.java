package org.epics.ca.impl.types;

import java.nio.ByteBuffer;

final class DoubleTypeSupport implements TypeSupport {
	public static final DoubleTypeSupport INSTANCE = new DoubleTypeSupport();
	private static final Double DUMMY_INSTANCE = Double.valueOf(0);
	private DoubleTypeSupport() {};
	@Override
	public Object newInstance() { return DUMMY_INSTANCE; }
	@Override
	public int getDataType() { return 6; }
	@Override
	public int getForcedElementCount() { return 1; }
	@Override
	public void serialize(ByteBuffer buffer, Object object, int count) { buffer.putDouble((Double)object); }
	@Override
	public int serializeSize(Object object, int count) { return 8; };
	@Override
	public Object deserialize(ByteBuffer buffer, Object object, int count) { return buffer.getDouble(); }
}