package org.epics.ca;

/**
 * pvAccessJava module version (to be keept in sync with pom.xml).
 */
public final class Version {

    /**
     * Major version.
     */
    public static final int VERSION_MAJOR = 0;
    
    /**
     * Minor version.
     */
    public static final int VERSION_MINOR = 1;

    /**
     * Maintenance version.
     */
    public static final int VERSION_MAINTENANCE = 0;

    /**
     * Snapshot flag.
     */
    public static final boolean VERSION_SNAPSHOT = true;
    
    
    public static String getVersionString() {
    	StringBuffer sb = new StringBuffer(32);
    	sb.append(VERSION_MAJOR).append('.').append(VERSION_MINOR).
    		append('.').append(VERSION_MAINTENANCE);
		if (VERSION_SNAPSHOT)
        	sb.append("-SNAPSHOT");
		return sb.toString();
    }

}
