package org.olf.dcb.core.interaction;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.alma.AlmaClientFactory;
import org.olf.dcb.core.interaction.alma.AlmaHostLmsClient;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.interaction.koha.KohaApiClient;
import org.olf.dcb.core.interaction.koha.KohaClientFactory;
import org.olf.dcb.core.interaction.koha.KohaHostLmsClient;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.client.HttpClient;
import services.k_int.interaction.alma.AlmaApiClient;

/**
 * The {@link HostLmsClient#getClientId()} contract: two clients addressing the same
 * system compare equal, two addressing different systems do not.
 * <p>
 * Everything DCB knows about shared systems rests on this. Workflow routing picks
 * RET-LOCAL over RET-STD with it, and item resolution excludes same-server supply
 * with it - so an adapter that gets it wrong either misroutes real consortial
 * traffic or lets DCB try to lend an item to the database it already lives in.
 * Neither failure is visible at the adapter.
 */
@TestInstance(PER_CLASS)
class HostLmsClientIdentityTests {

	@Nested
	class AlmaIdentity {
		@Test
		void shouldDistinguishSeparateAlmaTenants() {
			// Every Alma client used to return "" here, which made every tenant compare
			// equal to every other - a genuine three-party request between two Alma
			// libraries was routed to RET-LOCAL and placed the supplier's item ids
			// against the borrower's tenant.
			final var first = almaClient("https://eu.alma.exlibrisgroup.com");
			final var second = almaClient("https://na.alma.exlibrisgroup.com");

			assertThat("Separate Alma tenants must not share a system identity",
				first.getClientId(), is(not(second.getClientId())));

			assertThat("Separate Alma tenants must not compare equal",
				first.compareTo(second), is(not(0)));
		}

		@Test
		void shouldTreatOneAlmaTenantAsOneSystem() {
			final var first = almaClient("https://eu.alma.exlibrisgroup.com");
			final var second = almaClient("https://eu.alma.exlibrisgroup.com");

			assertThat("Two Host LMS records on one Alma must compare equal",
				first.compareTo(second), is(0));
		}

		@Test
		void shouldIgnoreCosmeticUrlDifferences() {
			final var withPath = almaClient("https://eu.alma.exlibrisgroup.com/almaws/v1");
			final var withoutPath = almaClient("https://eu.alma.exlibrisgroup.com/");

			assertThat("Identity is the server, not the configured request path",
				withPath.compareTo(withoutPath), is(0));
		}

		private AlmaHostLmsClient almaClient(String almaUrl) {
			final var hostLms = hostLmsWith(Map.<String, Object>of(
				"alma-url", almaUrl,
				"apikey", "any-key"));

			final var clientFactory = mock(AlmaClientFactory.class);
			when(clientFactory.createClientFor(hostLms)).thenReturn(mock(AlmaApiClient.class));

			return new AlmaHostLmsClient(hostLms, mock(HttpClient.class), clientFactory,
				mock(ReferenceValueMappingService.class),
				mock(MaterialTypeToItemTypeMappingService.class),
				mock(LocationToAgencyMappingService.class),
				mock(ConversionService.class), mock(LocationService.class),
				mock(HostLmsService.class), mock(ConsortiumService.class));
		}
	}

	@Nested
	class KohaIdentity {
		@Test
		void shouldTreatTwoHostLmsOnOneKohaAsOneSystem() {
			// Koha used to answer with the Host LMS code, so two records pointing at one
			// Koha never registered as sharing a server.
			final var first = kohaClient("KOHA-NORTH", "https://shared.koha.example.com");
			final var second = kohaClient("KOHA-SOUTH", "https://shared.koha.example.com");

			assertThat("Two Host LMS records on one Koha must compare equal",
				first.compareTo(second), is(0));
		}

		@Test
		void shouldDistinguishSeparateKohaServers() {
			final var first = kohaClient("KOHA-NORTH", "https://north.koha.example.com");
			final var second = kohaClient("KOHA-SOUTH", "https://south.koha.example.com");

			assertThat("Separate Koha servers must not compare equal",
				first.compareTo(second), is(not(0)));
		}

