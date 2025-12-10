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
	OWN_LIBRARY_BORROWING, // If enabled, items from the patron's own library will be included in resolution
  PICKUP_ANYWHERE, // If enabled, pickup anywhere requests are allowed and an item can be picked up at any eligible location
	RE_RESOLUTION, // If enabled, DCB will attempt to re-resolve to a new supplier if the first supplier is unable to fulfil the request
	SELECT_UNAVAILABLE_ITEMS, // If enabled, DCB will include unavailable items in resolution
  TRIGGER_SUPPLIER_RENEWAL, // If enabled, DCB will attempt to mirror a renewal at the supplying library
	DENY_LIBRARY_MAPPING_EDIT, // If enabled, stops library users from being able to edit mappings
	VIRTUAL_PATRON_NAMES_VISIBLE, // If enabled, virtual patrons will be created with names from the 'real patron'.
	VIRTUAL_PATRON_NAMES_POLARIS; // A special case for Polaris virtual patron functionality. To be deprecated when we have group functional settings.

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
