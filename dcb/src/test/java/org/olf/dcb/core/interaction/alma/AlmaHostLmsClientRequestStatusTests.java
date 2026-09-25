package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.test.PublisherUtils;

import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.types.userRequest.AlmaRequestResponse;

@TestInstance(PER_CLASS)
class AlmaHostLmsClientRequestStatusTests {
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

	@ParameterizedTest
	@CsvSource({"NOT_STARTED,CONFIRMED", "IN_PROCESS,CONFIRMED", "ON_HOLD_SHELF,READY"})
	void shouldMapAlmaUserRequestStatus(String almaStatus, String expectedStatus) {
		whenRequestHasStatus(almaStatus);

		final var request = getRequest();

		assertThat(request.getStatus(), is(expectedStatus));
		assertThat(request.getRawStatus(), is(almaStatus));
	}

	@Test
	void shouldPassThroughAStatusItDoesNotRecognise() {
		whenRequestHasStatus("SOMETHING_NEW");

		assertThat(getRequest().getStatus(), is("SOMETHING_NEW"));
	}

	@Test
	void shouldNotFailWhenAlmaSendsNoStatus() {
		whenRequestHasStatus(null);

		assertThat(getRequest().getStatus(), is(nullValue()));
	}

	private void whenRequestHasStatus(String status) {
		when(almaApi.retrieveUserRequest("patron-id", "request-id"))
			.thenReturn(Mono.just(AlmaRequestResponse.builder()
				.requestId("request-id")
				.requestStatus(status)
				.build()));
	}

	private HostLmsRequest getRequest() {
		return PublisherUtils.singleValueFrom(sut.getRequest(HostLmsRequest.builder()
			.localId("request-id")
			.localPatronId("patron-id")
			.build()));
	}
}
