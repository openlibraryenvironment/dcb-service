package org.olf.dcb.graphql.validation;

import static org.olf.dcb.core.interaction.HostLmsClient.SHARED_SYSTEM;

import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/** <p> This is a class for validating the config supplied for a new Host LMS.
 * </p><br>
 * <p> This class applies validation rules for each type of LMS. When support for a new LMS type is added, this class must be updated and the docs updated
 * Similarly to LocationInputValidator, this class shifts the validation out of the data fetcher class to improve readability and separate concerns
 * */


@Singleton
@Slf4j
public class HostLmsConfigValidator {

	// The supported LMS Client Classes
	private static final String CLASS_SIERRA = "org.olf.dcb.core.interaction.sierra.SierraLmsClient";
	private static final String CLASS_ALMA = "org.olf.dcb.core.interaction.alma.AlmaHostLmsClient";
	private static final String CLASS_FOLIO = "org.olf.dcb.core.interaction.folio.ConsortialFolioHostLmsClient";
	private static final String CLASS_POLARIS = "org.olf.dcb.core.interaction.polaris.PolarisLmsClient";
	private static final String CLASS_KOHA = "org.olf.dcb.core.interaction.koha.KohaHostLmsClient";
	private static final String CLASS_FOUNDATION = "org.olf.dcb.core.interaction.foundation.FoundationClient";
	private static final String CLASS_ORS_APPLIANCE = "org.olf.dcb.request.lifecycle.ncip.ORSApplianceHostLMS";

	// Foundation composes a base protocol rather than speaking one fixed API, so
	// which keys are required depends on this value. NCIP when unset - the same
	// default FoundationClient applies.
	private static final String PROTOCOL_NCIP = "NCIP";
	private static final String PROTOCOL_SIP2 = "SIP2";

	public void validate(String lmsClientClass, Map<String, Object> clientConfig) {
		if (lmsClientClass == null || lmsClientClass.isBlank()) {
			throw new HttpStatusException(HttpStatus.BAD_REQUEST, "lmsClientClass cannot be null or empty.");
		}

		if (clientConfig == null || clientConfig.isEmpty()) {
			throw new HttpStatusException(HttpStatus.BAD_REQUEST, "clientConfig cannot be null or empty.");
		}

		validateSharedSystem(lmsClientClass, clientConfig);

		switch (lmsClientClass) {
			case CLASS_SIERRA -> validateSierra(clientConfig);
			case CLASS_ALMA -> validateAlma(clientConfig);
			case CLASS_FOLIO -> validateFolio(clientConfig);
			case CLASS_POLARIS -> validatePolaris(clientConfig);
			case CLASS_KOHA -> validateKoha(clientConfig);
			case CLASS_FOUNDATION -> validateFoundation(clientConfig);
			case CLASS_ORS_APPLIANCE -> validateOrsAppliance(clientConfig);
			default -> throw new HttpStatusException(HttpStatus.BAD_REQUEST,
				"Unsupported LMS Client Class: " + lmsClientClass);
		}
	}

	/**
	 * A shared system hosts several participating libraries, so no single agency can
	 * stand in for an unrecognised location. Accepting a default agency code here
	 * would let it silently attribute every co-tenant's patrons - including libraries
	 * outside the consortium entirely - to one library, with nothing to indicate that
	 * anything had gone wrong. Refuse the combination rather than ignore it.
	 */
	private void validateSharedSystem(String lmsClientClass, Map<String, Object> config) {
		if (hasSharedSystemConflict(lmsClientClass, config)) {
			throw new HttpStatusException(HttpStatus.BAD_REQUEST, SHARED_SYSTEM_CONFLICT_DETAIL);
		}
	}

