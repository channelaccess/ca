package org.epics.ca;

/**
 * pvAccessJava module version, retrieves version from MANIFEST.
 */
public final class Version {

	public static String getVersionString() {
		String version = null;

		Package aPackage = Version.class.getPackage();
		if (aPackage != null) {
			version = aPackage.getImplementationVersion();
			if (version == null) {
				version = aPackage.getSpecificationVersion();
			}
		}

		// we could not compute the version so use a blank
		if (version == null)
			version = "<unknown>";

		return version;
	}

}
