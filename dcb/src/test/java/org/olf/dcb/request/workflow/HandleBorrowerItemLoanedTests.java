package org.olf.dcb.request.workflow;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.*;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import services.k_int.test.mockserver.MockServerMicronautTest;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.olf.dcb.core.interaction.HostLmsItem.*;
import static org.olf.dcb.core.model.PatronRequest.Status.*;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.storage.PatronRequestRepository;
import reactor.core.publisher.Mono;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class HandleBorrowerItemLoanedTests {
	@Inject
	private HandleBorrowerItemLoaned handleBorrowerItemLoaned;

	@Test
	void shouldBeApplicableForLocalItemStatusLoaned() {
		// Arrange
		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.status(READY_FOR_PICKUP)
			.localItemStatus(ITEM_LOANED)
			.build();

		final var ctx = new RequestWorkflowContext();
		ctx.setPatronRequest(patronRequest);

		// Act
		final var applicable = handleBorrowerItemLoaned.isApplicableFor(ctx);

		// Assert
		assertThat("Should be applicable for local item status loaned",
			applicable, is(true));
	}

	@Test
	void shouldBeApplicableForPickupItemStatusLoaned() {
		// Arrange
		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.status(READY_FOR_PICKUP)
			.pickupItemStatus(ITEM_LOANED)
			.build();

		final var ctx = new RequestWorkflowContext();
		ctx.setPatronRequest(patronRequest);

		// Act
		final var applicable = handleBorrowerItemLoaned.isApplicableFor(ctx);

		// Assert
		assertThat("Should be applicable for pickup item status loaned",
			applicable, is(true));
	}

	@Test
	void shouldNotBeApplicableForItemStatusesOtherThanLoaned() {
		// Arrange
		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.status(READY_FOR_PICKUP)
			.pickupItemStatus(ITEM_RECEIVED)
			.localItemStatus(ITEM_ON_HOLDSHELF)
			.build();

		final var ctx = new RequestWorkflowContext();
		ctx.setPatronRequest(patronRequest);

		// Act
		final var applicable = handleBorrowerItemLoaned.isApplicableFor(ctx);

		// Assert
		assertThat("Should not be applicable for any other item status than loaned",
			applicable, is(false));
	}

	@Test
	void declarativeSupplierDoesNotRequireVirtualPatronCheckout() {
		final var repository = mock(PatronRequestRepository.class);
		final var hostLmsService = mock(HostLmsService.class);
		final var auditService = mock(PatronRequestAuditService.class);
		final var transition = new HandleBorrowerItemLoaned(
			repository, hostLmsService, auditService);
		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.status(READY_FOR_PICKUP)
			.localItemStatus(ITEM_LOANED)
			.build();
		final var context = new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setSupplierRequest(SupplierRequest.builder()
				.id(randomUUID())
				.protocol("ncip-v202")
				.build());
		when(repository.saveOrUpdate(any())).thenReturn(Mono.just(patronRequest));

		final var updated = singleValueFrom(transition.attempt(context));

		assertThat(updated.getPatronRequest().getStatus(), is(LOANED));
		assertThat(updated.getWorkflowMessages(), hasItem(
			"Skipped supplier-side virtual patron checkout for declarative supplier request"));
		verifyNoInteractions(hostLmsService, auditService);
	}
}
