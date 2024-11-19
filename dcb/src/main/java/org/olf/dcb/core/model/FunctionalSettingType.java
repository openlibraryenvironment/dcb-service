package org.olf.dcb.core.model;
/** <p> An enum representing the current valid functional settings in DCB
 * </p><br>
 * <p> These are the valid functional setting names.
 * 	If you are adding a new valid functional setting, please also add it to the enum in schema.graphqls.
 * 	This will ensure that you do not break DCB Admin functionality.
 * </p><br>
 * */

public enum FunctionalSettingType {
	// These are the valid functional setting names.
	// If you are adding a new valid functional setting, please also add it to the enum in schema.graphqls.
	// This will ensure that you do not break DCB Admin functionality.
	PICKUP_ANYWHERE,
	RE_RESOLUTION,
	TEST_POLICY_3;

	public static boolean isValid(String name) {
		for (FunctionalSettingType type : values()) {
			if (String.valueOf(type).equals(name)) {
				return true;
			}
		}
		return false;
	}

	public static String getValidNames() {
		StringBuilder validNames = new StringBuilder();
		for (FunctionalSettingType type : values()) {
			if (!validNames.isEmpty()) {
				validNames.append(", ");
			}
			validNames.append(type);
		}
		return validNames.toString();
	}
}
