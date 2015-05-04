package org.epics.ca.data;

public class Graphic<T> extends Alarm<T> {
	
	protected String units = "";
	protected int precision = 0;		// for floating-point values only (float, double)
	
	protected T upperDisplay;
	protected T lowerDisplay;
	protected T upperAlarm;
	protected T upperWarning;
	protected T lowerWarning;
	protected T lowerAlarm;
	
	public String getUnits() { return units; }
	public void setUnits(String units) { this.units = units; }
	
	public int getPrecision() { return precision; }
	public void setPrecision(int precision) { this.precision = precision; }
	
	
	public T getUpperDisplay() {
		return upperDisplay;
	}
	public void setUpperDisplay(T upperDisplay) {
		this.upperDisplay = upperDisplay;
	}
	public T getLowerDisplay() {
		return lowerDisplay;
	}
	public void setLowerDisplay(T lowerDisplay) {
		this.lowerDisplay = lowerDisplay;
	}
	public T getUpperAlarm() {
		return upperAlarm;
	}
	public void setUpperAlarm(T upperAlarm) {
		this.upperAlarm = upperAlarm;
	}
	public T getUpperWarning() {
		return upperWarning;
	}
	public void setUpperWarning(T upperWarning) {
		this.upperWarning = upperWarning;
	}
	public T getLowerWarning() {
		return lowerWarning;
	}
	public void setLowerWarning(T lowerWarning) {
		this.lowerWarning = lowerWarning;
	}
	public T getLowerAlarm() {
		return lowerAlarm;
	}
	public void setLowerAlarm(T lowerAlarm) {
		this.lowerAlarm = lowerAlarm;
	}
	
}