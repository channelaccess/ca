package org.epics.ca.impl.types;

import java.nio.ByteBuffer;

import org.epics.ca.data.Graphic;
import org.epics.ca.impl.types.TypeSupports.ValueReader;

final class GraphicDoubleArrayTypeSupport implements TypeSupport, ValueReader<Double> {
	public static final GraphicDoubleArrayTypeSupport INSTANCE = new GraphicDoubleArrayTypeSupport();
	private GraphicDoubleArrayTypeSupport() {};
	@Override
	public Object newInstance() { return new Graphic<double[], Double>(); }
	@Override
	public int getDataType() { return 27; }
	
	@Override
	public Double readValue(ByteBuffer buffer, Double value) {
		return buffer.getDouble();
	}
	
	@Override
	public Object deserialize(ByteBuffer buffer, Object object, int count)
	{ 
		@SuppressWarnings("unchecked")
		Graphic<double[], Double> data = (object == null) ? 
				(Graphic<double[], Double>)newInstance() : (Graphic<double[], Double>)object;

		TypeSupports.readAlarm(buffer, data);
		
		TypeSupports.readPrecision(buffer, data);
		
		TypeSupports.readUnits(buffer, data);

		TypeSupports.readGraphicLimits(this, buffer, data);

		data.setValue((double[])DoubleArrayTypeSupport.INSTANCE.deserialize(buffer, data.getValue(), count));
		
		return data;
	}
}