package org.olf.dcb.request.workflow;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.PatronRequest.Status.PICKUP_TRANSIT;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import jakarta.inject.Inject;
import reactor.core.publisher.Mono;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class HandleSupplierRequestCancelledTests {
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;

	@Inject
	private RequestWorkflowContextHelper requestWorkflowContextHelper;
	@Inject
	private HandleSupplierRequestCancelled handleSupplierRequestCancelled;

	@BeforeEach
	void beforeEach() {
		supplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
	}

	@Test
	void shouldProgressRequestWhenRequestHasBeenPlacedWithTheSupplier() {
		// Arrange
		final var patronRequest = definePatronRequest(REQUEST_PLACED_AT_SUPPLYING_AGENCY);

		// Act
		final var updatedPatronRequest = handleCancelledSupplierRequest(patronRequest);

		// Assert
		assertThat(updatedPatronRequest, allOf(
			notNullValue()
		));
	}

	@Test
	void shouldNotProgressRequestWhenItemHasBeenDispatchedForPickup() {
		// Arrange
		final var patronRequest = definePatronRequest(PICKUP_TRANSIT);

		// Act
		final var applicable = isApplicable(patronRequest);

		// Assert
		assertThat("Should not be applicable after item has been dispatched",
			applicable, is(false));
	}

	@Test
	void shouldTolerateNullPatronRequestStatus() {
		// Arrange
		final var patronRequest = definePatronRequest(null);

		// Act
		final var applicable = isApplicable(patronRequest);

		// Assert
		assertThat("Should not be applicable for request with no status",
			applicable, is(false));
	}

	private PatronRequest handleCancelledSupplierRequest(PatronRequest patronRequest) {
		return singleValueFrom(requestWorkflowContextHelper.fromPatronRequest(patronRequest)
				.flatMap(ctx -> {
					if (!handleSupplierRequestCancelled.isApplicableFor(ctx)) {
						return Mono.error(new RuntimeException("Handle supplier request cancelled is not applicable for request"));
					}

					return handleSupplierRequestCancelled.attempt(ctx);
				})
				.thenReturn(patronRequest));
	}

	private Boolean isApplicable(PatronRequest patronRequest) {
		return singleValueFrom(
			requestWorkflowContextHelper.fromPatronRequest(patronRequest)
				.map(ctx -> handleSupplierRequestCancelled.isApplicableFor(ctx)));
	}

	private PatronRequest definePatronRequest(PatronRequest.Status status) {
		final var patron = Patron.builder()
			.id(randomUUID())
			.build();

		patronFixture.savePatron(patron);

		final var patronRequest = PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(status)
			.build();

		patronRequestsFixture.savePatronRequest(patronRequest);
		return patronRequest;
	}
}
