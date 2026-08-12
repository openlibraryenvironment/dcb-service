package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.client.HttpClient;
import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.types.AlmaBib;
import services.k_int.interaction.alma.types.CodeValuePair;
import services.k_int.interaction.alma.types.holdings.AlmaHolding;
import services.k_int.interaction.alma.types.holdings.AlmaHoldings;
import services.k_int.interaction.alma.types.items.AlmaHoldingData;
import services.k_int.interaction.alma.types.items.AlmaItem;
import services.k_int.interaction.alma.types.items.AlmaItemData;
import services.k_int.interaction.alma.types.items.AlmaItems;
import services.k_int.interaction.alma.types.userRequest.AlmaRequests;

/**
 * What an Alma item says about where it lives.
 * <p>
 * Alma calls the owning branch a "library" and the shelf it sits on a "location", and
 * the mapper used the second as if it were the first. Every library on a tenant draws
 * its shelving locations from the same vocabulary, so on a shared Alma that made
 * location-to-agency mapping an unanswerable question: map "STACKS" to a library.
 */
@TestInstance(PER_CLASS)
class AlmaItemMappingTests {
	private AlmaApiClient apiClient;
	private AlmaHostLmsClient client;

	@BeforeEach
	void beforeEach() {
		// A real DataHostLms, not a mock of the HostLms interface: locationForLibraryCode
		// casts to DataHostLms, and a mock of the interface fails that cast. The failure
		// is invisible - getItems swallows it through onErrorContinue and the item simply
		// vanishes from availability.
		final var hostLms = DataHostLms.builder()
			.id(UUID.randomUUID())
			.code("ALMA")
			.clientConfig(Map.of(
				"alma-url", "https://api-eu.hosted.exlibrisgroup.com",
				"apikey", "any-key"))
			.build();

		apiClient = mock(AlmaApiClient.class);

		final var clientFactory = mock(AlmaClientFactory.class);
		when(clientFactory.createClientFor(hostLms)).thenReturn(apiClient);

		// Enrichment has its own tests; here it passes items through so the assertions
		// are about what the Alma mapper produced and nothing else
		final var locationToAgency = mock(LocationToAgencyMappingService.class);
		when(locationToAgency.enrichItemAgencyFromLocation(any(), any()))
			.thenAnswer(invocation -> Mono.just(invocation.<Item>getArgument(0)));

		final var materialTypeToItemType = mock(MaterialTypeToItemTypeMappingService.class);
		when(materialTypeToItemType.enrichItemWithMappedItemType(any()))
			.thenAnswer(invocation -> Mono.just(invocation.<Item>getArgument(0)));

		client = new AlmaHostLmsClient(hostLms, mock(HttpClient.class), clientFactory,
			mock(ReferenceValueMappingService.class), materialTypeToItemType, locationToAgency,
			mock(ConversionService.class), mock(LocationService.class),
			mock(HostLmsService.class), mock(ConsortiumService.class));
	}

	@Test
	void shouldMapTheOwningLibraryAsTheItemLocation() {
		givenItem("MAIN-LIB", "STACKS");

		final var item = firstItem();

		assertThat("The branch that owns the item, not the shelf it sits on",
			item.getLocationCode(), is("MAIN-LIB"));

		assertThat("The shelving location is kept, but as what it is",
			item.getShelvingLocation(), is("STACKS"));
	}

	@Test
	void shouldNotFallBackToTheShelvingLocationWhenThereIsNoLibrary() {
		// Falling back would reintroduce the bug for exactly the items that trigger it.
		// No location at all is honest and gets the item dropped by the hasAgency filter,
		// which is better than attributing it to whichever library owns "STACKS".
		givenItem(null, "STACKS");

		final var item = firstItem();

		assertThat(item.getLocation(), is(nullValue()));
		assertThat(item.getShelvingLocation(), is("STACKS"));
	}

	@Test
	void shouldRecordTheSystemTheItemCameFrom() {
		// The only record of where an item came from when its location does not resolve,
		// which is the case an operator has to diagnose on a shared system
		givenItem("MAIN-LIB", "STACKS");

		assertThat(firstItem().getSourceHostLmsCode(), is("ALMA"));
	}

	private Item firstItem() {
		final var items = client.getItems(BibRecord.builder()
			.sourceRecordId("99123")
			.build()).block();

		assertThat("Expected exactly one mapped item", items.size(), is(1));

		return items.get(0);
	}

	private void givenItem(String libraryCode, String shelvingLocationCode) {
		when(apiClient.retrieveHoldingsList("99123"))
			.thenReturn(Mono.just(AlmaHoldings.builder()
				.holdings(List.of(AlmaHolding.builder().holdingId("22456").build()))
				.build()));

		when(apiClient.retrieveItemsList("99123", "22456"))
			.thenReturn(Mono.just(AlmaItems.builder()
				.items(List.of(AlmaItem.builder()
					.bibData(AlmaBib.builder().mmsId("99123").build())
					.holdingData(AlmaHoldingData.builder().holdingId("22456").build())
					.itemData(AlmaItemData.builder()
						.pid("23789")
						.barcode("6747664")
						.baseStatus(CodeValuePair.builder().value("1").build())
						.physicalMaterialType(CodeValuePair.builder().value("BOOK").build())
						.library(libraryCode != null
							? CodeValuePair.builder().value(libraryCode).build()
							: null)
						.location(shelvingLocationCode != null
							? CodeValuePair.builder().value(shelvingLocationCode).build()
							: null)
						.build())
					.build()))
				.build()));

		when(apiClient.retrieveItemRequests(any(), any(), any()))
			.thenReturn(Mono.just(AlmaRequests.builder().recordCount(0).build()));
	}
}
