package org.olf.dcb.request.lifecycle.ncip;

import static org.olf.dcb.core.interaction.HostLmsPropertyDefinition.urlPropertyDefinition;
import static services.k_int.utils.MapUtils.getAsOptionalString;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.olf.dcb.core.interaction.HostLmsPropertyDefinition;
import org.olf.dcb.core.model.HostLms;

final class NcipHostLmsConfiguration {
	static final String ENDPOINT_URL_KEY = "ncip-endpoint-url";
	static final String NCIP_AGENCY_ID_KEY = "ncipAgencyId";
	static final String NCIP_AGENCY_ID_KEBAB_KEY = "ncip-agency-id";
	static final String NCIP_AGENCY_ID_NORMALIZED_KEY = "ncipagencyid";
	static final String NCIP_SYSTEM_ID_KEY = "ncipSystemId";
	static final String NCIP_SYSTEM_ID_KEBAB_KEY = "ncip-system-id";
	static final String NCIP_SYSTEM_ID_NORMALIZED_KEY = "ncipsystemid";
	private static final List<String> NCIP_AGENCY_ID_KEYS = List.of(
		NCIP_AGENCY_ID_KEY,
		NCIP_AGENCY_ID_KEBAB_KEY,
		NCIP_AGENCY_ID_NORMALIZED_KEY);
	private static final List<String> NCIP_SYSTEM_ID_KEYS = List.of(
		NCIP_SYSTEM_ID_KEY,
		NCIP_SYSTEM_ID_KEBAB_KEY,
		NCIP_SYSTEM_ID_NORMALIZED_KEY);
	static final HostLmsPropertyDefinition ENDPOINT_URL
		= urlPropertyDefinition(
			ENDPOINT_URL_KEY,
			"NCIP v2.02 endpoint URL",
			true);
	static final HostLmsPropertyDefinition NCIP_SYSTEM_ID
		= HostLmsPropertyDefinition.stringPropertyDefinition(
			NCIP_SYSTEM_ID_KEY,
			"NCIP SystemId for this HostLMS",
			true);
	static final HostLmsPropertyDefinition NCIP_AGENCY_ID
		= HostLmsPropertyDefinition.stringPropertyDefinition(
			NCIP_AGENCY_ID_KEY,
			"NCIP AgencyId directory symbol for this HostLMS. Defaults to NCIP SystemId.",
			false);

	URI endpointUriFor(HostLms hostLms) {
		return URI.create(ENDPOINT_URL.getRequiredConfigValue(hostLms));
	}

	String ncipSystemIdFor(HostLms hostLms) {
		return findNcipSystemId(hostLms.getClientConfig())
			.orElseThrow(() -> new IllegalArgumentException(
				"Missing required configuration property: \"" + NCIP_SYSTEM_ID_KEBAB_KEY + "\""));
	}

	String ncipAgencyIdFor(HostLms hostLms) {
		return findNcipAgencyId(hostLms.getClientConfig())
			.orElseGet(() -> ncipSystemIdFor(hostLms));
	}

	private java.util.Optional<String> findNcipAgencyId(Map<String, Object> clientConfig) {
		return NCIP_AGENCY_ID_KEYS.stream()
			.map(key -> getAsOptionalString(clientConfig, key))
			.flatMap(java.util.Optional::stream)
			.findFirst();
	}

	private java.util.Optional<String> findNcipSystemId(Map<String, Object> clientConfig) {
		return NCIP_SYSTEM_ID_KEYS.stream()
			.map(key -> getAsOptionalString(clientConfig, key))
			.flatMap(java.util.Optional::stream)
			.findFirst();
	}
}
