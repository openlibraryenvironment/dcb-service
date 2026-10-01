package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.test.PublisherUtils;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.types.AlmaUser;
import services.k_int.interaction.alma.types.CodeValuePair;

@TestInstance(PER_CLASS)
class AlmaHostLmsClientPatronAuthTests {
	private static final String AUTH_PROFILE = "BASIC/BARCODE+PASSWORD";

	private AlmaApiClient almaApi;
	private AlmaHostLmsClient sut;

	@BeforeEach
	void setUp() {
		final var hostLms = mock(HostLms.class);
		when(hostLms.getCode()).thenReturn("ALMA");

		almaApi = mock(AlmaApiClient.class);

		final var clientFactory = mock(AlmaClientFactory.class);
		when(clientFactory.createClientFor(hostLms)).thenReturn(almaApi);

		sut = new AlmaHostLmsClient(
			hostLms,
			clientFactory,
			mock(ReferenceValueMappingService.class),
			mock(MaterialTypeToItemTypeMappingService.class),
			mock(LocationToAgencyMappingService.class),
			mock(ConsortiumService.class));
	}

	@Test
	void shouldAuthenticateThePasswordBeforeLookingUpThePatron() {
		when(almaApi.authenticateUser("BAR1", "correct")).thenReturn(Mono.empty());
		when(almaApi.getUserDetails("BAR1")).thenReturn(Mono.just(almaUser("BAR1")));

		final var patron = PublisherUtils.singleValueFrom(sut.patronAuth(AUTH_PROFILE, "BAR1", "correct"));

		assertThat(patron.getLocalId(), contains("BAR1"));

		final var order = inOrder(almaApi);
		order.verify(almaApi).authenticateUser("BAR1", "correct");
		order.verify(almaApi).getUserDetails("BAR1");
	}

	@Test
	void shouldRejectThePatronWhenAlmaRejectsThePassword() {
		when(almaApi.authenticateUser("BAR1", "wrong"))
			.thenReturn(Mono.error(new HttpClientResponseException("Bad Request", HttpResponse.badRequest())));

		final var patron = PublisherUtils.singleValueFrom(sut.patronAuth(AUTH_PROFILE, "BAR1", "wrong"));

		assertThat(patron, is(nullValue()));
		verify(almaApi, never()).getUserDetails(any());
	}

	@Test
	void shouldNotCallAlmaWithoutAPassword() {
		final var patron = PublisherUtils.singleValueFrom(sut.patronAuth(AUTH_PROFILE, "BAR1", ""));

		assertThat(patron, is(nullValue()));
		verifyNoInteractions(almaApi);
	}

	@Test
	void shouldReportAnAlmaOutageAsAnErrorRatherThanAnInvalidLogin() {
		when(almaApi.authenticateUser("BAR1", "correct"))
			.thenReturn(Mono.error(new HttpClientResponseException("Server Error", HttpResponse.serverError())));

		assertThrows(HttpClientResponseException.class,
			() -> sut.patronAuth(AUTH_PROFILE, "BAR1", "correct").block());
	}

	@Test
	void shouldVerifyAPinAgainstTheSameInternalPassword() {
		when(almaApi.authenticateUser("BAR1", "1234")).thenReturn(Mono.empty());
		when(almaApi.getUserDetails("BAR1")).thenReturn(Mono.just(almaUser("BAR1")));

		final var patron = PublisherUtils.singleValueFrom(sut.patronAuth("BASIC/BARCODE+PIN", "BAR1", "1234"));

		assertThat(patron.getLocalId(), contains("BAR1"));
		verify(almaApi).authenticateUser("BAR1", "1234");
	}

	@Test
	void shouldRefuseAnAuthProfileAlmaCannotVerify() {
		final var error = assertThrows(IllegalStateException.class,
			() -> sut.patronAuth("BASIC/BARCODE+NAME", "BAR1", "Jane Doe").block());

		assertThat(error.getMessage().contains("BASIC/BARCODE+NAME"), is(true));
		verifyNoInteractions(almaApi);
	}

	private static AlmaUser almaUser(String primaryId) {
		return AlmaUser.builder()
			.primary_id(primaryId)
			.first_name("Test")
			.last_name("Patron")
			.user_group(CodeValuePair.builder().value("UNDRGRD").build())
			.build();
	}
}
