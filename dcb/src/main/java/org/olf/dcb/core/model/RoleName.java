package org.olf.dcb.core.model;

public enum RoleName {
	SPONSOR, LIBRARY_SERVICES_ADMINISTRATOR, SIGN_OFF_AUTHORITY, SUPPORT, OPERATIONS_CONTACT, IMPLEMENTATION_CONTACT,
	TECHNICAL_CONTACT;
	public static boolean isValid(String name) {
		for (RoleName role : values()) {
			if (String.valueOf(role).equals(name)) {
				return true;
			}
		}
		return false;
	}
	public static String getValidNames() {
		StringBuilder validNames = new StringBuilder();
		for (RoleName roleName : values()) {
			if (!validNames.isEmpty()) {
				validNames.append(", ");
			}
			validNames.append(roleName);
		}
		return validNames.toString();
	}

}
