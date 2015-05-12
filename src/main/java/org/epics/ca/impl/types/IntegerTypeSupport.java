package org.epics.ca.impl.types;

import java.nio.ByteBuffer;

final class IntegerTypeSupport implements TypeSupport {
	public static final IntegerTypeSupport INSTANCE = new IntegerTypeSupport();
	public static final Integer DUMMY_INSTANCE = Integer.valueOf(0);
	private IntegerTypeSupport() {};
	@Override
	public Object newInstance() { return DUMMY_INSTANCE; }
	@Override
	public int getDataType() { return 5; }
	@Override
	public int getForcedElementCount() { return 1; }
	@Override
	public int serializeSize(Object object, int count) { return 4; };
	@Override
	public void serialize(ByteBuffer buffer, Object object, int count) { buffer.putInt((Integer)object); }
	@Override
	public Object deserialize(ByteBuffer buffer, Object object, int count) { return buffer.getInt(); }
}