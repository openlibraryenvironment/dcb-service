package org.olf.dcb.graphql.validation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.polaris.PolarisConfig;

import io.micronaut.http.exceptions.HttpStatusException;

/**
 * Host LMS configuration rules that the admin API enforces.
 */
class HostLmsConfigValidatorTests {
	private static final String SIERRA = "org.olf.dcb.core.interaction.sierra.SierraLmsClient";
	private static final String ORS_APPLIANCE = "org.olf.dcb.request.lifecycle.ncip.ORSApplianceHostLMS";
	private static final String KOHA = "org.olf.dcb.core.interaction.koha.KohaHostLmsClient";
	private static final String POLARIS = "org.olf.dcb.core.interaction.polaris.PolarisLmsClient";
	private static final String FOLIO = "org.olf.dcb.core.interaction.folio.ConsortialFolioHostLmsClient";

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

	@Test
	void shouldWarnWhenAKohaCannotHarvest() {
		// Neither key is part of KohaClientConfig, the admin form never asked for them,
		// and KohaOaiPmhIngestSource throws on construction without them - so a Koha
		// created through the UI produced a Host LMS that pinged and never ingested.
		final var warnings = validator.findConfigurationWarnings(KOHA, kohaConfig());

		assertThat(warnings, hasItem(containsString("base-url")));
		assertThat(warnings, hasItem(containsString("metadata-prefix")));
	}

	@Test
	void shouldNotWarnAboutHarvestingWhenTheKohaIsConfiguredToIngest() {
		final var config = kohaConfig();
		config.put("base-url", "https://catalogue.example.org");
		config.put("metadata-prefix", "marcxml");

		final var warnings = validator.findConfigurationWarnings(KOHA, config);

		assertThat(warnings, not(hasItem(containsString("base-url"))));
		assertThat(warnings, not(hasItem(containsString("metadata-prefix"))));
	}

	@Test
	void shouldNotWarnAboutHarvestingWhenIngestIsTurnedOff() {
		// A member that only borrows has nothing to contribute to the shared index, so
		// demanding OAI configuration of it would be noise rather than a warning.
		final var config = kohaConfig();
		config.put("ingest", "false");

		final var warnings = validator.findConfigurationWarnings(KOHA, config);

		assertThat(warnings, not(hasItem(containsString("base-url"))));
		assertThat(warnings, not(hasItem(containsString("metadata-prefix"))));
	}

	private static final String ALMA = "org.olf.dcb.core.interaction.alma.AlmaHostLmsClient";

	@Test
	void shouldRequireTheLibraryAndLocationCodesTheAlmaClientCannotRunWithout() {
		final var config = almaConfig();
		config.remove("sharing-library-code");
		config.remove("virtual-item-library-code");
		config.remove("virtual-item-location-code");
		config.remove("request-cancellation-reason");

		final var exception = assertThrows(HttpStatusException.class,
			() -> validator.validate(ALMA, config));

		assertThat(exception.getMessage(), containsString("sharing-library-code"));
		assertThat(exception.getMessage(), containsString("virtual-item-library-code"));
		assertThat(exception.getMessage(), containsString("virtual-item-location-code"));
		// Ex Libris documents reason as required on the cancel call, from a per-institution code table
		assertThat(exception.getMessage(), containsString("request-cancellation-reason"));
	}

	@Test
	void shouldRefuseAnAlternativeSharingLibraryThatIsTheSharingLibrary() {
		final var config = almaConfig();
		config.put("alternative-sharing-library-code", config.get("sharing-library-code"));

		final var exception = assertThrows(HttpStatusException.class,
			() -> validator.validate(ALMA, config));

		assertThat(exception.getMessage(), containsString("alternative-sharing-library-code"));
	}

	@Test
	void shouldAcceptADifferentAlternativeSharingLibrary() {
		final var config = almaConfig();
		config.put("alternative-sharing-library-code", "OTHER-LIB");

		assertDoesNotThrow(() -> validator.validate(ALMA, config));
	}

	@Test
	void shouldAcceptACompleteAlmaConfiguration() {
		assertDoesNotThrow(() -> validator.validate(ALMA, almaConfig()));
	}

	private Map<String, Object> almaConfig() {
		final Map<String, Object> config = new HashMap<>();

		config.put("base-url", "https://institution.alma.exlibrisgroup.com");
		config.put("alma-url", "https://api-eu.hosted.exlibrisgroup.com");
		config.put("apikey", "any-key");
		config.put("institution-code", "ANY_INSTITUTION");
		config.put("default-agency-code", "any-agency");
		config.put("sharing-library-code", "DCB");
		config.put("virtual-item-library-code", "DCB");
		config.put("virtual-item-location-code", "DCB-LOC");
		config.put("request-cancellation-reason", "LibraryCancelled");

		return config;
	}

	// Neither key is read anywhere in dcb-service: the warnings sent implementers to find
	// values nothing uses
	@Test
	void shouldNotWarnAboutFolioKeysNothingReads() {
		final var warnings = validator.findConfigurationWarnings(
			"org.olf.dcb.core.interaction.folio.ConsortialFolioHostLmsClient",
			Map.of("base-url", "https://folio.example.org", "apikey", "key"));

		assertThat(warnings, not(hasItem(containsString("folio-tenant"))));
		assertThat(warnings, not(hasItem(containsString("user-base-url"))));
	}

	@Test
	void shouldRequireTheValuesThePolarisClientReadsWhenARequestNeedsThem() {
		final var config = polarisConfig();
		config.put("services", Map.of());
		config.put("item", Map.of());

		final var exception = assertThrows(HttpStatusException.class,
			() -> validator.validate(POLARIS, config));

		for (String key : new String[] { "services.organisation-id", "item.ill-location-id",
			"item.fine-code-id", "item.history-action-id", "item.shelving-scheme-id",
			"item.loan-period-code-id", "item.av-loan-period-code-id", "item.renewal-limit" }) {

			assertThat(exception.getMessage(), containsString(key));
		}
	}

