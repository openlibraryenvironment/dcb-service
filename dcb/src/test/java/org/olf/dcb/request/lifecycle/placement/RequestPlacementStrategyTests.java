package org.olf.dcb.request.lifecycle.placement;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.BorrowingAgencyService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.StrategyType;

import reactor.core.publisher.Mono;

class RequestPlacementStrategyTests {
	@Test
	void supplyingResolverDefaultsToImperativeStrategy() {
		final var imperativeStrategy = mock(ImperativeSupplyingAgencyRequestStrategy.class);
		final var resolver = new SupplyingAgencyRequestStrategyResolver(imperativeStrategy);
		final var context = new RequestWorkflowContext();

		final var strategy = resolver.resolve(context, LifecycleOperation.PLACE_REQUEST);

		assertThat(strategy, sameInstance(imperativeStrategy));
	}

	@Test
	void borrowingResolverDefaultsToImperativeStrategyForPlaceAndRevise() {
		final var imperativeStrategy = mock(ImperativeBorrowingAgencyRequestStrategy.class);
		final var resolver = new BorrowingAgencyRequestStrategyResolver(imperativeStrategy);
		final var context = new RequestWorkflowContext();

		assertThat(resolver.resolve(context, LifecycleOperation.PLACE_REQUEST),
			sameInstance(imperativeStrategy));
		assertThat(resolver.resolve(context, LifecycleOperation.REVISE_REQUEST),
			sameInstance(imperativeStrategy));
	}

	@Test
	void imperativeSupplyingStrategyDelegatesToSupplyingAgencyService() {
		final var supplyingAgencyService = mock(SupplyingAgencyService.class);
		final var strategy = new ImperativeSupplyingAgencyRequestStrategy(
			supplyingAgencyService);
		final var patronRequest = new PatronRequest();
		final var supplierRequest = new SupplierRequest();
		final var context = new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setSupplierRequest(supplierRequest);

		when(supplyingAgencyService.placePatronRequestAtSupplyingAgency(
			patronRequest))
			.thenReturn(Mono.just(patronRequest));

		final var result = singleValueFrom(strategy.place(context));

		assertThat(strategy.type(), is(StrategyType.IMPERATIVE));
		assertThat(result.patronRequest(), sameInstance(patronRequest));
		assertThat(result.supplierRequest(), sameInstance(supplierRequest));
		verify(supplyingAgencyService).placePatronRequestAtSupplyingAgency(
			patronRequest);
	}

	@Test
	void imperativeBorrowingStrategyDelegatesPlaceToBorrowingAgencyService() {
		final var borrowingAgencyService = mock(BorrowingAgencyService.class);
		final var strategy = new ImperativeBorrowingAgencyRequestStrategy(
			borrowingAgencyService);
		final var context = new RequestWorkflowContext();
		final var patronRequest = new PatronRequest();

		when(borrowingAgencyService.placePatronRequestAtBorrowingAgency(context))
			.thenReturn(Mono.just(patronRequest));

		final var result = singleValueFrom(strategy.place(context));

		assertThat(strategy.type(), is(StrategyType.IMPERATIVE));
		assertThat(result.patronRequest(), sameInstance(patronRequest));
		verify(borrowingAgencyService).placePatronRequestAtBorrowingAgency(
			context);
	}

	@Test
	void imperativeBorrowingStrategyDelegatesReviseToBorrowingAgencyService() {
		final var borrowingAgencyService = mock(BorrowingAgencyService.class);
		final var strategy = new ImperativeBorrowingAgencyRequestStrategy(
			borrowingAgencyService);
		final var context = new RequestWorkflowContext();
		final var patronRequest = new PatronRequest();

		when(borrowingAgencyService.updatePatronRequestAtBorrowingAgency(context))
			.thenReturn(Mono.just(patronRequest));

		final var result = singleValueFrom(strategy.revise(context));

		assertThat(strategy.type(), is(StrategyType.IMPERATIVE));
		assertThat(result.patronRequest(), sameInstance(patronRequest));
		verify(borrowingAgencyService).updatePatronRequestAtBorrowingAgency(
			context);
	}

