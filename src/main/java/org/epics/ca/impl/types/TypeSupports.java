package org.epics.ca.impl.types;

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

public class TypeSupports {

	/**
	 * Threshold at which the method to copy the buffer is changed. If lower,
	 * the elements are copied one by one. If higher, the data is copied with
	 * ByteBuffer bulk operations.
	 * <p>
	 * As of JDK 1.7 the optimization at lower count is desirable because there
	 * is a fair amount of logic implemented in ByteBuffer bulk operations to
	 * determine which methods are available and which one is more efficient.
	 */
	static final int OPTIMIZED_COPY_THRESHOLD = 128;

	static final Set<Class<?>> nativeTypeSet;
	static final Map<Class<?>, Map<Class<?>, TypeSupport>> typeSupportMap;

	static {
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

		// native type support (metaType class == Void.class)
		Map<Class<?>, TypeSupport> map = new HashMap<>();
		map.put(Double.class, DoubleTypeSupport.INSTANCE);
		map.put(Integer.class, IntegerTypeSupport.INSTANCE);
		map.put(Short.class, ShortTypeSupport.INSTANCE);
		map.put(String.class, StringTypeSupport.INSTANCE);

		map.put(double[].class, DoubleArrayTypeSupport.INSTANCE);
		
		rootMap.put(Void.class, Collections.unmodifiableMap(map));

		
		// STS type support (metaType class == Alarm.class)
		map = new HashMap<>();
		map.put(Double.class, STSDoubleTypeSupport.INSTANCE);
		map.put(Integer.class, STSIntegerTypeSupport.INSTANCE);

		map.put(double[].class, STSDoubleArrayTypeSupport.INSTANCE);
		
		rootMap.put(Alarm.class, Collections.unmodifiableMap(map));

		
		// TIME type support (metaType class == Timestamped.class)
		map = new HashMap<>();
		map.put(Double.class, TimeDoubleTypeSupport.INSTANCE);

		map.put(double[].class, TimeDoubleArrayTypeSupport.INSTANCE);
		
		rootMap.put(Timestamped.class, Collections.unmodifiableMap(map));

		
		// GRAPHIC type support (metaType class == Graphic.class)
		map = new HashMap<>();
		map.put(Double.class, GraphicDoubleTypeSupport.INSTANCE);

		map.put(double[].class, GraphicDoubleArrayTypeSupport.INSTANCE);

		rootMap.put(Graphic.class, Collections.unmodifiableMap(map));

		
		// CONTROL type support (metaType class == Control.class)
		map = new HashMap<>();
		map.put(Double.class, ControlDoubleTypeSupport.INSTANCE);

		map.put(double[].class, ControlDoubleArrayTypeSupport.INSTANCE);

		rootMap.put(Control.class, Collections.unmodifiableMap(map));

		typeSupportMap = Collections.unmodifiableMap(rootMap);
	}
	
	
	/**
	 * Create (extract) string (zero-terminated) from byte buffer.
	 * 
	 * @param rawBuffer
	 * @return decoded DBR.
	 */
	final static String extractString(byte[] rawBuffer) {
		int len = 0;
		final int rawBufferLen = rawBuffer.length;
		while (len < rawBufferLen && rawBuffer[len] != 0)
			len++;
		return new String(rawBuffer, 0, len);
	}

	final static void readAlarm(ByteBuffer buffer, Alarm<?> data) {
		int status = buffer.getShort() & 0xFFFF;
		int severity = buffer.getShort() & 0xFFFF;

		data.setAlarmStatus(AlarmStatus.values()[status]);
		data.setAlarmSeverity(AlarmSeverity.values()[severity]);
	}

	final static void readTimestamp(ByteBuffer buffer, Timestamped<?> data) {
		// seconds since 0000 Jan 1, 1990
		long secPastEpoch = buffer.getInt() & 0x00000000FFFFFFFFL;
		// nanoseconds within second
		int nsec = buffer.getInt();

		data.setSeconds(secPastEpoch + Timestamped.EPOCH_SECONDS_PAST_1970);
		data.setNanos(nsec);
	}

	final static void readUnits(ByteBuffer buffer, Graphic<?, ?> data) {
		final int MAX_UNITS_SIZE = 8;
		byte[] rawUnits = new byte[MAX_UNITS_SIZE];
		buffer.get(rawUnits);

		data.setUnits(extractString(rawUnits));
	}

	final static void readPrecision(ByteBuffer buffer, Graphic<?, ?> data) {
		int precision = buffer.getShort() & 0xFFFF;
		data.setPrecision(precision);

		// RISC padding
		buffer.getShort();
	}

	final static <T> void readGraphicLimits(ValueReader<T> valueReader, ByteBuffer buffer, Graphic<?, T> data) {
		data.setUpperDisplay(valueReader.readValue(buffer, data.getUpperDisplay()));
		data.setLowerDisplay(valueReader.readValue(buffer, data.getLowerDisplay()));
		data.setUpperAlarm(valueReader.readValue(buffer, data.getUpperAlarm()));
		data.setUpperWarning(valueReader.readValue(buffer, data.getUpperWarning()));
		data.setLowerWarning(valueReader.readValue(buffer, data.getLowerWarning()));
		data.setLowerAlarm(valueReader.readValue(buffer, data.getLowerAlarm()));
	}

	final static <T> void readControlLimits(ValueReader<T> valueReader, ByteBuffer buffer, Control<?, T> data) {
		data.setUpperControl(valueReader.readValue(buffer, data.getUpperControl()));
		data.setLowerControl(valueReader.readValue(buffer, data.getLowerControl()));
	}

	public static final TypeSupport getTypeSupport(Class<?> clazz) {
		return getTypeSupport(Void.class, clazz);
	}

	public static final TypeSupport getTypeSupport(Class<?> metaTypeClass, Class<?> typeClass) {
		// special case(s)
		if (metaTypeClass == GraphicEnum.class){
			return GraphicEnumTypeSupport.INSTANCE;
		}

		Map<Class<?>, TypeSupport> m = typeSupportMap.get(metaTypeClass);
		return (m != null) ? m.get(typeClass) : null;
	}

	public static final boolean isNativeType(Class<?> clazz) {
		return nativeTypeSet.contains(clazz);
	}

}