	/**
	 * The contradiction described by {@link #validateSharedSystem}, as a predicate.
	 * <p>
	 * Scoped by client class because 'default-agency-code' does not mean the same
	 * thing everywhere. For most adapters it is a fallback: the agency to assume
	 * when a patron's home location does not map, which is precisely what a shared
	 * system cannot have. For the OpenRS appliance it is an identity - the agency
	 * DCB names in the NCIP party element on every LookupUser and LookupItemSet -
	 * and an appliance fronting several libraries needs it exactly as much as one
	 * fronting a single library does.
	 * <p>
	 * Host LMS records also arrive from application configuration at startup, which
	 * never passes through this validator. That path cannot reasonably refuse to boot
	 * over it, but it can say so - see DCBStartupEventListener.
	 */
	public static boolean hasSharedSystemConflict(String lmsClientClass, Map<String, Object> config) {
		if (config == null || CLASS_ORS_APPLIANCE.equals(lmsClientClass)) {
			return false;
		}

		return isSharedSystem(config) && isPresent(config, "default-agency-code");
	}

	public static final String SHARED_SYSTEM_CONFLICT_DETAIL
		= "Invalid Configuration. 'default-agency-code' cannot be set when 'shared-system' is true: "
			+ "a shared system must map each library's locations to its agency explicitly.";

	private static boolean isSharedSystem(Map<String, Object> config) {
		return Boolean.parseBoolean(String.valueOf(config.getOrDefault(SHARED_SYSTEM, Boolean.FALSE)));
	}

	/**
	 * Required on a dedicated system, forbidden on a shared one.
	 *
	 * @see #validateSharedSystem
	 */
	private void checkDefaultAgencyCode(Map<String, Object> config, List<String> missing) {
		if (isSharedSystem(config)) {
			return;
		}

		checkPresent(config, "default-agency-code", missing);
	}

	private void validateSierra(Map<String, Object> config) {
		List<String> missing = new ArrayList<>();
		checkPresent(config, "base-url", missing);
		checkPresent(config, "key", missing);
		checkPresent(config, "secret", missing);
		checkDefaultAgencyCode(config, missing);
		checkPresent(config, "page-size", missing);

		throwIfMissing("Sierra", missing);
	}

	private void validateAlma(Map<String, Object> config) {
		List<String> missing = new ArrayList<>();
		checkPresent(config, "base-url", missing);
		checkPresent(config, "alma-url", missing);
		checkPresent(config, "apikey", missing);
		checkPresent(config, "institution-code", missing);
		checkDefaultAgencyCode(config, missing);

		throwIfMissing("Alma", missing);
	}

	private void validateFolio(Map<String, Object> config) {
		List<String> missing = new ArrayList<>();
		checkPresent(config, "base-url", missing);
		checkPresent(config, "apikey", missing);
		checkDefaultAgencyCode(config, missing);
		throwIfMissing("Folio", missing);
	}

	private void validatePolaris(Map<String, Object> config) {
		List<String> missing = new ArrayList<>();
		checkPresent(config, "base-url", missing);
		checkPresent(config, "access-id", missing);
		checkPresent(config, "access-key", missing);
		checkPresent(config, "domain-id", missing);
		checkPresent(config, "logon-branch-id", missing);
		checkPresent(config, "logon-user-id", missing);
		checkPresent(config, "staff-username", missing);
		checkPresent(config, "staff-password", missing);
		checkDefaultAgencyCode(config, missing);

		// Check the nested objects. This is a good opportunity to extend to do more specific analysis
		if (!config.containsKey("papi") || !(config.get("papi") instanceof Map)) {
			missing.add("papi (object)");
		}
		if (!config.containsKey("services") || !(config.get("services") instanceof Map)) {
			missing.add("services (object)");
		}
		if (!config.containsKey("item") || !(config.get("item") instanceof Map)) {
			missing.add("item (object)");
		}

		throwIfMissing("Polaris", missing);
	}

	/**
	 * Koha talks OAuth against its REST API, so the credential pair is required
	 * as well as the URL. The three "virtual" codes are declared required by
	 * KohaClientConfig, which throws on the first request without them - so
	 * accepting a config that omits them only defers the failure to a point where
	 * it is much harder to diagnose.
	 *
	 * Note the key is "api-url", NOT "base-url" as everywhere else, and the OAuth
	 * pair is snake_case; both match KohaClientConfig exactly.
	 */
	private void validateKoha(Map<String, Object> config) {
		List<String> missing = new ArrayList<>();
		checkPresent(config, "api-url", missing);
		checkPresent(config, "client_id", missing);
		checkPresent(config, "client_secret", missing);
		checkDefaultAgencyCode(config, missing);
		checkPresent(config, "sharing-library-code", missing);
		checkPresent(config, "virtual-item-library-code", missing);
		checkPresent(config, "virtual-item-location-code", missing);

		throwIfMissing("Koha", missing);
	}