		private KohaHostLmsClient kohaClient(String code, String apiUrl) {
			final var hostLms = hostLmsWith(code, Map.<String, Object>of(
				"api-url", apiUrl,
				"client_id", "any-id",
				"client_secret", "any-secret"));

			final var clientFactory = mock(KohaClientFactory.class);
			when(clientFactory.createClientFor(hostLms)).thenReturn(mock(KohaApiClient.class));

			return new KohaHostLmsClient(hostLms,
				mock(ReferenceValueMappingService.class), clientFactory,
				mock(MaterialTypeToItemTypeMappingService.class),
				mock(LocationToAgencyMappingService.class));
		}
	}

	@Nested
	class QualifierBehaviour {
		@Test
		void shouldSeparateLogicalSystemsSharingOneUrl() {
			// Appliances and gateways front several logical circulation systems on one
			// transport URL. Those libraries can genuinely lend to each other, so the
			// qualifier has to keep them apart.
			final var first = qualifiedClient("https://appliance.example.com", "library-a");
			final var second = qualifiedClient("https://appliance.example.com", "library-b");

			assertThat("Differently qualified systems on one URL must not compare equal",
				first.compareTo(second), is(not(0)));
		}

		@Test
		void shouldLeaveIdentityAloneWithoutAQualifier() {
			final var client = qualifiedClient("https://appliance.example.com", null);

			assertThat(client.getClientId(), is("https://appliance.example.com"));
		}

		@Test
		void shouldIgnoreABlankQualifier() {
			final var unqualified = qualifiedClient("https://appliance.example.com", null);
			final var blank = qualifiedClient("https://appliance.example.com", "   ");

			assertThat("A blank qualifier must not create a distinct system",
				unqualified.compareTo(blank), is(0));
		}

		private HostLmsClient qualifiedClient(String systemIdentity, String qualifier) {
			final Map<String, Object> config = qualifier == null
				? Map.<String, Object>of()
				: Map.<String, Object>of(HostLmsClient.BASE_URL_QUALIFIER, qualifier);

			final var hostLms = hostLmsWith(config);

			return new AbstractHostLmsClient(hostLms) {
				@Override
				public String getClientId() {
					return qualifySystemIdentity(systemIdentity);
				}
			};
		}
	}

	@Nested
	class SharedSystemFlag {
		@Test
		void shouldStillReportDefaultAgencyCodeOnASharedSystem() {
			// The accessor returns the raw configured value and does not judge it. The
			// dangerous meaning of this key - "the agency to assume when a location
			// does not map" - is a resolution concern, and it is suppressed for a
			// shared system in LocationToAgencyMappingService.findDefaultAgencyCode.
			//
			// Guarding it here instead also nulled it for ORSApplianceHostLMS, which
			// reads the same key as the agency it names in every NCIP party element,
			// and so broke patron lookup on exactly the shared appliances the flag is
			// meant to support.
			final var client = clientWithConfig(Map.<String, Object>of(
				"shared-system", true,
				"default-agency-code", "some-agency"));

			assertThat(client.isSharedSystem(), is(true));
			assertThat(client.getDefaultAgencyCode(), is("some-agency"));
		}

		@Test
		void shouldProvideDefaultAgencyCodeOnADedicatedSystem() {
			final var client = clientWithConfig(Map.<String, Object>of("default-agency-code", "some-agency"));

			assertThat(client.isSharedSystem(), is(false));
			assertThat(client.getDefaultAgencyCode(), is("some-agency"));
		}

		@Test
		void shouldReadTheFlagFromAStringConfigValue() {
			// Config arrives as JSON from the admin UI and as YAML from file imports,
			// so the flag cannot rely on already being a Boolean.
			final var client = clientWithConfig(Map.<String, Object>of("shared-system", "true"));

			assertThat(client.isSharedSystem(), is(true));
		}

		private HostLmsClient clientWithConfig(Map<String, Object> config) {
			return new AbstractHostLmsClient(hostLmsWith(config)) {
				@Override
				public String getClientId() {
					return "any-system";
				}
			};
		}
	}

	private static HostLms hostLmsWith(Map<String, Object> clientConfig) {
		return hostLmsWith("ANY-HOST-LMS", clientConfig);
	}

	private static HostLms hostLmsWith(String code, Map<String, Object> clientConfig) {
		final var hostLms = mock(HostLms.class);

		when(hostLms.getCode()).thenReturn(code);
		when(hostLms.getClientConfig()).thenReturn(clientConfig);

		return hostLms;
	}
}
