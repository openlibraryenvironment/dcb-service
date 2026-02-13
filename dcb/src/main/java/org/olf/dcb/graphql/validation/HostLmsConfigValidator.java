package org.olf.dcb.graphql.validation;

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

	public void validate(String lmsClientClass, Map<String, Object> clientConfig) {
		if (lmsClientClass == null || lmsClientClass.isBlank()) {
			throw new HttpStatusException(HttpStatus.BAD_REQUEST, "lmsClientClass cannot be null or empty.");
		}

		if (clientConfig == null || clientConfig.isEmpty()) {
			throw new HttpStatusException(HttpStatus.BAD_REQUEST, "clientConfig cannot be null or empty.");
		}

		switch (lmsClientClass) {
			case CLASS_SIERRA -> validateSierra(clientConfig);
			case CLASS_ALMA -> validateAlma(clientConfig);
			case CLASS_FOLIO -> validateFolio(clientConfig);
			case CLASS_POLARIS -> validatePolaris(clientConfig);
			default -> throw new HttpStatusException(HttpStatus.BAD_REQUEST,
				"Unsupported LMS Client Class: " + lmsClientClass);
		}
	}

	private void validateSierra(Map<String, Object> config) {
		List<String> missing = new ArrayList<>();
		checkPresent(config, "base-url", missing);
		checkPresent(config, "key", missing);
		checkPresent(config, "secret", missing);
		checkPresent(config, "default-agency-code", missing);
		checkPresent(config, "page-size", missing);

		throwIfMissing("Sierra", missing);
	}

	private void validateAlma(Map<String, Object> config) {
		List<String> missing = new ArrayList<>();
		checkPresent(config, "base-url", missing);
		checkPresent(config, "alma-url", missing);
		checkPresent(config, "apikey", missing);
		checkPresent(config, "institution-code", missing);
		checkPresent(config, "default-agency-code", missing);

		throwIfMissing("Alma", missing);
	}

	private void validateFolio(Map<String, Object> config) {
		List<String> missing = new ArrayList<>();
		checkPresent(config, "base-url", missing);
		checkPresent(config, "apikey", missing);
		checkPresent(config, "folio-tenant", missing);
		checkPresent(config, "user-base-url", missing);
		checkPresent(config, "default-agency-code", missing);

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
		checkPresent(config, "default-agency-code", missing);

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
		if (!config.containsKey("shelfLocationPolicyMap") || !(config.get("shelfLocationPolicyMap") instanceof Map)) {
			missing.add("item (object)");
		}
		if (!config.containsKey("services") || !(config.get("services") instanceof Map)) {
			missing.add("item (object)");
		}
		throwIfMissing("Polaris", missing);
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
		}

		return warnings;
	}

	private void checkPresent(Map<String, Object> config, String key, List<String> missingList) {
		if (!config.containsKey(key) || config.get(key) == null || config.get(key).toString().isBlank()) {
			missingList.add(key);
		}
	}

	private void throwIfMissing(String lmsName, List<String> missing) {
		if (!missing.isEmpty()) {
			throw new HttpStatusException(HttpStatus.BAD_REQUEST,
				String.format("Invalid %s Configuration. Missing required fields: %s", lmsName, String.join(", ", missing)));
		}
	}
}