	/**
	 * The Foundation connector composes its behaviour from a base protocol plus
	 * per-operation overrides, so there is no single fixed key set: an NCIP host
	 * needs an NCIP endpoint and a SIP2 host needs a socket. Validating the
	 * union would reject every valid configuration, so validate the branch the
	 * config actually selects.
	 */
	private void validateFoundation(Map<String, Object> config) {
		List<String> missing = new ArrayList<>();
		checkDefaultAgencyCode(config, missing);

		final String protocol = foundationBaseProtocol(config);

		if (PROTOCOL_SIP2.equalsIgnoreCase(protocol)) {
			// Sip2Adaptor reads these from the nested "sip2" object; everything
			// else it defaults, so the socket is all that has to be stated.
			Map<String, Object> sip2 = nestedObject(config, "sip2");
			if (sip2 == null) {
				missing.add("sip2 (object)");
			} else {
				checkPresent(sip2, "host", missing, "sip2.");
				checkPresent(sip2, "port", missing, "sip2.");
			}
		} else if (PROTOCOL_NCIP.equalsIgnoreCase(protocol)) {
			// NcipAdaptor resolves the endpoint from the unified key first and
			// falls back to the legacy nested one, so either satisfies this.
			if (!isPresent(config, "ncip-endpoint-url")
				&& !isPresent(nestedObject(config, "ncip"), "endpoint")) {
				missing.add("ncip-endpoint-url");
			}
		} else {
			throw new HttpStatusException(HttpStatus.BAD_REQUEST, String.format(
				"Invalid Foundation Configuration. Unsupported 'base-protocol': %s. Supported values are: %s, %s",
				protocol, PROTOCOL_NCIP, PROTOCOL_SIP2));
		}

		throwIfMissing("Foundation", missing);
	}

	/**
	 * The OpenRS appliance is an NCIP v2.02 peer. ORSApplianceHostLMS declares
	 * the endpoint and system id required; the agency id defaults to the system
	 * id, so it is only warned about.
	 *
	 * The NCIP keys are accepted in kebab-case and camelCase because
	 * NcipHostLmsConfiguration reads both - configuration written by the DCB
	 * profile registration flow uses kebab-case.
	 * <p>
	 * 'default-agency-code' is unconditionally required here, including on a shared
	 * appliance. Unlike everywhere else it is not a resolution fallback but the
	 * agency DCB names in the NCIP party element - see hasSharedSystemConflict.
	 */
	private void validateOrsAppliance(Map<String, Object> config) {
		List<String> missing = new ArrayList<>();
		checkPresent(config, "base-url", missing);
		checkPresent(config, "default-agency-code", missing);
		checkPresent(config, "ncip-endpoint-url", missing);

		if (!isPresent(config, "ncip-system-id") && !isPresent(config, "ncipSystemId")) {
			missing.add("ncip-system-id");
		}

		throwIfMissing("OpenRS appliance", missing);
	}

