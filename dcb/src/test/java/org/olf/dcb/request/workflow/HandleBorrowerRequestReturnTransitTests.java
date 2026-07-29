package org.olf.dcb.request.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.olf.dcb.core.model.WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.CheckInItemCommand;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.SupplierReturnExpectedNotifier;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class HandleBorrowerRequestReturnTransitTests {
	@Test
	void notifiesSupplierAndMovesStandardRequestToReturnTransit() {
		final var dependencies = dependencies();
		final var context = standardContext();
		when(dependencies.notifier().notifyExpectedReturn(context))
			.thenReturn(Mono.just(context));

		StepVerifier.create(dependencies.transition().attempt(context))
			.expectNext(context)
			.verifyComplete();

		assertThat(context.getPatronRequest().getStatus(),
			is(PatronRequest.Status.RETURN_TRANSIT));
		assertThat(dependencies.transition().isApplicableFor(context), is(false));
		verify(dependencies.notifier()).notifyExpectedReturn(context);
		verifyNoInteractions(dependencies.hostLmsService());
	}

	@Test
	void propagatesSupplierNotificationFailure() {
		final var dependencies = dependencies();
		final var context = standardContext();
		final var failure = new IllegalStateException("supplier unavailable");
		when(dependencies.notifier().notifyExpectedReturn(context))
			.thenReturn(Mono.error(failure));

		StepVerifier.create(dependencies.transition().attempt(context))
			.expectErrorMatches(error -> error == failure)
			.verify();

		verify(dependencies.notifier()).notifyExpectedReturn(context);
	}

	@Test
	void standardApplicabilityRequiresLoanedAndReturnedItemEvidence() {
		final var transition = dependencies().transition();
		final var context = standardContext();

		assertThat(transition.isApplicableFor(context), is(true));
		context.getPatronRequest().setLocalItemStatus("LOANED");
		assertThat(transition.isApplicableFor(context), is(false));
		context.getPatronRequest().setLocalItemStatus(HostLmsItem.ITEM_AVAILABLE);
		assertThat(transition.isApplicableFor(context), is(true));
		context.getPatronRequest().setStatus(PatronRequest.Status.COMPLETED);
		assertThat(transition.isApplicableFor(context), is(false));
	}

	@Test
	void pickupAnywhereChecksInAtBorrowerWithoutNotifyingSupplier() {
		final var dependencies = dependencies();
		final var context = standardContext();
		context.getPatronRequest().setActiveWorkflow(PICKUP_ANYWHERE_WORKFLOW);
		context.setPatronSystemCode("borrower-host");
		final var hostLmsClient = mock(HostLmsClient.class);
		when(dependencies.hostLmsService().getClientFor("borrower-host"))
			.thenReturn(Mono.just(hostLmsClient));
		when(hostLmsClient.checkInItem(any(CheckInItemCommand.class)))
			.thenReturn(Mono.empty());

		StepVerifier.create(dependencies.transition().attempt(context))
			.expectNext(context)
			.verifyComplete();

		assertThat(context.getPatronRequest().getStatus(),
			is(PatronRequest.Status.RETURN_TRANSIT));
		verify(hostLmsClient).checkInItem(any(CheckInItemCommand.class));
		verify(dependencies.notifier(), never()).notifyExpectedReturn(any());
	}

	private static RequestWorkflowContext standardContext() {
		final var patronRequest = PatronRequest.builder()
			.status(PatronRequest.Status.LOANED)
			.localRequestId("borrower-request")
			.localItemId("borrower-item")
			.localItemStatus(HostLmsItem.ITEM_TRANSIT)
			.build();
		final var supplierRequest = SupplierRequest.builder()
			.localItemBarcode("supplier-barcode")
			.build();
		return new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setSupplierRequest(supplierRequest);
	}

	private static Dependencies dependencies() {
		final var hostLmsService = mock(HostLmsService.class);
		final var notifier = mock(SupplierReturnExpectedNotifier.class);
		return new Dependencies(
			hostLmsService,
			notifier,
			new HandleBorrowerRequestReturnTransit(
				hostLmsService,
				mock(PatronRequestAuditService.class),
				notifier));
	}

	private record Dependencies(
		HostLmsService hostLmsService,
		SupplierReturnExpectedNotifier notifier,
		HandleBorrowerRequestReturnTransit transition) {
	}
}
