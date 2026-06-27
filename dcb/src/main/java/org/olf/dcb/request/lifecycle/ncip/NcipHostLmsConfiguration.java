package org.olf.dcb.request.lifecycle.ncip;

import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.urlPropertyDefinition;

import java.net.URI;
import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.model.HostLms;

final class NcipHostLmsConfiguration {
	static final String ENDPOINT_URL_KEY = "ncip-endpoint-url";
	static final HostLmsPropertyDefinition ENDPOINT_URL
		= urlPropertyDefinition(
			ENDPOINT_URL_KEY,
			"NCIP v2.02 endpoint URL",
			true);

	URI endpointUriFor(HostLms hostLms) {
		return URI.create(ENDPOINT_URL.getRequiredConfigValue(hostLms));
	}
}
