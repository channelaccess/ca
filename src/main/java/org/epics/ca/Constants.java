package org.epics.ca;

public interface Constants {

	public static final String ADDR_LIST_KEY = "EPICS_CA_ADDR_LIST";
	
	  /** Minimal priority. */
	  static final public short CHANNEL_PRIORITY_MIN = 0;
	  /** Maximal priority. */
	  static final public short CHANNEL_PRIORITY_MAX = 99;
	  /** Default priority. */
	  static final public short CHANNEL_PRIORITY_DEFAULT = CHANNEL_PRIORITY_MIN;
	  /** DB links priority. */
	  static final public short CHANNEL_PRIORITY_LINKS_DB = CHANNEL_PRIORITY_MAX;
	  /** Archive priority. */
	  static final public short CHANNEL_PRIORITY_ARCHIVE = (CHANNEL_PRIORITY_MAX + CHANNEL_PRIORITY_MIN) / 2;
	  /** OPI priority. */
	  static final public short CHANNEL_PRIORITY_OPI = CHANNEL_PRIORITY_MIN;
	
}
