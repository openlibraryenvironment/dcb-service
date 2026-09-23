package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.types.AlmaBib;
import services.k_int.interaction.alma.types.CodeValuePair;
import services.k_int.interaction.alma.types.items.AlmaHoldingData;
import services.k_int.interaction.alma.types.items.AlmaItem;
import services.k_int.interaction.alma.types.items.AlmaItemData;
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
	private LocationToAgencyMappingService locationToAgency;
	private MaterialTypeToItemTypeMappingService materialTypeToItemType;
	private AlmaHostLmsClient client;

	@BeforeEach
	void beforeEach() {
		// A real DataHostLms, not a mock of the HostLms interface: locationForLibraryCode
		// casts to DataHostLms, and a mock of the interface fails that cast.
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
		locationToAgency = mock(LocationToAgencyMappingService.class);
		when(locationToAgency.enrichItemAgencyFromLocation(any(), any()))
			.thenAnswer(invocation -> Mono.just(invocation.<Item>getArgument(0)));

		materialTypeToItemType = mock(MaterialTypeToItemTypeMappingService.class);
		when(materialTypeToItemType.enrichItemWithMappedItemType(any()))
			.thenAnswer(invocation -> Mono.just(invocation.<Item>getArgument(0)));

		client = new AlmaHostLmsClient(hostLms, mock(HttpClient.class), clientFactory,
			mock(ReferenceValueMappingService.class), materialTypeToItemType, locationToAgency,
			mock(ConversionService.class), mock(LocationService.class),
			mock(HostLmsService.class), mock(ConsortiumService.class));
	}

	@Test
	void shouldMapTheOwningLibraryAsTheItemLocation() {
		givenItems(almaItem("23789", "MAIN-LIB", "STACKS", "BOOK"));

		final var item = onlyItem();

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
		givenItems(almaItem("23789", null, "STACKS", "BOOK"));

		final var item = onlyItem();

		assertThat(item.getLocation(), is(nullValue()));
		assertThat(item.getShelvingLocation(), is("STACKS"));
	}

	@Test
	void shouldRecordTheSystemTheItemCameFrom() {
		// The only record of where an item came from when its location does not resolve,
		// which is the case an operator has to diagnose on a shared system
		givenItems(almaItem("23789", "MAIN-LIB", "STACKS", "BOOK"));

		assertThat(onlyItem().getSourceHostLmsCode(), is("ALMA"));
	}

	@Test
	void shouldReportAnUnknownHoldCountRatherThanZero() {
		givenItems(almaItem("23789", "MAIN-LIB", "STACKS", "BOOK"));

		when(apiClient.retrieveItemRequests(any(), any(), any()))
			.thenReturn(Mono.error(new RuntimeException("Alma is unavailable")));

		assertThat(onlyItem().getHoldCount(), is(nullValue()));
	}

	@Test
	void shouldKeepAnItemWithNoMaterialTypeRatherThanLoseItToAnException() {
		// Reading the material type unguarded threw, and the whole item became a placeholder
		// with no location and therefore no agency - which availability drops before anything
		// is audited. An absent code is the mapping service's to report, not a reason to
		// discard everything else known about the item.
		givenItems(
			almaItem("23789", "MAIN-LIB", "STACKS", "BOOK"),
			almaItem("23790", "MAIN-LIB", "STACKS", null));

		final var items = items();

		assertThat(items, hasSize(2));

		final var noMaterialType = items.get(1);

		assertThat(noMaterialType.getLocalId(), is("23790"));
		assertThat(noMaterialType.getLocalItemTypeCode(), is(nullValue()));
		assertThat(noMaterialType.getLocationCode(), is("MAIN-LIB"));
	}

	@Test
	void shouldKeepTheLocationOfAnItemWhoseItemTypeCannotBeMapped() {
		givenItems(almaItem("23789", "MAIN-LIB", "STACKS", "BOOK"));

		doReturn(Mono.error(new RuntimeException("No mapping for BOOK")))
			.when(materialTypeToItemType)
			.enrichItemWithMappedItemType(any());

		final var item = onlyItem();

		// Location intact, so the item reaches the report and the reason reaches the audit
		assertThat(item.getLocationCode(), is("MAIN-LIB"));
		assertThat(item.getIsRequestable(), is(false));
		assertThat(item.getDecisionLogEntries(),
			contains("Could not map this Alma item: No mapping for BOOK"));
	}

	@Test
	void shouldReturnAnItemWhoseEnrichmentFailsWithTheReasonAlongsideTheOthers() {
		givenItems(
			almaItem("23789", "MAIN-LIB", "STACKS", "BOOK"),
			almaItem("23790", "BRANCH", "STACKS", "BOOK"));

		// doReturn, because when(...) would invoke the pass-through answer above with a null item
		doReturn(Mono.error(new RuntimeException("No mapping for BRANCH")))
			.when(locationToAgency)
			.enrichItemAgencyFromLocation(argThat(item -> item != null && "23790".equals(item.getLocalId())), any());

		final var items = items();

		assertThat(items, hasSize(2));
		assertThat(items.get(1).getDecisionLogEntries(),
			contains("Could not map this Alma item: No mapping for BRANCH"));
	}

	@Test
	void shouldReportAFailureToRetrieveTheItemsAsAnError() {
		when(apiClient.retrieveAllItems("99123"))
			.thenReturn(Flux.error(new RuntimeException("Alma is unavailable")));

		assertThrows(RuntimeException.class, this::items);
	}

	@Test
	void shouldMapTheLoanDueDateAlmaReturnsWithTheItem() {
		final var almaItem = almaItem("23789", "MAIN-LIB", "STACKS", "BOOK");
		almaItem.getItemData().setDueDate("2026-10-01T22:59:00Z");

		givenItems(almaItem);

		assertThat(onlyItem().getDueDate(), is(java.time.Instant.parse("2026-10-01T22:59:00Z")));
	}

	@Test
	void shouldLeaveTheDueDateUnknownWhenAlmaSendsOneItCannotRead() {
		final var almaItem = almaItem("23789", "MAIN-LIB", "STACKS", "BOOK");
		almaItem.getItemData().setDueDate("10/01/2026");

		givenItems(almaItem);

		final var item = onlyItem();

		assertThat(item.getDueDate(), is(nullValue()));
		assertThat(item.getLocationCode(), is("MAIN-LIB"));
	}

	private Item onlyItem() {
		final var items = items();

		assertThat("Expected exactly one mapped item", items.size(), is(1));

		return items.get(0);
	}

	private List<Item> items() {
		return client.getItems(BibRecord.builder()
			.sourceRecordId("99123")
			.build()).block();
	}

	private void givenItems(AlmaItem... almaItems) {
		when(apiClient.retrieveAllItems("99123")).thenReturn(Flux.just(almaItems));

		when(apiClient.retrieveItemRequests(any(), any(), any()))
			.thenReturn(Mono.just(AlmaRequests.builder().recordCount(0).build()));
	}

	private static AlmaItem almaItem(String pid, String libraryCode, String shelvingLocationCode,
		String materialType) {

		return AlmaItem.builder()
			.bibData(AlmaBib.builder().mmsId("99123").build())
			.holdingData(AlmaHoldingData.builder().holdingId("22456").build())
			.itemData(AlmaItemData.builder()
				.pid(pid)
				.barcode("6747664")
				.baseStatus(CodeValuePair.builder().value("1").build())
				.physicalMaterialType(materialType != null
					? CodeValuePair.builder().value(materialType).build()
					: null)
				.library(libraryCode != null
					? CodeValuePair.builder().value(libraryCode).build()
					: null)
				.location(shelvingLocationCode != null
					? CodeValuePair.builder().value(shelvingLocationCode).build()
					: null)
				.build())
			.build();
	}
}
