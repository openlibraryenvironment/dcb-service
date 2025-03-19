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
import static org.olf.dcb.core.interaction.HostLmsItem.*;
import static org.olf.dcb.core.model.PatronRequest.Status.*;

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
}
