package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.interaction.PingResponse;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;

import io.micronaut.http.HttpResponse;
import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.AlmaGeneralConfiguration;
import services.k_int.interaction.alma.types.CodeValuePair;

/** An Alma API key is granted per area: the ping checks each area DCB uses, as it uses it. */
@TestInstance(PER_CLASS)
class AlmaPingTests {
	private AlmaApiClient almaApi;
	private AlmaHostLmsClient client;

	@BeforeEach
	void beforeEach() {
		final var hostLms = mock(HostLms.class);
		when(hostLms.getCode()).thenReturn("ALMA");
		when(hostLms.getClientConfig()).thenReturn(Map.of());

		almaApi = mock(AlmaApiClient.class);

		final var clientFactory = mock(AlmaClientFactory.class);
		when(clientFactory.createClientFor(hostLms)).thenReturn(almaApi);

		client = new AlmaHostLmsClient(hostLms, clientFactory, mock(ReferenceValueMappingService.class),
			mock(MaterialTypeToItemTypeMappingService.class), mock(LocationToAgencyMappingService.class),
			mock(ConsortiumService.class));

		when(almaApi.testReadResponse("conf")).thenReturn(Mono.just(HttpResponse.ok("GET - OK")));
		when(almaApi.testWrite("users/operation")).thenReturn(Mono.just("POST - OK"));
		when(almaApi.testWrite("bibs")).thenReturn(Mono.just("POST - OK"));
		when(almaApi.retrieveGeneralConfiguration()).thenReturn(Mono.just(AlmaGeneralConfiguration.builder()
			.institution(new CodeValuePair("01TEST_INST", "Test University"))
			.environmentType("sandbox")
			.build()));
	}

	@Test
	void shouldReportTheEnvironmentAndInstitutionTheKeyReaches() {
		final var response = client.ping().block();

		assertThat(response.getStatus(), is(PingResponse.OK));
		assertThat(response.getVersionInfo(), is("ALMA API v1 (sandbox, 01TEST_INST)"));
	}

	@Test
	void shouldFailNamingTheAreaAKeyCannotWrite() {
		when(almaApi.testWrite("users/operation"))
			.thenReturn(Mono.error(new IllegalStateException("UNAUTHORIZED")));

		final var response = client.ping().block();

		assertThat(response.getStatus(), is(PingResponse.ERROR));
		assertThat(response.getAdditional(), containsString("Users (read and write)"));
	}

	@Test
	void shouldStillBeOkWhenTheGeneralConfigurationCannotBeRead() {
		when(almaApi.retrieveGeneralConfiguration())
			.thenReturn(Mono.error(new IllegalStateException("UNAUTHORIZED")));

		final var response = client.ping().block();

		assertThat(response.getStatus(), is(PingResponse.OK));
		assertThat(response.getVersionInfo(), is("ALMA API v1"));
	}

	@Test
	void shouldReportTheApiCallsRemainingAndTheClockSkew() {
		final var aMinuteAhead = ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(60).format(RFC_1123_DATE_TIME);

		when(almaApi.testReadResponse("conf")).thenReturn(Mono.just(HttpResponse.ok("GET - OK")
			.header("X-Exl-Api-Remaining", "48210")
			.header("Date", aMinuteAhead)));

		final var facts = client.ping().block().getFacts();

		assertThat(facts, hasEntry("apiCallsRemaining", (Object) 48210L));
		assertThat(facts, hasKey("clockSkewSeconds"));
		assertThat("about a minute ahead", ((Long) facts.get("clockSkewSeconds")) >= 55L, is(true));
	}

	@Test
	void shouldReportNothingItWasNotTold() {
		assertThat(client.ping().block().getFacts(), not(hasKey("apiCallsRemaining")));
	}

	@Test
	void shouldCallARefusedKeyRefused() {
		when(almaApi.testWrite("bibs")).thenReturn(Mono.error(new io.micronaut.http.client.exceptions
			.HttpClientResponseException("Forbidden", HttpResponse.status(io.micronaut.http.HttpStatus.FORBIDDEN))));

		final var response = client.ping().block();

		assertThat(response.getFailure(), is("REFUSED"));
		assertThat(response.summary(), containsString("REFUSED: The API key was refused"));
	}
}
