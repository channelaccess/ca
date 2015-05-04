package org.epics.ca.data;

public class Control<T> extends Graphic<T> {
	
	protected T upperControl;
	protected T lowerControl;
	
	public T getUpperControl() {
		return upperControl;
	}
	public void setUpperControl(T upperControl) {
		this.upperControl = upperControl;
	}
	public T getLowerControl() {
		return lowerControl;
	}
	public void setLowerControl(T lowerControl) {
		this.lowerControl = lowerControl;
	}
	
}