	@Test
	void shouldAcceptACompletePolarisConfiguration() {
		assertDoesNotThrow(() -> validator.validate(POLARIS, polarisConfig()));
	}

	@Test
	void shouldAcceptEveryItemAgencyResolutionMethodPolarisImplements() {
		for (String method : PolarisConfig.ITEM_AGENCY_RESOLUTION_METHODS) {
			final var config = polarisConfig();
			config.put("item", polarisItemConfigWith("item-agency-resolution-method", method));

			assertDoesNotThrow(() -> validator.validate(POLARIS, config));
		}
	}

	@Test
	void shouldRejectAnItemAgencyResolutionMethodPolarisDoesNotImplement() {
		final var config = polarisConfig();
		config.put("item", polarisItemConfigWith("item-agency-resolution-method", "locationid"));

		final var exception = assertThrows(HttpStatusException.class,
			() -> validator.validate(POLARIS, config));

		assertThat(exception.getMessage(), containsString("item-agency-resolution-method"));
	}

	@Test
	void shouldAcceptAFolioWithoutARecordSyntaxBecauseItHasADefault() {
		final var config = folioConfig();
		config.remove("record-syntax");

		assertDoesNotThrow(() -> validator.validate(FOLIO, config));
	}

	private Map<String, Object> folioConfig() {
		final Map<String, Object> config = new HashMap<>();
		config.put("base-url", "https://folio.example.org");
		config.put("apikey", "any-key");
		config.put("default-agency-code", "any-agency");
		config.put("record-syntax", "oai_dc");
		return config;
	}

	// The adapter harvests 1000 at a time when it is absent, so requiring it only invited a
	// smaller guess
	@Test
	void shouldNotRequireASierraPageSize() {
		final var config = sierraConfig();
		config.remove("page-size");
		config.put("default-agency-code", "any-agency");

		assertDoesNotThrow(() -> validator.validate(SIERRA, config));
	}

	@Test
	void shouldRejectInvalidPolarisShelfLocationLoanPolicy() {
		final var config = polarisConfig();
		config.put("shelfLocationPolicyMap", Map.of("Reference", "REFERENCE"));

		final var exception = assertThrows(HttpStatusException.class,
			() -> validator.validate(POLARIS, config));

		assertThat(exception.getMessage(), containsString("Reference=REFERENCE"));
		assertThat(exception.getMessage(), containsString("REFERENCE_ONLY"));
	}

	@Test
	void shouldAcceptOnlyAnAddressForTheVirtualPatronMailbox() {
		for (final var lmsClass : List.of(SIERRA, POLARIS, KOHA)) {
			final var config = SIERRA.equals(lmsClass) ? sierraConfig()
				: POLARIS.equals(lmsClass) ? polarisConfig() : kohaConfig();
			config.put("default-agency-code", "AGENCY");

			config.put("virtual-patron-email", "ill-notices@consortium.example.org");
			assertDoesNotThrow(() -> validator.validate(lmsClass, config), lmsClass);

			config.put("virtual-patron-email", "the ILL desk");
			final var exception = assertThrows(HttpStatusException.class,
				() -> validator.validate(lmsClass, config), lmsClass);
			assertThat(exception.getMessage(), containsString("must be an email address"));
		}
	}

	@Test
	void shouldAcceptOnlyTheSierraApiVersionsSierraServes() {
		final var config = sierraConfig();
		config.put("default-agency-code", "AGENCY");
		config.put("api-version", "v5");
		assertDoesNotThrow(() -> validator.validate(SIERRA, config));

		config.put("api-version", "6");
		final var exception = assertThrows(HttpStatusException.class,
			() -> validator.validate(SIERRA, config));
		assertThat(exception.getMessage(), containsString("[v4, v5, v6]"));
	}

	private Map<String, Object> kohaConfig() {
		final Map<String, Object> config = new HashMap<>();

		// The keys KohaClientConfig itself declares required - deliberately a config
		// that validate() accepts, so the warnings are the only thing under test.
		config.put("api-url", "https://koha.example.org");
		config.put("client_id", "any-client-id");
		config.put("client_secret", "any-client-secret");
		config.put("default-agency-code", "any-agency");
		config.put("sharing-library-code", "DCB");
		config.put("virtual-item-library-code", "DCB");
		config.put("page-size", 100);

		return config;
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

	private Map<String, Object> polarisConfig() {
		final Map<String, Object> config = new HashMap<>();
		config.put("base-url", "https://polaris.example.org");
		config.put("access-id", "access-id");
		config.put("access-key", "access-key");
		config.put("domain-id", "domain-id");
		config.put("logon-branch-id", "branch-id");
		config.put("logon-user-id", "user-id");
		config.put("staff-username", "staff");
		config.put("staff-password", "password");
		config.put("default-agency-code", "agency");
		config.put("papi", Map.of());
		config.put("services", Map.of("organisation-id", "1"));
		config.put("item", polarisItemConfig());
		return config;
	}

	private Map<String, Object> polarisItemConfig() {
		final Map<String, Object> item = new HashMap<>();
		item.put("ill-location-id", 73);
		item.put("fine-code-id", 1);
		item.put("history-action-id", 6);
		item.put("shelving-scheme-id", 1);
		item.put("loan-period-code-id", 7);
		item.put("av-loan-period-code-id", 8);
		item.put("renewal-limit", 0);
		return item;
	}

	private Map<String, Object> polarisItemConfigWith(String key, Object value) {
		final var item = polarisItemConfig();
		item.put(key, value);
		return item;
	}
}
