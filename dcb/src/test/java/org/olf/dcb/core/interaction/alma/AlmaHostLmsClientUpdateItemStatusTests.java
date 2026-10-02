package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.interaction.HostLmsClient.CanonicalItemState;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.test.PublisherUtils;

import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.types.CodeValuePair;
import services.k_int.interaction.alma.types.items.AlmaItem;
import services.k_int.interaction.alma.types.items.AlmaItemData;
import services.k_int.interaction.alma.types.userRequest.AlmaRequestResponse;
import services.k_int.interaction.alma.types.userRequest.AlmaRequests;

@TestInstance(PER_CLASS)
class AlmaHostLmsClientUpdateItemStatusTests {
	private AlmaApiClient almaApi;
	private AlmaHostLmsClient sut;

	@BeforeEach
	void setUp() {
		final var hostLms = mock(HostLms.class);
		when(hostLms.getCode()).thenReturn("ALMA");
		when(hostLms.getClientConfig()).thenReturn(Map.of());

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
	void shouldScanAnItemInTransitInAtItsOwningLibrary() {
		final var almaItem = AlmaItem.builder()
			.itemData(AlmaItemData.builder()
				.pid("item-id")
				.library(CodeValuePair.builder().value("MAIN").build())
				.build())
			.build();

		when(almaApi.retrieveItem("bib-id", "holding-id", "item-id")).thenReturn(Mono.just(almaItem));
		when(almaApi.scanIn(any())).thenReturn(Mono.just(almaItem));

		final var result = PublisherUtils.singleValueFrom(sut.updateItemStatus(hostLmsItem(), CanonicalItemState.TRANSIT));

		assertThat(result, is("OK"));
		verify(almaApi).scanIn(new AlmaHostLmsClient.ScanInQuery("bib-id", "holding-id", "item-id", "MAIN",
			"DEFAULT_CIRC_DESK"));
	}

	@Test
	void shouldNotScanInTransitAnItemWhoseHoldIsForPickupAtItsOwnLibrary() {
		itemAt("MAIN");
		holdForPickupAt("MAIN");

		final var result = PublisherUtils.singleValueFrom(
			sut.updateItemStatus(hostLmsItemOnHold(), CanonicalItemState.TRANSIT));

		assertThat(result, is("OK"));
		verify(almaApi, never()).scanIn(any());
	}

	@Test
	void shouldScanInTransitAnItemWhoseHoldIsForPickupElsewhere() {
		final var almaItem = itemAt("MAIN");
		holdForPickupAt("BRANCH");
		when(almaApi.scanIn(any())).thenReturn(Mono.just(almaItem));

		PublisherUtils.singleValueFrom(sut.updateItemStatus(hostLmsItemOnHold(), CanonicalItemState.TRANSIT));

		verify(almaApi).scanIn(new AlmaHostLmsClient.ScanInQuery("bib-id", "holding-id", "item-id", "MAIN",
			"DEFAULT_CIRC_DESK"));
	}

	@Test
	void shouldScanInTransitWhenTheHoldCannotBeRead() {
		final var almaItem = itemAt("MAIN");
		when(almaApi.retrieveItemRequests("bib-id", "holding-id", "item-id"))
			.thenReturn(Mono.error(new RuntimeException("Alma unavailable")));
		when(almaApi.scanIn(any())).thenReturn(Mono.just(almaItem));

		PublisherUtils.singleValueFrom(sut.updateItemStatus(hostLmsItemOnHold(), CanonicalItemState.TRANSIT));

		verify(almaApi).scanIn(any());
	}

	@Test
	void shouldStillScanInOnReceiptAtTheHoldsPickupLibrary() {
		final var almaItem = itemAt("MAIN");
		holdForPickupAt("MAIN");
		when(almaApi.scanIn(any())).thenReturn(Mono.just(almaItem));

		PublisherUtils.singleValueFrom(sut.updateItemStatus(hostLmsItemOnHold(), CanonicalItemState.RECEIVED));

		verify(almaApi).scanIn(any());
	}

	@Test
	void shouldRefuseAStateAlmaHasNoItemActionFor() {
		assertThrows(UnsupportedOperationException.class,
			() -> sut.updateItemStatus(hostLmsItem(), CanonicalItemState.MISSING).block());

		verifyNoInteractions(almaApi);
	}

	private AlmaItem itemAt(String library) {
		final var almaItem = AlmaItem.builder()
			.itemData(AlmaItemData.builder()
				.pid("item-id")
				.library(CodeValuePair.builder().value(library).build())
				.build())
			.build();

		when(almaApi.retrieveItem("bib-id", "holding-id", "item-id")).thenReturn(Mono.just(almaItem));

		return almaItem;
	}

	private void holdForPickupAt(String library) {
		final var someoneElses = AlmaRequestResponse.builder()
			.requestId("other-request")
			.pickupLocationLibrary("ELSEWHERE")
			.build();
		final var dcbs = AlmaRequestResponse.builder()
			.requestId("request-id")
			.pickupLocationLibrary(library)
			.build();

		when(almaApi.retrieveItemRequests("bib-id", "holding-id", "item-id"))
			.thenReturn(Mono.just(new AlmaRequests(2, List.of(someoneElses, dcbs))));
	}

	private static HostLmsItem hostLmsItemOnHold() {
		return HostLmsItem.builder()
			.bibId("bib-id")
			.holdingId("holding-id")
			.localId("item-id")
			.localRequestId("request-id")
			.build();
	}

	private static HostLmsItem hostLmsItem() {
		return HostLmsItem.builder()
			.bibId("bib-id")
			.holdingId("holding-id")
			.localId("item-id")
			.build();
	}
}
