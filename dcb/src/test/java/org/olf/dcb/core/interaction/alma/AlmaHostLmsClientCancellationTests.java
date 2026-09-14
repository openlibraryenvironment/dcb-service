package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.CancelHoldRequestParameters;
import org.olf.dcb.core.interaction.DeleteCommand;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.test.PublisherUtils;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.client.HttpClient;
import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;

@TestInstance(PER_CLASS)
class AlmaHostLmsClientCancellationTests {
	private static final String REASON = "PatronNotInterested";

	private AlmaApiClient almaApi;
	private AlmaHostLmsClient sut;

	@BeforeEach
	void setUp() {
		final var hostLms = mock(HostLms.class);
		when(hostLms.getCode()).thenReturn("ALMA");
		when(hostLms.getClientConfig()).thenReturn(Map.of("request-cancellation-reason", REASON));

		almaApi = mock(AlmaApiClient.class);

		final var clientFactory = mock(AlmaClientFactory.class);
		when(clientFactory.createClientFor(hostLms)).thenReturn(almaApi);

		sut = new AlmaHostLmsClient(
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
	void shouldCancelAHoldWithTheConfiguredReason() {
		when(almaApi.cancelUserRequest("patron-id", "request-id", REASON))
			.thenReturn(Mono.just("Request deleted"));

		final var result = PublisherUtils.singleValueFrom(sut.cancelHoldRequest(
			CancelHoldRequestParameters.builder()
				.patronId("patron-id")
				.localRequestId("request-id")
				.build()));

		assertThat(result, is("request-id"));
		verify(almaApi).cancelUserRequest("patron-id", "request-id", REASON);
	}

	@Test
	void shouldDeleteAHoldWithTheConfiguredReason() {
		when(almaApi.cancelUserRequest("patron-id", "request-id", REASON))
			.thenReturn(Mono.just("Request deleted"));

		PublisherUtils.singleValueFrom(sut.deleteHold(DeleteCommand.builder()
			.patronId("patron-id")
			.requestId("request-id")
			.build()));

		verify(almaApi).cancelUserRequest("patron-id", "request-id", REASON);
	}
}
