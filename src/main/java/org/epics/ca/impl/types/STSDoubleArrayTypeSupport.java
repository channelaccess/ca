package org.epics.ca.impl.types;

import java.nio.ByteBuffer;

import org.epics.ca.data.Alarm;

final class STSDoubleArrayTypeSupport implements TypeSupport {
	public static final STSDoubleArrayTypeSupport INSTANCE = new STSDoubleArrayTypeSupport();
	private STSDoubleArrayTypeSupport() {};
	@Override
	public Object newInstance() { return new Alarm<double[]>(); }
	@Override
	public int getDataType() { return 13; }
	@Override
	public Object deserialize(ByteBuffer buffer, Object object, int count)
	{ 
		@SuppressWarnings("unchecked")
		Alarm<double[]> data = (object == null) ? 
				(Alarm<double[]>)newInstance() : (Alarm<double[]>)object;

		TypeSupports.readAlarm(buffer, data);

		// RISC padding
		buffer.getInt();

		data.setValue((double[])DoubleArrayTypeSupport.INSTANCE.deserialize(buffer, data.getValue(), count));
		
		return data;
	}
}