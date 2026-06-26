package org.olf.dcb.request.lifecycle.placement;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
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
}