	/** NCIP unless the config says otherwise, matching FoundationClient. */
	private String foundationBaseProtocol(Map<String, Object> config) {
		Object fromImperative = null;
		Map<String, Object> capabilities = nestedObject(config, "capabilities");
		if (capabilities != null) {
			Map<String, Object> imperative = nestedObject(capabilities, "imperative");
			if (imperative != null) {
				fromImperative = imperative.get("base-protocol");
			}
		}

		Object value = fromImperative != null ? fromImperative : config.get("base-protocol");
		return value == null || value.toString().isBlank()
			? PROTOCOL_NCIP
			: value.toString();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> nestedObject(Map<String, Object> config, String key) {
		if (config == null) return null;
		Object value = config.get(key);
		return value instanceof Map ? (Map<String, Object>) value : null;
	}


	// "Warn but allow"
	public List<String> findConfigurationWarnings(String lmsClientClass, Map<String, Object> clientConfig) {
		if (lmsClientClass == null || clientConfig == null) return Collections.emptyList();

		List<String> warnings = new ArrayList<>();

		// Put the warnings here. This is for stuff that might not be essential, but it's worth noting
		if (CLASS_POLARIS.equals(lmsClientClass)) {
			// Check for shelfLocationPolicyMap
			if (!clientConfig.containsKey("shelfLocationPolicyMap") || !(clientConfig.get("shelfLocationPolicyMap") instanceof Map)) {
				warnings.add("Missing 'shelfLocationPolicyMap' in Polaris config. Defaults will be used.");
			}
		}
		if (CLASS_FOLIO.equals(lmsClientClass))
		{
			if (!clientConfig.containsKey("folio-tenant")) {
				warnings.add("Missing 'folio-tenant' in FOLIO config. ");
			}
			if(!clientConfig.containsKey("user-base-url")) {
				warnings.add("Missing 'user-base-url' in FOLIO config. ");
			}
		}
		if (CLASS_KOHA.equals(lmsClientClass)) {
			if (!clientConfig.containsKey("page-size")) {
				warnings.add("Missing 'page-size' in Koha config. A default will be used for harvesting.");
			}
		}
		if (CLASS_FOUNDATION.equals(lmsClientClass)) {
			// Sip2Adaptor's transport is not wired yet, so every SIP2 operation
			// fails fast. Creating the Host LMS is still allowed - the record is
			// useful ahead of the protocol slice landing - but say so now.
			if (PROTOCOL_SIP2.equalsIgnoreCase(foundationBaseProtocol(clientConfig))) {
				warnings.add("Foundation SIP2 transport is not yet implemented. SIP2 operations will fail until it is.");
			}
			if (!clientConfig.containsKey("overrides")
				&& nestedObject(nestedObject(clientConfig, "capabilities"), "imperative") == null) {
				warnings.add("No 'capabilities.imperative' block in Foundation config. Base protocol defaults will be used for every operation.");
			}
		}
		if (CLASS_ORS_APPLIANCE.equals(lmsClientClass)) {
			// ORSApplianceOaiPmhIngestSource throws on construction without one
			// of these, which surfaces as a failed harvest rather than a failed
			// create.
			if (!clientConfig.containsKey("oai-endpoint-url") && !clientConfig.containsKey("tenant-id")) {
				warnings.add("Missing 'oai-endpoint-url' or 'tenant-id' in OpenRS appliance config. Ingest cannot run without one of them.");
			}
			if (!clientConfig.containsKey("ncip-agency-id") && !clientConfig.containsKey("ncipAgencyId")) {
				warnings.add("Missing 'ncip-agency-id' in OpenRS appliance config. The NCIP system id will be used instead.");
			}
			if (!clientConfig.containsKey("ncip-peer-auth-mode")) {
				warnings.add("Missing 'ncip-peer-auth-mode' in OpenRS appliance config. Inbound NCIP peer authentication will not be enforced.");
			}
		}

		return warnings;
	}

	/** Null-tolerant: validateFoundation asks this about nested objects that may not exist. */
	private static boolean isPresent(Map<String, Object> config, String key) {
		return config != null && config.get(key) != null
			&& !config.get(key).toString().isBlank();
	}

	private void checkPresent(Map<String, Object> config, String key, List<String> missingList) {
		checkPresent(config, key, missingList, "");
	}

	/**
	 * `keyPrefix` is for keys inside a nested object, so the error names the
	 * path the user has to fix ("sip2.host") rather than a bare "host" that
	 * appears nowhere in their config.
	 */
	private void checkPresent(Map<String, Object> config, String key, List<String> missingList, String keyPrefix) {
		if (!isPresent(config, key)) {
			missingList.add(keyPrefix + key);
		}
	}

	private void throwIfMissing(String lmsName, List<String> missing) {
		if (!missing.isEmpty()) {
			throw new HttpStatusException(HttpStatus.BAD_REQUEST,
				String.format("Invalid %s Configuration. Missing required fields: %s", lmsName, String.join(", ", missing)));
		}
	}
}
