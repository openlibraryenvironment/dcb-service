package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatusCode;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.types.AlmaBib;
import services.k_int.interaction.alma.types.CodeValuePair;
import services.k_int.interaction.alma.types.items.AlmaHoldingData;
import services.k_int.interaction.alma.types.items.AlmaItem;
import services.k_int.interaction.alma.types.items.AlmaItemData;
import services.k_int.interaction.alma.types.userRequest.AlmaRequests;

class AlmaItemStatusTests {
	private AlmaApiClient apiClient;
	private AlmaHostLmsClient client;

	@BeforeEach
	void beforeEach() {
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

		final var locationToAgency = mock(LocationToAgencyMappingService.class);
		when(locationToAgency.enrichItemAgencyFromLocation(any(), any()))
			.thenAnswer(invocation -> Mono.just(invocation.<Item>getArgument(0)));

		final var materialTypeToItemType = mock(MaterialTypeToItemTypeMappingService.class);
		when(materialTypeToItemType.enrichItemWithMappedItemType(any()))
			.thenAnswer(invocation -> Mono.just(invocation.<Item>getArgument(0)));

		client = new AlmaHostLmsClient(hostLms, clientFactory,
			mock(ReferenceValueMappingService.class), materialTypeToItemType, locationToAgency,
			mock(ConsortiumService.class));

		when(apiClient.retrieveItemRequests(any(), any(), any()))
			.thenReturn(Mono.just(AlmaRequests.builder().recordCount(0).build()));
	}

	// Every code in Alma's PROCESSTYPE table, plus no process type at all
	@ParameterizedTest(name = "base {0}, process {1} -> {2}")
	@CsvSource(nullValues = "none", value = {
		"1, none, AVAILABLE",
		"0, none, UNAVAILABLE",
		"0, LOAN, CHECKED_OUT",
		"1, REQUESTED, AVAILABLE",
		"0, REQUESTED, UNAVAILABLE",
		"0, HOLDSHELF, UNAVAILABLE",
		"0, TRANSIT, UNAVAILABLE",
		"0, TRANSIT_TO_REMOTE_STORAGE, UNAVAILABLE",
		"0, ILL, UNAVAILABLE",
		"0, MISSING, UNAVAILABLE",
		"0, LOST_LOAN, UNAVAILABLE",
		"0, LOST_LOAN_AND_PAID, UNAVAILABLE",
		"0, LOST_ILL, UNAVAILABLE",
		"0, CLAIM_RETURNED_LOAN, UNAVAILABLE",
		"0, WORK_ORDER_DEPARTMENT, UNAVAILABLE",
		"0, ACQ, UNAVAILABLE",
		"0, TECHNICAL, UNAVAILABLE",
	})
	void shouldDeriveTheStatusFromBaseStatusAndProcessType(String baseStatus,
		String processType, ItemStatusCode expected) {

		givenItems(almaItem(baseStatus, processType));

		assertThat(onlyItem().getStatus().getCode(), is(expected));
	}

	@Test
	void shouldKeepAlmasRawStatusOnTheItem() {
		givenItems(almaItem("0", "LOAN"));

		final var raw = onlyItem().getRawDataValues();

		assertThat(raw, hasEntry("baseStatus", "0"));
		assertThat(raw, hasEntry("processType", "LOAN"));
	}

	@Test
	void shouldRecordNoProcessTypeWhenAlmaSendsNone() {
		givenItems(almaItem("1", null));

		assertThat(onlyItem().getRawDataValues(), is(Map.of("baseStatus", "1")));
		assertThat(onlyItem().getDecisionLogEntries(), is(empty()));
	}

	@Test
	void shouldTreatAProcessTypeItDoesNotKnowAsUnavailableAndSaySo() {
		givenItems(almaItem("1", "SOMETHING_NEW"));

		final var item = onlyItem();

		assertThat(item.getStatus().getCode(), is(ItemStatusCode.UNAVAILABLE));
		assertThat(item.getDecisionLogEntries(), contains(containsString("SOMETHING_NEW")));
	}

	@Test
	void shouldNotReadAnItemGoingToRemoteStorageAsInTransitWhenTracking() {
		// Mapped to transit, this would move a request to PICKUP_TRANSIT for an item that
		// is going into storage, not to a borrower
		when(apiClient.retrieveItem("99123", "22456", "23789"))
			.thenReturn(Mono.just(almaItem("0", "TRANSIT_TO_REMOTE_STORAGE")));

		final var item = client.getItem(HostLmsItem.builder()
			.localId("23789")
			.bibId("99123")
			.holdingId("22456")
			.build()).block();

		assertThat(item.getStatus(), is(not(HostLmsItem.ITEM_TRANSIT)));
	}

	@Test
	void shouldLeaveTheRawValuesEmptyForAnItemWithNoStatusAtAll() {
		givenItems(almaItem(null, null));

		assertThat(onlyItem().getRawDataValues(), is(anEmptyMap()));
		assertThat(onlyItem().getStatus().getCode(), is(ItemStatusCode.UNAVAILABLE));
	}

	private Item onlyItem() {
		final List<Item> items = client.getItems(BibRecord.builder().sourceRecordId("99123").build()).block();

		assertThat("Expected exactly one mapped item", items.size(), is(1));

		return items.get(0);
	}

	private void givenItems(AlmaItem... almaItems) {
		when(apiClient.retrieveAllItems("99123")).thenReturn(Flux.just(almaItems));
	}

	private static AlmaItem almaItem(String baseStatus, String processType) {
		return AlmaItem.builder()
			.bibData(AlmaBib.builder().mmsId("99123").build())
			.holdingData(AlmaHoldingData.builder().holdingId("22456").build())
			.itemData(AlmaItemData.builder()
				.pid("23789")
				.barcode("6747664")
				.baseStatus(baseStatus != null ? CodeValuePair.builder().value(baseStatus).build() : null)
				.process_type(processType != null ? CodeValuePair.builder().value(processType).build() : null)
				.library(CodeValuePair.builder().value("MAIN-LIB").build())
				.physicalMaterialType(CodeValuePair.builder().value("BOOK").build())
				.build())
			.build();
	}
}
