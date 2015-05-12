package org.epics.ca.impl.types;

import java.nio.ByteBuffer;

import org.epics.ca.data.Timestamped;

final class TimeDoubleTypeSupport implements TypeSupport {
	public static final TimeDoubleTypeSupport INSTANCE = new TimeDoubleTypeSupport();
	private TimeDoubleTypeSupport() {};
	@Override
	public Object newInstance() { return new Timestamped<Double>(); }
	@Override
	public int getDataType() { return 20; }
	@Override
	public int getForcedElementCount() { return 1; }
	@Override
	public Object deserialize(ByteBuffer buffer, Object object, int count)
	{ 
		@SuppressWarnings("unchecked")
		Timestamped<Double> data = (object == null) ? 
				(Timestamped<Double>)newInstance() : (Timestamped<Double>)object;

		TypeSupports.readAlarm(buffer, data);
		TypeSupports.readTimestamp(buffer, data);
		
		// RISC padding
		buffer.getInt();

		data.setValue((Double)DoubleTypeSupport.INSTANCE.deserialize(buffer, data.getValue(), count));
		
		return data;
	}
}