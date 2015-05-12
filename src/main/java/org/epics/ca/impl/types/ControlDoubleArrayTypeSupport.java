package org.epics.ca.impl.types;

import java.nio.ByteBuffer;

import org.epics.ca.data.Control;

final class ControlDoubleArrayTypeSupport implements TypeSupport {
	public static final ControlDoubleArrayTypeSupport INSTANCE = new ControlDoubleArrayTypeSupport();
	private ControlDoubleArrayTypeSupport() {};
	@Override
	public Object newInstance() { return new Control<double[], Double>(); }
	@Override
	public int getDataType() { return 34; }
	
	@Override
	public Object deserialize(ByteBuffer buffer, Object object, int count)
	{ 
		@SuppressWarnings("unchecked")
		Control<double[], Double> data = (object == null) ? 
				(Control<double[], Double>)newInstance() : (Control<double[], Double>)object;

		TypeSupports.readAlarm(buffer, data);
		TypeSupports.readPrecision(buffer, data);
		TypeSupports.readUnits(buffer, data);
		TypeSupports.readGraphicLimits(GraphicDoubleTypeSupport.INSTANCE, buffer, data);
		TypeSupports.readControlLimits(GraphicDoubleTypeSupport.INSTANCE, buffer, data);

		data.setValue((double[])DoubleArrayTypeSupport.INSTANCE.deserialize(buffer, data.getValue(), count));
		
		return data;
	}
}