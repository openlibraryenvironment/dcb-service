package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.test.PublisherUtils;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.client.HttpClient;
import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.types.userRequest.AlmaRequestResponse;
import services.k_int.interaction.alma.types.userRequest.AlmaRequests;

@TestInstance(PER_CLASS)
class AlmaHostLmsClientPlaceHoldTests {
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
	void shouldAdoptAnExistingHoldBeyondTheFirstPageOfRequests() {
		when(almaApi.retrieveUserHoldRequestsPage("patron-id", 0))
			.thenReturn(Mono.just(requests(IntStream.range(0, 100))));
		when(almaApi.retrieveUserHoldRequestsPage("patron-id", 100))
			.thenReturn(Mono.just(requests(IntStream.range(100, 130))));

		final var localRequest = PublisherUtils.singleValueFrom(sut.placeHoldRequestAtPickupAgency(hold("item-121")));

		assertThat(localRequest.getLocalId(), is("request-121"));
		verify(almaApi, never()).createUserRequest(anyString(), anyString(), any());
	}

	@Test
	void shouldPlaceAHoldWhenThePatronHasNoneOnTheItem() {
		when(almaApi.retrieveUserHoldRequestsPage("patron-id", 0))
			.thenReturn(Mono.just(requests(IntStream.range(0, 3))));
		when(almaApi.createUserRequest(eq("patron-id"), eq("item-999"), any()))
			.thenReturn(Mono.just(AlmaRequestResponse.builder()
				.requestId("new-request")
				.requestStatus("NOT_STARTED")
				.build()));

		final var localRequest = PublisherUtils.singleValueFrom(sut.placeHoldRequestAtPickupAgency(hold("item-999")));

		assertThat(localRequest.getLocalId(), is("new-request"));
		verify(almaApi, never()).retrieveUserHoldRequestsPage("patron-id", 100);
	}

	private static AlmaRequests requests(IntStream numbers) {
		final List<AlmaRequestResponse> requests = numbers
			.mapToObj(n -> AlmaRequestResponse.builder()
				.requestId("request-" + n)
				.requestStatus("NOT_STARTED")
				.itemId("item-" + n)
				.build())
			.toList();

		return AlmaRequests.builder().recordCount(130).requests(requests).build();
	}

	private static PlaceHoldRequestParameters hold(String itemId) {
		return PlaceHoldRequestParameters.builder()
			.localPatronId("patron-id")
			.localItemId(itemId)
			.pickupLocation(Location.builder().localId("PICKUP-LIB").build())
			.activeWorkflow("RET-STD")
			.build();
	}
}
