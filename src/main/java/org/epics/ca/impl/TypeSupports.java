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
import org.epics.ca.data.Timestamped;

import com.lmax.disruptor.EventFactory;

public class TypeSupports {

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
	
	private final static void readUnits(ByteBuffer buffer, Graphic<?> data)
	{
		final int MAX_UNITS_SIZE = 8;
		byte[] rawUnits = new byte[MAX_UNITS_SIZE];
		buffer.get(rawUnits);

		data.setUnits(extractString(rawUnits));			
	}
	
	private final static void readPrecision(ByteBuffer buffer, Graphic<?> data)
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
	
	private final static <T> void readGraphicLimits(ValueReader<T> valueReader, ByteBuffer buffer, Graphic<T> data)
	{
		data.setUpperDisplay(valueReader.readValue(buffer, data.getUpperDisplay()));
		data.setLowerDisplay(valueReader.readValue(buffer, data.getLowerDisplay()));
		data.setUpperAlarm  (valueReader.readValue(buffer, data.getUpperAlarm()));
		data.setUpperWarning(valueReader.readValue(buffer, data.getUpperWarning()));
		data.setLowerWarning(valueReader.readValue(buffer, data.getLowerWarning()));
		data.setLowerAlarm  (valueReader.readValue(buffer, data.getLowerAlarm()));
	}
	
	private final static <T> void readControlLimits(ValueReader<T> valueReader, ByteBuffer buffer, Control<T> data)
	{
		data.setUpperControl(valueReader.readValue(buffer, data.getUpperControl()));
		data.setLowerControl(valueReader.readValue(buffer, data.getLowerControl()));
	}	
	

	// TODO move type support to separate package
	
	public static interface TypeSupport {
		public Object newInstance();
		public int getDataType();
		public int getElementCount();
		public void serialize(ByteBuffer buffer, Object object); 
		public Object deserialize(ByteBuffer buffer, Object object);
	}
	
	private static class DoubleTypeSupport implements TypeSupport {
		public static final DoubleTypeSupport INSTANCE = new DoubleTypeSupport();
		private DoubleTypeSupport() {};
		public Object newInstance() { return Double.valueOf(0); }
		public int getDataType() { return 6; }
		public int getElementCount() { return 1; }
		public void serialize(ByteBuffer buffer, Object object) { buffer.putDouble((Double)object); }
		public Object deserialize(ByteBuffer buffer, Object object) { return buffer.getDouble(); }
	}

	private static class STSDoubleTypeSupport implements TypeSupport {
		public static final STSDoubleTypeSupport INSTANCE = new STSDoubleTypeSupport();
		private STSDoubleTypeSupport() {};
		public Object newInstance() { return new Alarm<Double>(); }
		public int getDataType() { return 13; }
		public int getElementCount() { return 1; }
		public void serialize(ByteBuffer buffer, Object object) { /* TODO */ }
		public Object deserialize(ByteBuffer buffer, Object object)
		{ 
			@SuppressWarnings("unchecked")
			Alarm<Double> data = (object == null) ? 
					(Alarm<Double>)newInstance() : (Alarm<Double>)object;

			readAlarm(buffer, data);

			// RISC padding
			buffer.getInt();

			data.setValue(buffer.getDouble());
			
			return data;
		}
	}

	private static class TimeDoubleTypeSupport implements TypeSupport {
		public static final TimeDoubleTypeSupport INSTANCE = new TimeDoubleTypeSupport();
		private TimeDoubleTypeSupport() {};
		public Object newInstance() { return new Timestamped<Double>(); }
		public int getDataType() { return 20; }
		public int getElementCount() { return 1; }
		public void serialize(ByteBuffer buffer, Object object) { /* TODO */ }
		public Object deserialize(ByteBuffer buffer, Object object)
		{ 
			@SuppressWarnings("unchecked")
			Timestamped<Double> data = (object == null) ? 
					(Timestamped<Double>)newInstance() : (Timestamped<Double>)object;

			readAlarm(buffer, data);

			readTimestamp(buffer, data);
			
			// RISC padding
			buffer.getInt();

			data.setValue(buffer.getDouble());
			
			return data;
		}
	}

	private static class GraphicDoubleTypeSupport implements TypeSupport, ValueReader<Double> {
		public static final GraphicDoubleTypeSupport INSTANCE = new GraphicDoubleTypeSupport();
		private GraphicDoubleTypeSupport() {};
		public Object newInstance() { return new Graphic<Double>(); }
		public int getDataType() { return 27; }
		public int getElementCount() { return 1; }
		public void serialize(ByteBuffer buffer, Object object) { /* TODO */ }
		
		@Override
		public Double readValue(ByteBuffer buffer, Double value) {
			return buffer.getDouble();
		}
		
		public Object deserialize(ByteBuffer buffer, Object object)
		{ 
			@SuppressWarnings("unchecked")
			Graphic<Double> data = (object == null) ? 
					(Graphic<Double>)newInstance() : (Graphic<Double>)object;

			readAlarm(buffer, data);
			
			readPrecision(buffer, data);
			
			readUnits(buffer, data);

			readGraphicLimits(this, buffer, data);

			data.setValue(buffer.getDouble());
			
			return data;
		}
	}

	private static class ControlDoubleTypeSupport implements TypeSupport, ValueReader<Double> {
		public static final ControlDoubleTypeSupport INSTANCE = new ControlDoubleTypeSupport();
		private ControlDoubleTypeSupport() {};
		public Object newInstance() { return new Control<Double>(); }
		public int getDataType() { return 34; }
		public int getElementCount() { return 1; }
		public void serialize(ByteBuffer buffer, Object object) { /* TODO */ }
		
