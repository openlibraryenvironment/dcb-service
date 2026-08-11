package org.olf.dcb.graphql.validation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.micronaut.http.exceptions.HttpStatusException;

/**
 * Host LMS configuration rules that the admin API enforces.
 */
class HostLmsConfigValidatorTests {
	private static final String SIERRA = "org.olf.dcb.core.interaction.sierra.SierraLmsClient";
	private static final String ORS_APPLIANCE = "org.olf.dcb.request.lifecycle.ncip.ORSApplianceHostLMS";

	private final HostLmsConfigValidator validator = new HostLmsConfigValidator();

	@Test
	void shouldRejectADefaultAgencyOnASharedSystem() {
		// No single agency can stand in for an unrecognised location when the system
		// hosts several libraries - it would attribute every co-tenant's patrons,
		// including libraries outside the consortium, to whichever one was configured.
		final var config = sierraConfig();
		config.put("shared-system", true);
		config.put("default-agency-code", "some-agency");

		final var exception = assertThrows(HttpStatusException.class,
			() -> validator.validate(SIERRA, config));

		assertThat(exception.getMessage(),
			containsString("cannot be set when 'shared-system' is true"));
	}

	@Test
	void shouldNotRequireADefaultAgencyOnASharedSystem() {
		// Every adapter used to demand default-agency-code unconditionally, which made
		// a correctly configured shared system unrepresentable through the admin UI
		final var config = sierraConfig();
		config.put("shared-system", true);

		assertDoesNotThrow(() -> validator.validate(SIERRA, config));
	}

	@Test
	void shouldStillRequireADefaultAgencyOnADedicatedSystem() {
		final var exception = assertThrows(HttpStatusException.class,
			() -> validator.validate(SIERRA, sierraConfig()));

		assertThat(exception.getMessage(), containsString("default-agency-code"));
	}

	@Test
	void shouldReportTheConflictAsAPredicateForNonApiCallers() {
		// Host LMS records also arrive from application configuration at startup, which
		// never reaches this validator
		final var conflicting = Map.<String, Object>of(
			"shared-system", "true",
			"default-agency-code", "some-agency");

		assertThat(HostLmsConfigValidator.hasSharedSystemConflict(SIERRA, conflicting), is(true));
		assertThat(HostLmsConfigValidator.hasSharedSystemConflict(SIERRA, Map.of()), is(false));
		assertThat(HostLmsConfigValidator.hasSharedSystemConflict(SIERRA, null), is(false));
	}

	@Test
	void shouldAllowADefaultAgencyOnASharedAppliance() {
		// The appliance reads default-agency-code as the agency it names in every NCIP
		// party element, not as a fallback for an unmapped location. An appliance
		// fronting several libraries - which is what shared-system says - needs that
		// identity exactly as much as one fronting a single library.
		final var config = orsApplianceConfig();
		config.put("shared-system", true);
		config.put("default-agency-code", "some-agency");

		assertThat(HostLmsConfigValidator.hasSharedSystemConflict(ORS_APPLIANCE, config), is(false));

		assertDoesNotThrow(() -> validator.validate(ORS_APPLIANCE, config));
	}

	@Test
	void shouldRequireADefaultAgencyOnASharedApplianceToo() {
		final var config = orsApplianceConfig();
		config.put("shared-system", true);

		final var exception = assertThrows(HttpStatusException.class,
			() -> validator.validate(ORS_APPLIANCE, config));

		assertThat(exception.getMessage(), containsString("default-agency-code"));
	}

	private Map<String, Object> orsApplianceConfig() {
		final Map<String, Object> config = new HashMap<>();

		config.put("base-url", "https://appliance.example.com");
		config.put("ncip-endpoint-url", "https://appliance.example.com/ncip");
		config.put("ncip-system-id", "any-system");

		return config;
	}

	private Map<String, Object> sierraConfig() {
		final Map<String, Object> config = new HashMap<>();

		config.put("base-url", "https://sierra.example.com");
		config.put("key", "any-key");
		config.put("secret", "any-secret");
		config.put("page-size", 100);

		return config;
	}
}
