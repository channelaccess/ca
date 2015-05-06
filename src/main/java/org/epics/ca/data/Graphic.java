package org.epics.ca.data;

// T is a value class, ST is a scalar of T (or same as T,  if T is already a scalar)
// e.g. Graphic<Double, Double>
// e.g. Graphic<double[], Double>
public class Graphic<T, ST> extends Alarm<T> {
	
	protected String units = "";
	protected int precision = 0;		// for floating-point values only (float, double)
	
	protected ST upperDisplay;
	protected ST lowerDisplay;
	protected ST upperAlarm;
	protected ST upperWarning;
	protected ST lowerWarning;
	protected ST lowerAlarm;
	
	public String getUnits() { return units; }
	public void setUnits(String units) { this.units = units; }
	
	public int getPrecision() { return precision; }
	public void setPrecision(int precision) { this.precision = precision; }
	
	
	public ST getUpperDisplay() {
		return upperDisplay;
	}
	public void setUpperDisplay(ST upperDisplay) {
		this.upperDisplay = upperDisplay;
	}
	public ST getLowerDisplay() {
		return lowerDisplay;
	}
	public void setLowerDisplay(ST lowerDisplay) {
		this.lowerDisplay = lowerDisplay;
	}
	public ST getUpperAlarm() {
		return upperAlarm;
	}
	public void setUpperAlarm(ST upperAlarm) {
		this.upperAlarm = upperAlarm;
	}
	public ST getUpperWarning() {
		return upperWarning;
	}
	public void setUpperWarning(ST upperWarning) {
		this.upperWarning = upperWarning;
	}
	public ST getLowerWarning() {
		return lowerWarning;
	}
	public void setLowerWarning(ST lowerWarning) {
		this.lowerWarning = lowerWarning;
	}
	public ST getLowerAlarm() {
		return lowerAlarm;
	}
	public void setLowerAlarm(ST lowerAlarm) {
		this.lowerAlarm = lowerAlarm;
	}
	
}