package org.olf.dcb.core.interaction.koha;

import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.model.HostLms;

import java.net.URI;
import java.util.List;

import static java.lang.Boolean.TRUE;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.stringPropertyDefinition;
import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.urlPropertyDefinition;

/** Config values for Koha LMS **/

public class KohaClientConfig {

	private static final HostLmsPropertyDefinition API_URL = urlPropertyDefinition("api-url", "Base API URL of the Koha system", TRUE);
	private static final HostLmsPropertyDefinition CLIENT_ID = stringPropertyDefinition("client_id", "Client ID for OAuth for the Koha system", TRUE);
	private static final HostLmsPropertyDefinition CLIENT_SECRET = stringPropertyDefinition("client_secret", "Client Secret for OAuth for the Koha system", TRUE);
	private static final HostLmsPropertyDefinition DCB_SHARING_LIBRARY_CODE
		= stringPropertyDefinition("sharing-library-code", "Library used to ship resources outside of Koha", TRUE);
	// The branch a virtual item is created at, used only when the borrowing patron's own
	// branch is unknown - see KohaHostLmsClient.virtualItemLibraryFor.
	//
	// There is deliberately no virtual-item-location-code here. Koha's createItem sets
	// home_library_id and holding_library_id and nothing else; a shelving location was
	// never sent, so the key was required configuration that no code path read.
	private static final HostLmsPropertyDefinition VIRTUAL_ITEM_LIBRARY_CODE = stringPropertyDefinition("virtual-item-library-code", "The library at which virtual items will be created", TRUE);



	private final HostLms hostLms;

	public KohaClientConfig(HostLms hostLms) {
		this.hostLms = hostLms;
	}

	URI getApiUrl() {
		return URI.create(API_URL.getRequiredConfigValue(hostLms));
	}
	String getClientId() {
		return CLIENT_ID.getRequiredConfigValue(hostLms);
	}
	String getClientSecret() {
		return CLIENT_SECRET.getRequiredConfigValue(hostLms);
	}
	List<HostLmsPropertyDefinition> getSettings() {
		return List.of(
			API_URL,
			CLIENT_ID,
			CLIENT_SECRET
		);
	}

	public String getDcbSharingLibraryCode() {
		return DCB_SHARING_LIBRARY_CODE.getRequiredConfigValue(hostLms);
	}

	public String getVirtualItemLibraryCode() {
		return VIRTUAL_ITEM_LIBRARY_CODE.getRequiredConfigValue(hostLms);
	}
}
