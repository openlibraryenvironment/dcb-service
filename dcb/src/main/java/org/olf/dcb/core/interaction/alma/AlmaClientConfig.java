package org.olf.dcb.core.interaction.alma;

import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.model.HostLms;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.stringPropertyDefinition;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.urlPropertyDefinition;

public class AlmaClientConfig {

	// These are the same config keys as from FolioOaiPmhIngestSource
	// which was implemented prior to this client
	private static final HostLmsPropertyDefinition BASE_URL_SETTING
		= urlPropertyDefinition("alma-url", "Base request URL of the ALMA system", TRUE);
	private static final HostLmsPropertyDefinition API_KEY_SETTING
		= stringPropertyDefinition("apikey", "API key for this ALMA system", TRUE);

	//	The item's override policy for loan rules.
//	Defines the conditions under which a request for this item can be fulfilled.
//	Possible codes are listed in 'ItemPolicy' code table.
	// https://developers.exlibrisgroup.com/alma/apis/docs/xsd/rest_item.xsd/?tags=POST
	private static final HostLmsPropertyDefinition ITEM_POLICY_SETTING
		= stringPropertyDefinition("item-policy", "Item policy for this ALMA system", FALSE);
	private static final HostLmsPropertyDefinition SHELF_LOCATION_SETTING
		= stringPropertyDefinition("shelf-location", "Shelf location for this ALMA system", FALSE);
	private static final HostLmsPropertyDefinition PICKUP_CIRC_DESK_SETTING
		= stringPropertyDefinition("pickup-circ-desk", "Pickup circ desk for this ALMA system", FALSE);
	private static final HostLmsPropertyDefinition USER_IDENTIFIER
		= stringPropertyDefinition("user-identifier", "User identifier to find patron", FALSE);

	// if the user doesn't have a homelibrary, this replaces it
	// We can then use this to find the first OPEN location for their library
	private static final HostLmsPropertyDefinition DEFAULT_PATRON_LOCATION_CODE
		= stringPropertyDefinition("default-patron-location-code", "Default patron location code for this ALMA system", FALSE);

	// Alma needs to define libraries that are outside the system. The way we do this is by having a DCB library/location
	// that acts as all locations outside the system. This needs to be set up in Alma first but is the only way we can
	// indicate that a check in goes in transit
	// if we don't use a different library a check in will transition the item to hold shelf
	private static final HostLmsPropertyDefinition DCB_SHARING_LIBRARY_CODE
		= stringPropertyDefinition("default-patron-location-code", "Default patron location code for this ALMA system", TRUE);

	private final HostLms hostLms;

	public AlmaClientConfig(HostLms hostLms) {
		this.hostLms = hostLms;
	}

	URI getBaseUrl() {
		return URI.create(BASE_URL_SETTING.getRequiredConfigValue(hostLms));
	}

	String getApiKey() {
		return API_KEY_SETTING.getRequiredConfigValue(hostLms);
	}

	String getItemPolicy(String defaultValue) {
		return ITEM_POLICY_SETTING.getOptionalValueFrom(hostLms.getClientConfig(), defaultValue);
	}

	String getShelfLocation() {
		return SHELF_LOCATION_SETTING.getOptionalValueFrom(hostLms.getClientConfig(), null);
	}

	String getPickupCircDesk(String defaultValue) {
		return PICKUP_CIRC_DESK_SETTING.getOptionalValueFrom(hostLms.getClientConfig(), defaultValue);
	}

	String getUserIdentifier(String defaultValue) {
		return USER_IDENTIFIER.getOptionalValueFrom(hostLms.getClientConfig(), defaultValue);
	}

	String getDcbSharingLibraryCode() {
		return DCB_SHARING_LIBRARY_CODE.getRequiredConfigValue(hostLms);
	}

	// We should move away from using this
	String getDefaultPatronLocationCode(String defaultValue) {
		return DEFAULT_PATRON_LOCATION_CODE.getOptionalValueFrom(hostLms.getClientConfig(), defaultValue);
	}

	List<HostLmsPropertyDefinition> getSettings() {
		return List.of(
			BASE_URL_SETTING,
			API_KEY_SETTING,
			ITEM_POLICY_SETTING,
			SHELF_LOCATION_SETTING
		);
	}


}
