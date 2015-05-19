package org.epics.ca.data;

public class GraphicEnumArray extends Alarm<short[]> {
	
	protected String[] labels;

	public String[] getLabels() {
		return labels;
	}

	public void setLabels(String[] labels) {
		this.labels = labels;
	}
	
}
