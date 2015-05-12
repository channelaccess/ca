package org.epics.ca.impl.types;

import java.nio.ByteBuffer;

final class DoubleArrayTypeSupport implements TypeSupport {
	public static final DoubleArrayTypeSupport INSTANCE = new DoubleArrayTypeSupport();
	private static final double[] DUMMY_INSTANCE = new double[0];
	private DoubleArrayTypeSupport() {};
	@Override
	public Object newInstance() { return DUMMY_INSTANCE; }
	@Override
	public int getDataType() { return 6; }
	@Override
	public int serializeSize(Object object, int count) { return 8 * count; };
	@Override
	public void serialize(ByteBuffer buffer, Object object, int count) {
		
		double[] array = (double[])object;
		if (count < TypeSupports.OPTIMIZED_COPY_THRESHOLD) {
			for (int i = 0; i < count; i++)
				buffer.putDouble(array[i]);
			
		}
		else
		{
			buffer.asDoubleBuffer().put(array, 0, count);
		}
	}
	@Override
	public Object deserialize(ByteBuffer buffer, Object object, int count) {
		
		double[] data;
		if (object == null)
		{
			data = new double[count];
		}
		else
		{
			data = (double[])object;
			// TODO think of "data.length < count"
			if (data.length != count)
				data = new double[count];
		}
		
		if (count < TypeSupports.OPTIMIZED_COPY_THRESHOLD) {
			for (int i = 0; i < count; i++)
				data[i] = buffer.getDouble();
		}
		else
		{
			buffer.asDoubleBuffer().get(data, 0, count);
		}
		
		return data;
	}
}