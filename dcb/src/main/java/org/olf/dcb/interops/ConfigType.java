package org.olf.dcb.interops;

public enum ConfigType {
	LOCATIONS("locations");

	private final String value;

	ConfigType(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public static ConfigType fromString(String value) {
		for (ConfigType type : ConfigType.values()) {
			if (type.value.equalsIgnoreCase(value)) {
				return type;
			}
		}
		return null;
	}
}
