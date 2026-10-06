package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.interaction.ConfigurationReport;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;

import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.AlmaCirculationDesk;
import services.k_int.interaction.alma.AlmaCodeTable;
import services.k_int.interaction.alma.AlmaLibrariesResponse;
import services.k_int.interaction.alma.AlmaLibraryResponse;
import services.k_int.interaction.alma.AlmaLocationResponse;

/**
 * Whether the library every supplier hold is sent to can actually take one.
 * <p>
 * Being in the library list is not enough. Alma's Resource Sharing Library is in it, and a hold
 * sent there comes back 401129 naming nothing - which cost a day against a live sandbox.
 */
@TestInstance(PER_CLASS)
class AlmaSharingLibraryCheckTests {
	@Test
	void shouldRefuseAResourceSharingLibraryAsTheSharingLibrary() {
		final var check = sharingLibraryCheck("RES_SHARE",
			library("RES_SHARE", "Resource Sharing Library", true),
			library("dc", "Central Library", false));

		assertThat(check.result(), is(ConfigurationReport.CheckResult.MISSING));
		assertThat(check.detail(), containsString("Resource Sharing Library"));
		assertThat(check.detail(), containsString("401129"));
	}

	@Test
	void shouldAcceptALibraryPatronsCanCollectFrom() {
		final var check = sharingLibraryCheck("dc",
			library("RES_SHARE", "Resource Sharing Library", true),
			library("dc", "Central Library", false));

		assertThat(check.result(), is(ConfigurationReport.CheckResult.PRESENT));
	}

	@Test
	void shouldStillSayWhenTheLibraryIsNotThereAtAll() {
		final var check = sharingLibraryCheck("NOSUCH",
			library("dc", "Central Library", false));

		assertThat(check.result(), is(ConfigurationReport.CheckResult.MISSING));
		assertThat("A library that does not exist is not a resource sharing library",
			check.detail(), containsString("Not found in Alma"));
	}

	@Test
	void shouldCheckTheAlternativeSharingLibraryTheSameWay() {
		final var report = reportFor(Map.<String, Object>of("sharing-library-code", "dc",
				"alternative-sharing-library-code", "RES_SHARE"),
			library("RES_SHARE", "Resource Sharing Library", true),
			library("dc", "Central Library", false));

		final var check = report.checks().stream()
			.filter(c -> "alternative-sharing-library-code".equals(c.setting()))
			.findFirst()
			.orElseThrow();

		assertThat(check.result(), is(ConfigurationReport.CheckResult.MISSING));
		assertThat(check.detail(), containsString("Resource Sharing Library"));
	}

	@Test
	void shouldAcceptASharingDeskWithAHoldShelf() {
		assertThat(deskCheck(desk(true)).result(), is(ConfigurationReport.CheckResult.PRESENT));
	}

	@Test
	void shouldRefuseASharingDeskWithNoHoldShelf() {
		final var check = deskCheck(desk(false));

		assertThat(check.result(), is(ConfigurationReport.CheckResult.MISSING));
		assertThat(check.detail(), containsString("no hold shelf"));
	}

	@Test
	void shouldSayWhenTheSharingDeskIsNotInTheLibrary() {
		final var check = deskCheck(Mono.error(new AlmaApiException("GET", "/almaws/v1/conf", 400,
			new services.k_int.interaction.alma.types.error.AlmaErrorResponse())));

		assertThat(check.result(), is(ConfigurationReport.CheckResult.MISSING));
		assertThat(check.detail(), containsString("Not found in Alma library dc"));
	}

	private ConfigurationReport.Check deskCheck(Mono<AlmaCirculationDesk> answer) {
		deskAnswer = answer;

		return reportFor(Map.<String, Object>of("sharing-library-code", "dc", "sharing-circ-desk-code", "OPENRS"),
			library("dc", "Central Library", false))
			.checks().stream()
			.filter(c -> "sharing-circ-desk-code".equals(c.setting()))
			.findFirst()
			.orElseThrow();
	}

	private static Mono<AlmaCirculationDesk> desk(boolean holdShelf) {
		return Mono.just(AlmaCirculationDesk.builder().code("OPENRS").hasHoldShelf(holdShelf).build());
	}

	@Test
	void shouldNotReportAnAlternativeThatIsNotConfigured() {
		final var report = reportFor(Map.<String, Object>of("sharing-library-code", "dc"),
			library("dc", "Central Library", false));

		assertThat(report.checks().stream()
			.noneMatch(c -> "alternative-sharing-library-code".equals(c.setting())), is(true));
	}

	private static Mono<AlmaCirculationDesk> deskAnswer = Mono.empty();

	private static ConfigurationReport.Check sharingLibraryCheck(String configuredValue,
		AlmaLibraryResponse... libraries) {

		final var report = reportFor(Map.<String, Object>of("sharing-library-code", configuredValue), libraries);

		return report.checks().stream()
			.filter(check -> "sharing-library-code".equals(check.setting()))
			.findFirst()
			.orElseThrow();
	}

	private static ConfigurationReport reportFor(Map<String, Object> config,
		AlmaLibraryResponse... libraries) {

		final var hostLms = mock(HostLms.class);
		when(hostLms.getCode()).thenReturn("ALMA");
		when(hostLms.getClientConfig()).thenReturn(config);

		final var almaApi = mock(AlmaApiClient.class);

		when(almaApi.retrieveCodeTable(anyString()))
			.thenReturn(Mono.just(AlmaCodeTable.builder().rows(List.of()).build()));

		when(almaApi.retrieveLibraries()).thenReturn(Mono.just(AlmaLibrariesResponse.builder()
			.libraries(List.of(libraries))
			.build()));

		when(almaApi.retrieveLocations(anyString()))
			.thenReturn(Mono.just(AlmaLocationResponse.builder().locations(List.of()).build()));

		when(almaApi.retrieveCirculationDesk(anyString(), anyString())).thenReturn(deskAnswer);

		final var clientFactory = mock(AlmaClientFactory.class);
		when(clientFactory.createClientFor(hostLms)).thenReturn(almaApi);

		final var client = new AlmaHostLmsClient(hostLms, clientFactory,
			mock(ReferenceValueMappingService.class), mock(MaterialTypeToItemTypeMappingService.class),
			mock(LocationToAgencyMappingService.class), mock(ConsortiumService.class));

		return client.checkConfiguration().block();
	}

	private static AlmaLibraryResponse library(String code, String name, boolean resourceSharing) {
		return AlmaLibraryResponse.builder()
			.code(code)
			.name(name)
			.resourceSharing(resourceSharing)
			.build();
	}
}
