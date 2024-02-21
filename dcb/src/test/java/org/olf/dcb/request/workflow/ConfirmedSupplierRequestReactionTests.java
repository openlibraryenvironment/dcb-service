package org.olf.dcb.request.workflow;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CONFIRMED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.SupplierRequestsFixture;
import org.olf.dcb.tracking.model.StateChange;

import jakarta.inject.Inject;

@DcbTest
class ConfirmedSupplierRequestReactionTests {
	@Inject
	HostLmsReactions hostLmsReactions;

	@Inject
	PatronRequestsFixture patronRequestsFixture;
	@Inject
	SupplierRequestsFixture supplierRequestsFixture;
	@Inject
	AgencyFixture agencyFixture;
	@Inject
	HostLmsFixture hostLmsFixture;

	@BeforeEach
	void beforeEach() {
		supplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms("supplying-host-lms");
	}

	@Test
	void shouldReactToLocalSupplierRequestChangingToConfirmed() {
		// Arrange
		final var supplierRequest = supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(UUID.randomUUID())
				.hostLmsCode("supplying-host-lms")
				.localItemId("5729624")
				.build());

		// Act
		final var context = singleValueFrom(
			hostLmsReactions.onTrackingEvent(StateChange.builder()
				.resourceType("SupplierRequest")
				.resource(supplierRequest)
				.toState(HOLD_CONFIRMED)
				.build()));

		// Assert
		assertThat(context, is(notNullValue()));
	}
}
