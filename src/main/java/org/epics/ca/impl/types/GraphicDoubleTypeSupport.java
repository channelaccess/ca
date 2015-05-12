package org.epics.ca.impl.types;

import java.nio.ByteBuffer;

import org.epics.ca.data.Graphic;
import org.epics.ca.impl.types.TypeSupports.ValueReader;

final class GraphicDoubleTypeSupport implements TypeSupport, ValueReader<Double> {
	public static final GraphicDoubleTypeSupport INSTANCE = new GraphicDoubleTypeSupport();
	private GraphicDoubleTypeSupport() {};
	@Override
	public Object newInstance() { return new Graphic<Double, Double>(); }
	@Override
	public int getDataType() { return 27; }
	@Override
	public int getForcedElementCount() { return 1; }
	
	@Override
	public Double readValue(ByteBuffer buffer, Double value) {
		return buffer.getDouble();
	}
	
	@Override
	public Object deserialize(ByteBuffer buffer, Object object, int count)
	{ 
		@SuppressWarnings("unchecked")
		Graphic<Double, Double> data = (object == null) ? 
				(Graphic<Double, Double>)newInstance() : (Graphic<Double, Double>)object;

		TypeSupports.readAlarm(buffer, data);
		TypeSupports.readPrecision(buffer, data);
		TypeSupports.readUnits(buffer, data);
		TypeSupports.readGraphicLimits(this, buffer, data);

		data.setValue((Double)DoubleTypeSupport.INSTANCE.deserialize(buffer, data.getValue(), count));
		
		return data;
	}
}