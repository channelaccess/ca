package org.epics.ca.impl;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.epics.ca.data.Alarm;
import org.epics.ca.data.AlarmSeverity;
import org.epics.ca.data.AlarmStatus;
import org.epics.ca.data.Control;
import org.epics.ca.data.Graphic;
import org.epics.ca.data.GraphicEnum;
import org.epics.ca.data.Timestamped;

import com.lmax.disruptor.EventFactory;

public class TypeSupports {

	/**
	 * Threshold at which the method to copy the buffer is changed.
	 * If lower, the elements are copied one by one. If higher, the
	 * data is copied with ByteBuffer bulk operations.
	 * <p>
	 * As of JDK 1.7 the optimization at lower count is desirable because there is a
	 * fair amount of logic implemented in ByteBuffer bulk operations
	 * to determine which methods are available and which one is more
	 * efficient.
	 */
	private static final int OPTIMIZED_COPY_THRESHOLD = 128;

	/**
	 * Create (extract) string (zero-terminated) from byte buffer.
	 * @param rawBuffer
	 * @return decoded DBR.
	 */
	private final static String extractString(byte[] rawBuffer)
	{
		int len = 0;
		final int rawBufferLen = rawBuffer.length;
		while (len < rawBufferLen && rawBuffer[len] != 0)
			len++;
		return new String(rawBuffer, 0, len);
	}
	
	private final static void readAlarm(ByteBuffer buffer, Alarm<?> data)
	{
		int status = buffer.getShort() & 0xFFFF;
		int severity = buffer.getShort() & 0xFFFF;

		data.setAlarmStatus(AlarmStatus.values()[status]);
		data.setAlarmSeverity(AlarmSeverity.values()[severity]);
	}

	private final static void readTimestamp(ByteBuffer buffer, Timestamped<?> data)
	{
		// seconds since 0000 Jan 1, 1990
		long secPastEpoch = buffer.getInt() & 0x00000000FFFFFFFFL;
		// nanoseconds within second
		int nsec = buffer.getInt();

		data.setSeconds(secPastEpoch + Timestamped.EPOCH_SECONDS_PAST_1970);
		data.setNanos(nsec);
	}
	
	private final static void readUnits(ByteBuffer buffer, Graphic<?, ?> data)
	{
		final int MAX_UNITS_SIZE = 8;
		byte[] rawUnits = new byte[MAX_UNITS_SIZE];
		buffer.get(rawUnits);

		data.setUnits(extractString(rawUnits));			
	}
	
	private final static void readPrecision(ByteBuffer buffer, Graphic<?, ?> data)
	{
		int precision = buffer.getShort() & 0xFFFF;
		data.setPrecision(precision);

		// RISC padding
		buffer.getShort();
	}
	
	private static interface ValueReader<T>
	{
		T readValue(ByteBuffer buffer, T value);
	}
	
	private final static <T> void readGraphicLimits(ValueReader<T> valueReader, ByteBuffer buffer, Graphic<?, T> data)
	{
		data.setUpperDisplay(valueReader.readValue(buffer, data.getUpperDisplay()));
		data.setLowerDisplay(valueReader.readValue(buffer, data.getLowerDisplay()));
		data.setUpperAlarm  (valueReader.readValue(buffer, data.getUpperAlarm()));
		data.setUpperWarning(valueReader.readValue(buffer, data.getUpperWarning()));
		data.setLowerWarning(valueReader.readValue(buffer, data.getLowerWarning()));
		data.setLowerAlarm  (valueReader.readValue(buffer, data.getLowerAlarm()));
	}
	
	private final static <T> void readControlLimits(ValueReader<T> valueReader, ByteBuffer buffer, Control<?, T> data)
	{
		data.setUpperControl(valueReader.readValue(buffer, data.getUpperControl()));
		data.setLowerControl(valueReader.readValue(buffer, data.getLowerControl()));
	}	
	
	@SuppressWarnings("rawtypes")
	public static interface TypeSupport extends EventFactory {
		//public Object newInstance();
		public int getDataType();
		public default int getForcedElementCount() { return 0; }
		public default void serialize(ByteBuffer buffer, Object object, int count) { throw new UnsupportedOperationException(); };
		public default int serializeSize(Object object, int count) { throw new UnsupportedOperationException(); };
		public Object deserialize(ByteBuffer buffer, Object object, int count);
	}
	