	@Test
	void supplyingProjectorAppliesCompatibilityEvidence() {
		final var patronRequest = new PatronRequest();
		final var supplierRequest = new SupplierRequest();
		final var context = new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setSupplierRequest(supplierRequest);
		final var projector = new SupplyingAgencyRequestProjector();

		projector.apply(context, new SupplyingAgencyRequestResult(
			null,
			null,
			"supplier-host",
			"supplier-request-1",
			"PLACED",
			"placed",
			"supplier-item-1",
			"supplier-barcode-1"));

		assertThat(context.getPatronRequest(), sameInstance(patronRequest));
		assertThat(context.getSupplierRequest(), sameInstance(supplierRequest));
		assertThat(patronRequest.getStatus(),
			is(PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY));
		assertThat(supplierRequest.getLocalId(), is("supplier-request-1"));
		assertThat(supplierRequest.getLocalStatus(), is("PLACED"));
		assertThat(supplierRequest.getRawLocalStatus(), is("placed"));
		assertThat(supplierRequest.getLocalItemId(), is("supplier-item-1"));
		assertThat(supplierRequest.getLocalItemBarcode(),
			is("supplier-barcode-1"));
	}

	@Test
	void borrowingProjectorAppliesCompatibilityEvidenceForRealArtifacts() {
		final var patronRequest = new PatronRequest();
		final var context = new RequestWorkflowContext()
			.setPatronRequest(patronRequest);
		final var projector = new BorrowingAgencyRequestProjector();

		projector.apply(context, new BorrowingAgencyRequestResult(
			null,
			"borrower-host",
			"borrower-request-1",
			"CONFIRMED",
			"confirmed",
			"virtual-bib-1",
			"virtual-item-1",
			"AVAILABLE",
			true,
			true));

		assertThat(context.getPatronRequest(), sameInstance(patronRequest));
		assertThat(patronRequest.getStatus(),
			is(PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY));
		assertThat(patronRequest.getLocalRequestId(), is("borrower-request-1"));
		assertThat(patronRequest.getLocalRequestStatus(), is("CONFIRMED"));
		assertThat(patronRequest.getRawLocalRequestStatus(), is("confirmed"));
		assertThat(patronRequest.getLocalBibId(), is("virtual-bib-1"));
		assertThat(patronRequest.getLocalItemId(), is("virtual-item-1"));
		assertThat(patronRequest.getLocalItemStatus(), is("AVAILABLE"));
	}

	@Test
	void borrowingProjectorDoesNotFabricateVirtualArtifactFields() {
		final var patronRequest = new PatronRequest()
			.setLocalBibId("existing-bib")
			.setLocalItemId("existing-item")
			.setLocalItemStatus("EXISTING");
		final var context = new RequestWorkflowContext()
			.setPatronRequest(patronRequest);
		final var projector = new BorrowingAgencyRequestProjector();

		projector.apply(context, new BorrowingAgencyRequestResult(
			null,
			"borrower-host",
			"borrower-request-1",
			"CONFIRMED",
			"confirmed",
			"declarative-bib-evidence",
			"declarative-item-evidence",
			"DECLARED",
			false,
			false));

		assertThat(patronRequest.getStatus(),
			is(PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY));
		assertThat(patronRequest.getLocalRequestId(), is("borrower-request-1"));
		assertThat(patronRequest.getLocalBibId(), is("existing-bib"));
		assertThat(patronRequest.getLocalItemId(), is("existing-item"));
		assertThat(patronRequest.getLocalItemStatus(), is("EXISTING"));
	}

	@Test
	void borrowingProjectorCanProjectRequestEvidenceWithoutArtifacts() {
		final var patronRequest = new PatronRequest();
		final var context = new RequestWorkflowContext()
			.setPatronRequest(patronRequest);
		final var projector = new BorrowingAgencyRequestProjector();

		projector.apply(context, new BorrowingAgencyRequestResult(
			null,
			"borrower-host",
			"borrower-request-1",
			"ACCEPTED",
			"accepted",
			null,
			null,
			null,
			false,
			false));

		assertThat(patronRequest.getStatus(),
			is(PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY));
		assertThat(patronRequest.getLocalRequestId(), is("borrower-request-1"));
		assertThat(patronRequest.getLocalRequestStatus(), is("ACCEPTED"));
		assertThat(patronRequest.getLocalBibId(), nullValue());
		assertThat(patronRequest.getLocalItemId(), nullValue());
	}
}
