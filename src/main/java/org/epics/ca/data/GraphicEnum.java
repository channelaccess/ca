package org.epics.ca.data;

public class GraphicEnum extends Alarm<Short> {
	
	protected String[] labels;

	public String[] getLabels() {
		return labels;
	}

	public void setLabels(String[] labels) {
		this.labels = labels;
	}
	
}
