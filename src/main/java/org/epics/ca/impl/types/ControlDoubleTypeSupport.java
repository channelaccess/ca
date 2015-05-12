package org.epics.ca.impl.types;

import java.nio.ByteBuffer;

import org.epics.ca.data.Control;

final class ControlDoubleTypeSupport implements TypeSupport {
	public static final ControlDoubleTypeSupport INSTANCE = new ControlDoubleTypeSupport();
	private ControlDoubleTypeSupport() {};
	@Override
	public Object newInstance() { return new Control<Double, Double>(); }
	@Override
	public int getDataType() { return 34; }
	@Override
	public int getForcedElementCount() { return 1; }
	
	@Override
	public Object deserialize(ByteBuffer buffer, Object object, int count)
	{ 
		@SuppressWarnings("unchecked")
		Control<Double, Double> data = (object == null) ? 
				(Control<Double, Double>)newInstance() : (Control<Double, Double>)object;

		TypeSupports.readAlarm(buffer, data);
		TypeSupports.readPrecision(buffer, data);
		TypeSupports.readUnits(buffer, data);
		TypeSupports.readGraphicLimits(GraphicDoubleTypeSupport.INSTANCE, buffer, data);
		TypeSupports.readControlLimits(GraphicDoubleTypeSupport.INSTANCE, buffer, data);

		data.setValue((Double)DoubleTypeSupport.INSTANCE.deserialize(buffer, data.getValue(), count));
		
		return data;
	}
}