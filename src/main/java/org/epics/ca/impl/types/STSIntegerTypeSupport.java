package org.epics.ca.impl.types;

import java.nio.ByteBuffer;

import org.epics.ca.data.Alarm;

final class STSIntegerTypeSupport implements TypeSupport {
	public static final STSIntegerTypeSupport INSTANCE = new STSIntegerTypeSupport();
	private STSIntegerTypeSupport() {};
	@Override
	public Object newInstance() { return new Alarm<Integer>(); }
	@Override
	public int getDataType() { return 12; }
	@Override
	public int getForcedElementCount() { return 1; }
	@Override
	public Object deserialize(ByteBuffer buffer, Object object, int count)
	{ 
		@SuppressWarnings("unchecked")
		Alarm<Integer> data = (object == null) ? 
				(Alarm<Integer>)newInstance() : (Alarm<Integer>)object;

		TypeSupports.readAlarm(buffer, data);

		data.setValue(buffer.getInt());
		
		return data;
	}
}