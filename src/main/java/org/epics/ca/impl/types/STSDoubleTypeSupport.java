package org.epics.ca.impl.types;

import java.nio.ByteBuffer;

import org.epics.ca.data.Alarm;

final class STSDoubleTypeSupport implements TypeSupport {
	public static final STSDoubleTypeSupport INSTANCE = new STSDoubleTypeSupport();
	private STSDoubleTypeSupport() {};
	@Override
	public Object newInstance() { return new Alarm<Double>(); }
	@Override
	public int getDataType() { return 13; }
	@Override
	public int getForcedElementCount() { return 1; }
	@Override
	public Object deserialize(ByteBuffer buffer, Object object, int count)
	{ 
		@SuppressWarnings("unchecked")
		Alarm<Double> data = (object == null) ? 
				(Alarm<Double>)newInstance() : (Alarm<Double>)object;

		TypeSupports.readAlarm(buffer, data);

		// RISC padding
		buffer.getInt();

		data.setValue((Double)DoubleTypeSupport.INSTANCE.deserialize(buffer, data.getValue(), count));
		
		return data;
	}
}