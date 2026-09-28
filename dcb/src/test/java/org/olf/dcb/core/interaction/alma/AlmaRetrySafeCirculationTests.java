package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.interaction.CheckoutItemCommand;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.olf.dcb.core.interaction.HostLmsClient.CanonicalItemState;

import reactor.core.publisher.Mono;
import services.k_int.interaction.alma.AlmaApiClient;
import services.k_int.interaction.alma.types.AlmaBib;
import services.k_int.interaction.alma.types.CodeValuePair;
import services.k_int.interaction.alma.types.items.AlmaHoldingData;
import services.k_int.interaction.alma.types.items.AlmaItem;
import services.k_int.interaction.alma.types.items.AlmaItemData;
import services.k_int.interaction.alma.types.items.AlmaItemLoan;
import services.k_int.interaction.alma.types.items.AlmaItemLoans;
import services.k_int.interaction.alma.types.userRequest.AlmaRequests;

/**
 * Supplier circulation calls that Alma applies but does not answer in time.
 * <p>
 * Observed on an Alma sandbox: a scan-in reached the hold shelf 52 seconds in, DCB's 60 second
 * read timeout fired first, and the request went to ERROR for work Alma had done. DCB also
 * records no holding id for a supplier item, and Alma's requests call refuses a missing one.
 */
@TestInstance(PER_CLASS)
class AlmaRetrySafeCirculationTests {
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

		client = new AlmaHostLmsClient(hostLms, clientFactory,
			mock(ReferenceValueMappingService.class), mock(MaterialTypeToItemTypeMappingService.class),
			mock(LocationToAgencyMappingService.class), mock(ConsortiumService.class));
	}

	@Test
	void shouldCountHoldsUsingTheHoldingAlmaReportsForASupplierItem() {
		when(almaApi.retrieveItem("bib-1", null, "item-1")).thenReturn(Mono.just(item("0", "HOLDSHELF")));
		when(almaApi.retrieveItemRequests("bib-1", "holding-1", "item-1"))
			.thenReturn(Mono.just(AlmaRequests.builder().recordCount(2).build()));

		final var item = client.getItem(HostLmsItem.builder()
			.bibId("bib-1").localId("item-1").build()).block();

		// "holdings/null/…/requests" answered 400 on every poll, so a waiting patron was never seen
		assertThat(item.getHoldCount(), is(2));
		assertThat(item.getHoldingId(), is("holding-1"));
	}

	@Test
	void shouldCountHoldsUsingTheBibAlmaReportsWhenTheCallerHasNone() {
		when(almaApi.retrieveItem(null, null, "item-1")).thenReturn(Mono.just(item("1", "LOAN")));
		when(almaApi.retrieveItemRequests("bib-1", "holding-1", "item-1"))
			.thenReturn(Mono.just(AlmaRequests.builder().recordCount(0).build()));

		final var item = client.getItem(HostLmsItem.builder().localId("item-1").build()).block();

		// "bibs/null/…/requests" answered 400, the count read as unknown, and renewal was prevented
		assertThat(item.getHoldCount(), is(0));
		assertThat(item.getBibId(), is("bib-1"));
	}

	@Test
	void shouldTreatAScanAlmaAppliedButDidNotAnswerAsDone() {
		when(almaApi.retrieveItem("bib-1", null, "item-1")).thenReturn(Mono.just(item("0", "TRANSIT")));
		when(almaApi.scanIn(any())).thenReturn(Mono.error(new RuntimeException("Read Timeout")));
		when(almaApi.retrieveItem("bib-1", "holding-1", "item-1")).thenReturn(Mono.just(item("0", "HOLDSHELF")));

		final var result = client.updateItemStatus(supplierItem(), CanonicalItemState.RECEIVED).block();

		assertThat(result, is("OK"));

		final var query = ArgumentCaptor.forClass(AlmaHostLmsClient.ScanInQuery.class);
		verify(almaApi).scanIn(query.capture());
		assertThat("The scan goes to the item's real holding, not holdings/null",
			query.getValue().holding_id(), is("holding-1"));
	}

	@Test
	void shouldKeepTheScanFailureWhenTheItemHasNotMoved() {
		when(almaApi.retrieveItem("bib-1", null, "item-1")).thenReturn(Mono.just(item("0", "TRANSIT")));
		when(almaApi.scanIn(any())).thenReturn(Mono.error(new RuntimeException("Read Timeout")));
		when(almaApi.retrieveItem("bib-1", "holding-1", "item-1")).thenReturn(Mono.just(item("0", "TRANSIT")));

		final var error = client.updateItemStatus(supplierItem(), CanonicalItemState.RECEIVED)
			.map(ok -> (Throwable) new IllegalStateException("the scan was reported done"))
			.onErrorResume(Mono::just)
			.block();

		assertThat(error.getMessage(), is("Read Timeout"));
	}

	@Test
	void shouldTreatALoanAlmaCreatedButDidNotAnswerAsDone() {
		refuseLoanWith(List.of(AlmaItemLoan.builder().loanId("loan-1").itemId("item-1").build()));

		final var result = client.checkOutItemToPatron(checkout()).block();

		assertThat(result, is("OK"));
	}

	@Test
	void shouldKeepTheLoanFailureWhenThePatronHasNoSuchLoan() {
		refuseLoanWith(List.of(AlmaItemLoan.builder().loanId("loan-2").itemId("another-item").build()));

		final var error = client.checkOutItemToPatron(checkout())
			.map(ok -> (Throwable) new IllegalStateException("the loan was reported done"))
			.onErrorResume(Mono::just)
			.block();

		assertThat(error, instanceOf(RuntimeException.class));
		assertThat(error.getMessage(), is("Read Timeout"));
	}

	private void refuseLoanWith(List<AlmaItemLoan> existingLoans) {
		when(almaApi.retrieveItemBarcodeOnly("barcode-1")).thenReturn(Mono.just(item("1", null)));
		when(almaApi.createUserLoan(eq("patron-1"), eq("item-1"), any()))
			.thenReturn(Mono.error(new RuntimeException("Read Timeout")));
		when(almaApi.retrieveUserLoansPage(anyString(), eq(0)))
			.thenReturn(Mono.just(AlmaItemLoans.builder().loans(existingLoans).build()));
	}

	private static CheckoutItemCommand checkout() {
		return CheckoutItemCommand.builder()
			.itemId("item-1")
			.itemBarcode("barcode-1")
			.patronId("patron-1")
			.localRequestId("request-1")
			.build();
	}

	private static HostLmsItem supplierItem() {
		// As a supplier request holds it: no holding id
		return HostLmsItem.builder().bibId("bib-1").localId("item-1").build();
	}

	private static AlmaItem item(String baseStatus, String processType) {
		return AlmaItem.builder()
			.bibData(AlmaBib.builder().mmsId("bib-1").build())
			.holdingData(AlmaHoldingData.builder().holdingId("holding-1").build())
			.itemData(AlmaItemData.builder()
				.pid("item-1")
				.library(CodeValuePair.builder().value("dc").build())
				.baseStatus(CodeValuePair.builder().value(baseStatus).build())
				.process_type(processType != null ? CodeValuePair.builder().value(processType).build() : null)
				.build())
			.build();
	}
}
