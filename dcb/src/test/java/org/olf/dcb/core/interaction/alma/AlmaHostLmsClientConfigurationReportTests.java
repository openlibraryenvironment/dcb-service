package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.ConfigurationReport;
import org.olf.dcb.core.interaction.ConfigurationReport.CheckResult;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.client.HttpClient;
import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.AlmaCodeTable;
import services.k_int.interaction.alma.AlmaLibrariesResponse;
import services.k_int.interaction.alma.AlmaLibraryResponse;
import services.k_int.interaction.alma.AlmaLocation;
import services.k_int.interaction.alma.AlmaLocationResponse;
import services.k_int.interaction.alma.types.LinkValuePair;

@TestInstance(PER_CLASS)
class AlmaHostLmsClientConfigurationReportTests {
	private AlmaApiClient almaApi;
	private HostLms hostLms;

	@BeforeEach
	void setUp() {
		hostLms = mock(HostLms.class);
		when(hostLms.getCode()).thenReturn("ALMA");

		almaApi = mock(AlmaApiClient.class);
	}

	private AlmaHostLmsClient clientWithConfig(Map<String, Object> config) {
		when(hostLms.getClientConfig()).thenReturn(config);

		final var clientFactory = mock(AlmaClientFactory.class);
		when(clientFactory.createClientFor(hostLms)).thenReturn(almaApi);

		return new AlmaHostLmsClient(
			hostLms,
			mock(HttpClient.class),
			clientFactory,
			mock(ReferenceValueMappingService.class),
			mock(MaterialTypeToItemTypeMappingService.class),
			mock(LocationToAgencyMappingService.class),
			mock(ConversionService.class),
			mock(LocationService.class),
			mock(HostLmsService.class),
			mock(ConsortiumService.class));
	}

	@Test
	void shouldReportConfiguredValuesThatAlmaHasAndThoseItDoesNot() {
		whenAlmaReturnsTheUsualVocabularies();

		final var sut = clientWithConfig(Map.of(
			"sharing-library-code", "RES_SHARE",
			"virtual-item-library-code", "RES_SHARE",
			"virtual-item-location-code", "NOT_A_REAL_LOCATION",
			"no-renew-item-policy", "DCB_NO_RENEW"));

		final var report = sut.checkConfiguration().block();

		assertThat(report.status(), is(ConfigurationReport.Status.CHECKED));
		assertThat(resultOf(report, "sharing-library-code"), is(CheckResult.PRESENT));
		assertThat(resultOf(report, "no-renew-item-policy"), is(CheckResult.PRESENT));

		// Configured in DCB, absent from Alma - the case this report exists to surface
		assertThat(resultOf(report, "virtual-item-location-code"), is(CheckResult.MISSING));
	}

	@Test
	void shouldDistinguishAnUnsetSettingFromOneAlmaDoesNotHave() {
		whenAlmaReturnsTheUsualVocabularies();

		final var sut = clientWithConfig(Map.of("sharing-library-code", "RES_SHARE"));

		final var report = sut.checkConfiguration().block();

		// Nothing set in DCB is not the same fault as a value Alma rejects
		assertThat(resultOf(report, "virtual-item-library-code"), is(CheckResult.NOT_CONFIGURED));
	}

	@Test
	void shouldOfferTheVocabulariesMappingsAreBuiltFrom() {
		whenAlmaReturnsTheUsualVocabularies();

		final var report = clientWithConfig(Map.of()).checkConfiguration().block();

		assertThat(codesIn(report, "Item types"), is(List.of("BOOK", "DVD")));
		assertThat(codesIn(report, "Patron types"), is(List.of("UNDRGRD", "STAFF")));

		// A bare location code is ambiguous across libraries, so the library is named
		assertThat(descriptionIn(report, "Locations", "MAIN"), containsString("Resource Sharing"));
	}

	@Test
	void shouldSayWhenAListCouldNotBeReadRatherThanCallTheSettingMissing() {
		whenAlmaReturnsTheUsualVocabularies();

		when(almaApi.retrieveCodeTable("ItemPolicy"))
			.thenReturn(Mono.error(new RuntimeException("Alma is unwell")));

		final var sut = clientWithConfig(Map.of("no-renew-item-policy", "DCB_NO_RENEW"));

		final var report = sut.checkConfiguration().block();

		// Unreadable is not absent: reporting MISSING here would send someone to create a
		// policy code that may already exist
		assertThat(resultOf(report, "no-renew-item-policy"), is(CheckResult.UNKNOWN));
	}

	private void whenAlmaReturnsTheUsualVocabularies() {
		when(almaApi.retrieveCodeTable("PhysicalMaterialType"))
			.thenReturn(Mono.just(codeTable("BOOK", "Book", "DVD", "DVD")));

		when(almaApi.retrieveCodeTable("UserGroups"))
			.thenReturn(Mono.just(codeTable("UNDRGRD", "Undergraduate", "STAFF", "Staff")));

		when(almaApi.retrieveCodeTable("ItemPolicy"))
			.thenReturn(Mono.just(codeTable("BOOK", "Book", "DCB_NO_RENEW", "No renewal")));

		when(almaApi.retrieveLibraries()).thenReturn(Mono.just(AlmaLibrariesResponse.builder()
			.libraries(List.of(AlmaLibraryResponse.builder()
				.code("RES_SHARE")
				.name("Resource Sharing")
				.numberOfLocations(LinkValuePair.builder().value(1).build())
				.build()))
			.build()));

		when(almaApi.retrieveLocations("RES_SHARE")).thenReturn(Mono.just(AlmaLocationResponse.builder()
			.locations(List.of(AlmaLocation.builder().code("MAIN").name("Main stacks").build()))
			.build()));
	}

	private static AlmaCodeTable codeTable(String firstCode, String firstDescription,
		String secondCode, String secondDescription) {

		return AlmaCodeTable.builder()
			.rows(List.of(
				AlmaCodeTable.Row.builder().code(firstCode).description(firstDescription).build(),
				AlmaCodeTable.Row.builder().code(secondCode).description(secondDescription).build()))
			.build();
	}

	private static CheckResult resultOf(ConfigurationReport report, String setting) {
		return report.checks().stream()
			.filter(check -> setting.equals(check.setting()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No check for " + setting))
			.result();
	}

	private static List<String> codesIn(ConfigurationReport report, String vocabulary) {
		return vocabularyNamed(report, vocabulary).entries().stream()
			.map(ConfigurationReport.Entry::code)
			.toList();
	}

	private static String descriptionIn(ConfigurationReport report, String vocabulary, String code) {
		return vocabularyNamed(report, vocabulary).entries().stream()
			.filter(entry -> code.equals(entry.code()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No entry " + code + " in " + vocabulary))
			.description();
	}

	private static ConfigurationReport.Vocabulary vocabularyNamed(ConfigurationReport report, String name) {
		return report.vocabularies().stream()
			.filter(entry -> name.equals(entry.name()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No vocabulary " + name));
	}
}
