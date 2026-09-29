package org.olf.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.olf.dcb.core.model.PatronRequest.Status.FINALISED;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.RETURN_TRANSIT;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import jakarta.inject.Inject;

@DcbTest
class ActiveRequestHoldingSupplierItemTests {
	private static final String LENDER = "lender-system";
	private static final String ITEM = "item-1";

	@Inject
	private PatronRequestsFixture patronRequestsFixture;

	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;

	@Inject
	private PatronRequestRepository patronRequestRepository;

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAll();
	}

	@Test
	void shouldFindARequestInFlightForTheItem() {
		final var inFlight = requestHolding(REQUEST_PLACED_AT_SUPPLYING_AGENCY, LENDER, true);

		assertThat(find().getId(), is(inFlight.getId()));
	}

	@Test
	void shouldIgnoreAFinishedRequest() {
		requestHolding(FINALISED, LENDER, true);

		assertThat(find(), is(nullValue()));
	}

	// A walk-up whose book is back on the lender's shelf may be lent again
	@Test
	void shouldIgnoreARequestWhoseItemIsOnItsWayBack() {
		requestHolding(RETURN_TRANSIT, LENDER, true);

		assertThat(find(), is(nullValue()));
	}

	@Test
	void shouldIgnoreASupplierRequestThatWasReplaced() {
		requestHolding(REQUEST_PLACED_AT_SUPPLYING_AGENCY, LENDER, false);

		assertThat(find(), is(nullValue()));
	}

	@Test
	void shouldIgnoreTheSameItemIdOnAnotherSystem() {
		requestHolding(REQUEST_PLACED_AT_SUPPLYING_AGENCY, "other-system", true);

		assertThat(find(), is(nullValue()));
	}

	private PatronRequest find() {
		return singleValueFrom(patronRequestRepository.findActiveRequestHoldingSupplierItem(LENDER, ITEM));
	}

	private PatronRequest requestHolding(PatronRequest.Status status, String hostLmsCode, boolean active) {
		final var patronRequest = patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.status(status)
			.build());

		supplierRequestsFixture.saveSupplierRequest(SupplierRequest.builder()
			.id(randomUUID())
			.patronRequest(patronRequest)
			.localItemId(ITEM)
			.hostLmsCode(hostLmsCode)
			.isActive(active)
			.build());

		return patronRequest;
	}
}
