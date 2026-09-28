package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;

import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.types.userRequest.AlmaRequest;
import services.k_int.interaction.alma.types.userRequest.AlmaRequestResponse;

/**
 * Where a supplier hold is sent. Alma shelves an item scanned in at its hold's pickup library
 * rather than sending it into transit, so an item the sharing library itself owns must go
 * somewhere else, or the request never moves on.
 */
class AlmaSupplierPickupLibraryTests {
	private AlmaApiClient almaApi;

	@Test
	void shouldSendAnotherLibrarysItemToTheSharingLibrary() {
		final var client = clientWith("dc", "gc");

		placeSupplierHold(client, "bc", "RET-STD");

		assertThat(pickupLibrarySent(), is("dc"));
	}

	@Test
	void shouldSendTheSharingLibrarysOwnItemToTheAlternative() {
		final var client = clientWith("dc", "gc");

		placeSupplierHold(client, "dc", "RET-STD");

		assertThat(pickupLibrarySent(), is("gc"));
	}

	@Test
	void shouldKeepTheSharingLibraryWhenNoAlternativeIsConfigured() {
		final var client = clientWith("dc", null);

		placeSupplierHold(client, "dc", "RET-STD");

		assertThat(pickupLibrarySent(), is("dc"));
	}

	@Test
	void shouldSendEveryItemToTheSharingDeskWhenOneIsConfigured() {
		final var client = clientWith("dc", "gc", "OPENRS");

		placeSupplierHold(client, "bc", "RET-STD");

		final var request = requestSent();
		assertThat(request.getPickupLocationType(), is("CIRCULATION_DESK"));
		assertThat(request.getPickupLocationLibrary(), is("dc"));
		assertThat(request.getPickupLocationCirculationDesk(), is("OPENRS"));
	}

	@Test
	void shouldPreferTheDeskToTheAlternativeForTheSharingLibrarysOwnItem() {
		// A scan at any other desk, even in the same library, is a transit, so the alternative
		// library is not needed once there is a desk
		final var client = clientWith("dc", "gc", "OPENRS");

		placeSupplierHold(client, "dc", "RET-STD");

		assertThat(requestSent().getPickupLocationLibrary(), is("dc"));
		assertThat(requestSent().getPickupLocationCirculationDesk(), is("OPENRS"));
	}

	@Test
	void shouldAddressALibraryWhenNoDeskIsConfigured() {
		final var client = clientWith("dc", null);

		placeSupplierHold(client, "bc", "RET-STD");

		assertThat(requestSent().getPickupLocationType(), is("LIBRARY"));
		assertThat(requestSent().getPickupLocationCirculationDesk(), is(nullValue()));
	}

	@Test
	void shouldLeaveTheExpeditedWorkflowsPickupLocationAlone() {
		final var client = clientWith("dc", "gc");

		placeSupplierHold(client, "dc", "RET-EXP");

		assertThat(pickupLibrarySent(), is("PICKUP"));
	}

	private AlmaHostLmsClient clientWith(String sharingLibrary, String alternative) {
		return clientWith(sharingLibrary, alternative, null);
	}

	private AlmaHostLmsClient clientWith(String sharingLibrary, String alternative, String desk) {
		final Map<String, Object> config = new HashMap<>();
		config.put("sharing-library-code", sharingLibrary);
		if (alternative != null) {
			config.put("alternative-sharing-library-code", alternative);
		}
		if (desk != null) {
			config.put("sharing-circ-desk-code", desk);
		}

		final var hostLms = mock(HostLms.class);
		when(hostLms.getCode()).thenReturn("ALMA");
		when(hostLms.getClientConfig()).thenReturn(config);

		almaApi = mock(AlmaApiClient.class);
		when(almaApi.retrieveUserHoldRequestsPage(anyString(), anyInt())).thenReturn(Mono.empty());
		when(almaApi.createUserRequest(anyString(), anyString(), any()))
			.thenReturn(Mono.just(AlmaRequestResponse.builder()
				.requestId("request-1")
				.requestStatus("NOT_STARTED")
				.build()));

		final var clientFactory = mock(AlmaClientFactory.class);
		when(clientFactory.createClientFor(hostLms)).thenReturn(almaApi);

		return new AlmaHostLmsClient(hostLms, clientFactory,
			mock(ReferenceValueMappingService.class), mock(MaterialTypeToItemTypeMappingService.class),
			mock(LocationToAgencyMappingService.class), mock(ConsortiumService.class));
	}

	private static void placeSupplierHold(AlmaHostLmsClient client, String itemsLibrary, String workflow) {
		client.placeHoldRequestAtSupplyingAgency(PlaceHoldRequestParameters.builder()
				.localPatronId("patron-1")
				.localItemId("item-1")
				.supplyingLocalItemLocation(itemsLibrary)
				.pickupLocation(Location.builder().code("PICKUP").localId("PICKUP").build())
				.activeWorkflow(workflow)
				.patronRequestId("pr-1")
				.build())
			.block();
	}

	private String pickupLibrarySent() {
		return requestSent().getPickupLocationLibrary();
	}

	private AlmaRequest requestSent() {
		final var request = ArgumentCaptor.forClass(AlmaRequest.class);
		verify(almaApi).createUserRequest(eq("patron-1"), eq("item-1"), request.capture());

		return request.getValue();
	}
}
