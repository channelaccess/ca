package org.epics.ca.impl.types;

import java.nio.ByteBuffer;

import org.epics.ca.data.GraphicEnum;

final class GraphicEnumTypeSupport implements TypeSupport {
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

		TypeSupports.readAlarm(buffer, data);
		
		final int MAX_ENUM_STRING_SIZE = 26;
		final int MAX_ENUM_STATES = 16;

		int n = buffer.getShort() & 0xFFFF;
		
		byte[] rawBuffer = new byte[MAX_ENUM_STRING_SIZE];

		// read labels
		String[] labels = new String[n];
		for (int i = 0; i < n; i++)
		{
			buffer.get(rawBuffer);
			labels[i] = TypeSupports.extractString(rawBuffer);
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