		@Override
		public Double readValue(ByteBuffer buffer, Double value) {
			return buffer.getDouble();
		}
		
		public Object deserialize(ByteBuffer buffer, Object object)
		{ 
			@SuppressWarnings("unchecked")
			Control<Double> data = (object == null) ? 
					(Control<Double>)newInstance() : (Control<Double>)object;

			readAlarm(buffer, data);
			
			readPrecision(buffer, data);
			
			readUnits(buffer, data);

			readGraphicLimits(this, buffer, data);
			
			readControlLimits(this, buffer, data);

			data.setValue(buffer.getDouble());
			
			return data;
		}
	}

	private static class STSIntegerTypeSupport implements TypeSupport {
		public static final STSIntegerTypeSupport INSTANCE = new STSIntegerTypeSupport();
		private STSIntegerTypeSupport() {};
		public Object newInstance() { return new Alarm<Integer>(); }
		public int getDataType() { return 8; }
		public int getElementCount() { return 1; }
		public void serialize(ByteBuffer buffer, Object object) { /* TODO */ }
		public Object deserialize(ByteBuffer buffer, Object object)
		{ 
			@SuppressWarnings("unchecked")
			Alarm<Integer> data = (object == null) ? 
					(Alarm<Integer>)newInstance() : (Alarm<Integer>)object;

			readAlarm(buffer, data);

			data.setValue((int)buffer.getShort());
			
			return data;
		}
	}

	private static class IntegerTypeSupport implements TypeSupport {
		public static final IntegerTypeSupport INSTANCE = new IntegerTypeSupport();
		private IntegerTypeSupport() {};
		public Object newInstance() { return Integer.valueOf(0); }
		public int getDataType() { return 1; }
		public int getElementCount() { return 1; }
		public void serialize(ByteBuffer buffer, Object object) { buffer.putShort(((Integer)object).shortValue()); }
		public Object deserialize(ByteBuffer buffer, Object object) { return (int)buffer.getShort(); }
	}

	private static class StringTypeSupport implements TypeSupport {
		public static final StringTypeSupport INSTANCE = new StringTypeSupport();
		private StringTypeSupport() {};
		public Object newInstance() { return ""; }
		public int getDataType() { return 0; }
		public int getElementCount() { return 1; }
		public void serialize(ByteBuffer buffer, Object object) { /* TODO */ }
		public Object deserialize(ByteBuffer buffer, Object object) { return object; }
	}

	static final Set<Class<?>> nativeTypeSet;
	static final Map<Class<?>, Map<Class<?>, TypeSupport>> typeSupportMap;

	static
	{
		Set<Class<?>> set = new HashSet<Class<?>>();
		set.add(String.class);
		set.add(Integer.class);	// TODO INT == SHORT
		set.add(Short.class);
		set.add(Float.class);
		set.add(Enum.class);	// TODO
		set.add(Byte.class);
		set.add(Long.class);
		set.add(Double.class);
		
		set.add(String[].class);
		set.add(int[].class);
		set.add(short[].class);
		set.add(float[].class);
		set.add(Enum[].class);	// TODO
		set.add(byte[].class);
		set.add(long[].class);
		set.add(double[].class);

		nativeTypeSet = Collections.unmodifiableSet(set);

		
		Map<Class<?>, Map<Class<?>, TypeSupport>> rootMap = new HashMap<>();
		
		//
		// native type support (metaType class == Void.class)
		//
		{
			Map<Class<?>, TypeSupport> map = new HashMap<>();
			map.put(Double.class, DoubleTypeSupport.INSTANCE);
			map.put(Integer.class, IntegerTypeSupport.INSTANCE);
			map.put(String.class, StringTypeSupport.INSTANCE);
			
			rootMap.put(Void.class, Collections.unmodifiableMap(map));
		}
		
		//
		// STS type support (metaType class == Alarm.class)
		//
		{
			Map<Class<?>, TypeSupport> map = new HashMap<>();
			map.put(Double.class, STSDoubleTypeSupport.INSTANCE);
			map.put(Integer.class, STSIntegerTypeSupport.INSTANCE);
	
			rootMap.put(Alarm.class, Collections.unmodifiableMap(map));
		}
		
		//
		// TIME type support (metaType class == Timestamped.class)
		//
		{
			Map<Class<?>, TypeSupport> map = new HashMap<>();
			map.put(Double.class, TimeDoubleTypeSupport.INSTANCE);
	
			rootMap.put(Timestamped.class, Collections.unmodifiableMap(map));
		}
		
		//
		// GRAPHIC type support (metaType class == Graphic.class)
		//
		{
			Map<Class<?>, TypeSupport> map = new HashMap<>();
			map.put(Double.class, GraphicDoubleTypeSupport.INSTANCE);
	
			rootMap.put(Graphic.class, Collections.unmodifiableMap(map));
		}

		//
		// CONTROL type support (metaType class == Control.class)
		//
		{
			Map<Class<?>, TypeSupport> map = new HashMap<>();
			map.put(Double.class, ControlDoubleTypeSupport.INSTANCE);
	
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
		Map<Class<?>, TypeSupport> m = typeSupportMap.get(metaTypeClass);
		return (m != null) ? m.get(typeClass) : null;
	}

	static final boolean isNativeType(Class<?> clazz)
	{
		return nativeTypeSet.contains(clazz);
	}

	// TODO move to TypeSupport
	static final <T> EventFactory<T> getEventFactory(Class<T> clazz)
	{
		// TODO cache factories using Map<Class<T>, EventFactory>
		return new EventFactory<T>() {
			@Override
			public T newInstance() {
				try {
					return clazz.newInstance();
				} catch (Throwable th) {
					throw new RuntimeException("failed to instantiate new instance of " + clazz, th);
				}
			}
		};
	}

}
