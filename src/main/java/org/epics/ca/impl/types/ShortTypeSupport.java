package org.epics.ca.impl.types;

import java.nio.ByteBuffer;

final class ShortTypeSupport implements TypeSupport {
	public static final ShortTypeSupport INSTANCE = new ShortTypeSupport();
	public static final Short DUMMY_INSTANCE = Short.valueOf((short)0);
	private ShortTypeSupport() {};
	@Override
	public Object newInstance() { return DUMMY_INSTANCE; }
	@Override
	public int getDataType() { return 1; }
	@Override
	public int getForcedElementCount() { return 1; }
	@Override
	public int serializeSize(Object object, int count) { return 2; };
	@Override
	public void serialize(ByteBuffer buffer, Object object, int count) { buffer.putShort(((Short)object)); }
	@Override
	public Object deserialize(ByteBuffer buffer, Object object, int count) { return buffer.getShort(); }
}