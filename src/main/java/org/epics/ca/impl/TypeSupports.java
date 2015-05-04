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

import com.lmax.disruptor.EventFactory;

public class TypeSupports {

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

			int status = buffer.getShort() & 0xFFFF;
			int severity = buffer.getShort() & 0xFFFF;

			data.setAlarmStatus(AlarmStatus.values()[status]);
			data.setAlarmSeverity(AlarmSeverity.values()[severity]);

			// RISC padding
			buffer.getInt();

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

			int status = buffer.getShort() & 0xFFFF;
			int severity = buffer.getShort() & 0xFFFF;

			data.setAlarmStatus(AlarmStatus.values()[status]);
			data.setAlarmSeverity(AlarmSeverity.values()[severity]);

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
		// native type support (metaType class == Alarm.class)
		//
		{
			Map<Class<?>, TypeSupport> map = new HashMap<>();
			map.put(Double.class, STSDoubleTypeSupport.INSTANCE);
			map.put(Integer.class, STSIntegerTypeSupport.INSTANCE);
	
			rootMap.put(Alarm.class, Collections.unmodifiableMap(map));
		}
		
		
		typeSupportMap = Collections.unmodifiableMap(rootMap);
	}
	
	static final TypeSupport getTypeSupport(Class<?> clazz)
	{
		return getTypeSupport(Void.class, clazz);
	}
	
	static final TypeSupport getTypeSupport(Class<?> metaTypeClass, Class<?> typeClass)
	{
		return typeSupportMap.get(metaTypeClass).get(typeClass);
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