	private static final class DoubleTypeSupport implements TypeSupport {
		public static final DoubleTypeSupport INSTANCE = new DoubleTypeSupport();
		private static final Double DUMMY_INSTANCE = Double.valueOf(0);
		private DoubleTypeSupport() {};
		@Override
		public Object newInstance() { return DUMMY_INSTANCE; }
		@Override
		public int getDataType() { return 6; }
		@Override
		public int getForcedElementCount() { return 1; }
		@Override
		public void serialize(ByteBuffer buffer, Object object, int count) { buffer.putDouble((Double)object); }
		@Override
		public int serializeSize(Object object, int count) { return 8; };
		@Override
		public Object deserialize(ByteBuffer buffer, Object object, int count) { return buffer.getDouble(); }
	}

	private static final class STSDoubleTypeSupport implements TypeSupport {
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

			readAlarm(buffer, data);

			// RISC padding
			buffer.getInt();

			data.setValue((Double)DoubleTypeSupport.INSTANCE.deserialize(buffer, data.getValue(), count));
			
			return data;
		}
	}

	private static final class TimeDoubleTypeSupport implements TypeSupport {
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

			readAlarm(buffer, data);
			readTimestamp(buffer, data);
			
			// RISC padding
			buffer.getInt();

			data.setValue((Double)DoubleTypeSupport.INSTANCE.deserialize(buffer, data.getValue(), count));
			
			return data;
		}
	}

	private static final class GraphicDoubleTypeSupport implements TypeSupport, ValueReader<Double> {
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

			readAlarm(buffer, data);
			readPrecision(buffer, data);
			readUnits(buffer, data);
			readGraphicLimits(this, buffer, data);

			data.setValue((Double)DoubleTypeSupport.INSTANCE.deserialize(buffer, data.getValue(), count));
			
			return data;
		}
	}

	private static final class ControlDoubleTypeSupport implements TypeSupport {
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

			readAlarm(buffer, data);
			readPrecision(buffer, data);
			readUnits(buffer, data);
			readGraphicLimits(GraphicDoubleTypeSupport.INSTANCE, buffer, data);
			readControlLimits(GraphicDoubleTypeSupport.INSTANCE, buffer, data);

			data.setValue((Double)DoubleTypeSupport.INSTANCE.deserialize(buffer, data.getValue(), count));
			
			return data;
		}
	}

	private static final class DoubleArrayTypeSupport implements TypeSupport {
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
			if (count < OPTIMIZED_COPY_THRESHOLD) {
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
			
			if (count < OPTIMIZED_COPY_THRESHOLD) {
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

	private static final class STSDoubleArrayTypeSupport implements TypeSupport {
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

			readAlarm(buffer, data);

			// RISC padding
			buffer.getInt();

			data.setValue((double[])DoubleArrayTypeSupport.INSTANCE.deserialize(buffer, data.getValue(), count));
			
			return data;
		}
	}

	private static final class TimeDoubleArrayTypeSupport implements TypeSupport {
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

			readAlarm(buffer, data);
			readTimestamp(buffer, data);
			
			// RISC padding
			buffer.getInt();

			data.setValue((double[])DoubleArrayTypeSupport.INSTANCE.deserialize(buffer, data.getValue(), count));
			
			return data;
		}
	}

	private static final class GraphicDoubleArrayTypeSupport implements TypeSupport, ValueReader<Double> {
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

			readAlarm(buffer, data);
			
			readPrecision(buffer, data);
			
			readUnits(buffer, data);

			readGraphicLimits(this, buffer, data);

			data.setValue((double[])DoubleArrayTypeSupport.INSTANCE.deserialize(buffer, data.getValue(), count));
			
			return data;
		}
	}

	private static final class ControlDoubleArrayTypeSupport implements TypeSupport {
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

			readAlarm(buffer, data);
			readPrecision(buffer, data);
			readUnits(buffer, data);
			readGraphicLimits(GraphicDoubleTypeSupport.INSTANCE, buffer, data);
			readControlLimits(GraphicDoubleTypeSupport.INSTANCE, buffer, data);

			data.setValue((double[])DoubleArrayTypeSupport.INSTANCE.deserialize(buffer, data.getValue(), count));
			
			return data;
		}
	}

	private static final class ShortTypeSupport implements TypeSupport {
		public static final ShortTypeSupport INSTANCE = new ShortTypeSupport();
		public static final Short DUMMY_INSTANCE = Short.valueOf((short)0);
		private ShortTypeSupport() {};
		@Override
		public Object newInstance() { return DUMMY_INSTANCE; }
		@Override
		public int getDataType() { return 1; }
		@Override
		public int getForcedElementCount() { return 1; }
		@Override
		public int serializeSize(Object object, int count) { return 2; };
		@Override
		public void serialize(ByteBuffer buffer, Object object, int count) { buffer.putShort(((Short)object)); }
		@Override
		public Object deserialize(ByteBuffer buffer, Object object, int count) { return buffer.getShort(); }
	}

	private static final class GraphicEnumTypeSupport implements TypeSupport {
		public static final GraphicEnumTypeSupport INSTANCE = new GraphicEnumTypeSupport();
		private GraphicEnumTypeSupport() {};
		@Override
		public Object newInstance() { return new GraphicEnum(); }
		@Override
		public int getDataType() { return 24; }
		@Override
		public int getForcedElementCount() { return 1; }
		
		@Override
		public Object deserialize(ByteBuffer buffer, Object object, int count)
		{ 
			GraphicEnum data = (object == null) ? 
					(GraphicEnum)newInstance() : (GraphicEnum)object;

			readAlarm(buffer, data);
			
			final int MAX_ENUM_STRING_SIZE = 26;
			final int MAX_ENUM_STATES = 16;

			int n = buffer.getShort() & 0xFFFF;
			
			byte[] rawBuffer = new byte[MAX_ENUM_STRING_SIZE];

			// read labels
			String[] labels = new String[n];
			for (int i = 0; i < n; i++)
			{
				buffer.get(rawBuffer);
				labels[i] = extractString(rawBuffer);
			}
			
			// read rest
			int restEntries = MAX_ENUM_STATES - n; 
			for (int i = 0; i < restEntries; i++)
				buffer.get(rawBuffer);
			
			data.setLabels(labels);

			data.setValue((Short)ShortTypeSupport.INSTANCE.deserialize(buffer, data.getValue(), count));
			
			return data;
		}
	}


	private static final class IntegerTypeSupport implements TypeSupport {
		public static final IntegerTypeSupport INSTANCE = new IntegerTypeSupport();
		public static final Integer DUMMY_INSTANCE = Integer.valueOf(0);
		private IntegerTypeSupport() {};
		@Override
		public Object newInstance() { return DUMMY_INSTANCE; }
		@Override
		public int getDataType() { return 5; }
		@Override
		public int getForcedElementCount() { return 1; }
		@Override
		public int serializeSize(Object object, int count) { return 4; };
		@Override
		public void serialize(ByteBuffer buffer, Object object, int count) { buffer.putInt((Integer)object); }
		@Override
		public Object deserialize(ByteBuffer buffer, Object object, int count) { return buffer.getInt(); }
	}

	private static final class STSIntegerTypeSupport implements TypeSupport {
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

			readAlarm(buffer, data);

			data.setValue(buffer.getInt());
			
			return data;
		}
	}

	private static final class StringTypeSupport implements TypeSupport {
		public static final StringTypeSupport INSTANCE = new StringTypeSupport();
		public static final String DUMMY_INSTANCE = "";
		private StringTypeSupport() {};
		@Override
		public Object newInstance() { return DUMMY_INSTANCE; }
		@Override
		public int getDataType() { return 0; }
		@Override
		public int getForcedElementCount() { return 1; }
		@Override
		public void serialize(ByteBuffer buffer, Object object, int count) { 
			buffer.put(((String)object).getBytes());
			buffer.put((byte)0);
		}
		@Override
		public Object deserialize(ByteBuffer buffer, Object object, int count) {
			
			int start = buffer.position();
			final int bufferEnd = buffer.limit();
			int end = start;
			
			// find zero char (string terminator)
			while (buffer.get(end) != 0 && end < bufferEnd)
				end++;

			// If the buffer is array backed, we can simply
			// use it directly. If not, we need to make a copy
			if (buffer.hasArray()) {
				// NOTE: rest of the bytes are left in the buffer
				return new String(buffer.array(), start, end-start);
			}
			else
			{
				int length = end-start;
				byte[] data = new byte[length];
				if (length < OPTIMIZED_COPY_THRESHOLD) {
					for (int i = 0; i < length; i++)
						data[i] = buffer.get();
				} else {
					buffer.get(data, 0, length);
				}
				return new String(data, 0, length);
			}
		}
	}

	static final Set<Class<?>> nativeTypeSet;
	static final Map<Class<?>, Map<Class<?>, TypeSupport>> typeSupportMap;

	static
	{
		Set<Class<?>> set = new HashSet<Class<?>>();
		set.add(String.class);
		set.add(Integer.class);
		set.add(Short.class);
		set.add(Float.class);
		set.add(Byte.class);
		set.add(Long.class);
		set.add(Double.class);
		
		set.add(String[].class);
		set.add(int[].class);
		set.add(short[].class);
		set.add(float[].class);
		set.add(byte[].class);
		set.add(long[].class);
		set.add(double[].class);

		// enum is short
		
		nativeTypeSet = Collections.unmodifiableSet(set);

		
		Map<Class<?>, Map<Class<?>, TypeSupport>> rootMap = new HashMap<>();
		
		//
		// native type support (metaType class == Void.class)
		//
		{
			Map<Class<?>, TypeSupport> map = new HashMap<>();
			map.put(Double.class, DoubleTypeSupport.INSTANCE);
			map.put(Integer.class, IntegerTypeSupport.INSTANCE);
			map.put(Short.class, ShortTypeSupport.INSTANCE);
			map.put(String.class, StringTypeSupport.INSTANCE);
			
			map.put(double[].class, DoubleArrayTypeSupport.INSTANCE);

			rootMap.put(Void.class, Collections.unmodifiableMap(map));
		}
		
		//
		// STS type support (metaType class == Alarm.class)
		//
		{
			Map<Class<?>, TypeSupport> map = new HashMap<>();
			map.put(Double.class, STSDoubleTypeSupport.INSTANCE);
			map.put(Integer.class, STSIntegerTypeSupport.INSTANCE);
	
			map.put(double[].class, STSDoubleArrayTypeSupport.INSTANCE);

			rootMap.put(Alarm.class, Collections.unmodifiableMap(map));
		}
		
		//
		// TIME type support (metaType class == Timestamped.class)
		//
		{
			Map<Class<?>, TypeSupport> map = new HashMap<>();
			map.put(Double.class, TimeDoubleTypeSupport.INSTANCE);
	
			map.put(double[].class, TimeDoubleArrayTypeSupport.INSTANCE);

			rootMap.put(Timestamped.class, Collections.unmodifiableMap(map));
		}
		
		//
		// GRAPHIC type support (metaType class == Graphic.class)
		//
		{
			Map<Class<?>, TypeSupport> map = new HashMap<>();
			map.put(Double.class, GraphicDoubleTypeSupport.INSTANCE);
	
			map.put(double[].class, GraphicDoubleArrayTypeSupport.INSTANCE);

			rootMap.put(Graphic.class, Collections.unmodifiableMap(map));
		}

		//
		// CONTROL type support (metaType class == Control.class)
		//
		{
			Map<Class<?>, TypeSupport> map = new HashMap<>();
			map.put(Double.class, ControlDoubleTypeSupport.INSTANCE);
	
			map.put(double[].class, ControlDoubleArrayTypeSupport.INSTANCE);

			rootMap.put(Control.class, Collections.unmodifiableMap(map));
		}

		typeSupportMap = Collections.unmodifiableMap(rootMap);
	}
	
	static final TypeSupport getTypeSupport(Class<?> clazz)
	{
		return getTypeSupport(Void.class, clazz);
	}
	
	static final TypeSupport getTypeSupport(Class<?> metaTypeClass, Class<?> typeClass)
	{
		// special case(s)
		if (metaTypeClass == GraphicEnum.class)
			return GraphicEnumTypeSupport.INSTANCE;
					
		Map<Class<?>, TypeSupport> m = typeSupportMap.get(metaTypeClass);
		return (m != null) ? m.get(typeClass) : null;
	}

	static final boolean isNativeType(Class<?> clazz)
	{
		return nativeTypeSet.contains(clazz);
	}

}
