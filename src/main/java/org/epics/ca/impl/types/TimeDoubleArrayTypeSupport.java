package org.epics.ca.impl.types;

import java.nio.ByteBuffer;

import org.epics.ca.data.Timestamped;

final class TimeDoubleArrayTypeSupport implements TypeSupport {
	public static final TimeDoubleArrayTypeSupport INSTANCE = new TimeDoubleArrayTypeSupport();
	private TimeDoubleArrayTypeSupport() {};
	@Override
	public Object newInstance() { return new Timestamped<double[]>(); }
	@Override
	public int getDataType() { return 20; }
	@Override
	public Object deserialize(ByteBuffer buffer, Object object, int count)
	{ 
		@SuppressWarnings("unchecked")
		Timestamped<double[]> data = (object == null) ? 
				(Timestamped<double[]>)newInstance() : (Timestamped<double[]>)object;

		TypeSupports.readAlarm(buffer, data);
		TypeSupports.readTimestamp(buffer, data);
		
		// RISC padding
		buffer.getInt();

		data.setValue((double[])DoubleArrayTypeSupport.INSTANCE.deserialize(buffer, data.getValue(), count));
		
		return data;
	}
}