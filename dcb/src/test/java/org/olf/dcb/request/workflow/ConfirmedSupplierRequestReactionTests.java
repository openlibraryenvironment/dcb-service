package org.olf.dcb.request.workflow;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CONFIRMED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_PLACED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasAuditDataProperty;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasBriefDescription;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.PatronRequest;
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
		final var patronRequest = patronRequestsFixture.savePatronRequest(
			PatronRequest.builder()
				.id(UUID.randomUUID())
				.build());

		final var supplierRequest = supplierRequestsFixture.saveSupplierRequest(
			SupplierRequest.builder()
				.id(UUID.randomUUID())
				.hostLmsCode("supplying-host-lms")
				.localItemId("5729624")
				.patronRequest(patronRequest)
				.build());

		// Act
		singleValueFrom(hostLmsReactions.onTrackingEvent(
			StateChange.builder()
				.resourceType("SupplierRequest")
				.resource(supplierRequest)
				.resourceId(supplierRequest.getId().toString())
				.fromState(HOLD_PLACED)
				.toState(HOLD_CONFIRMED)
				.patronRequestId(patronRequest.getId())
				.build()));

		// Assert
		assertThat(patronRequestsFixture.findOnlyAuditEntry(patronRequest), allOf(
			notNullValue(),
			hasBriefDescription("Downstream change to SupplierRequest(%s) to %s from %s triggers SupplierRequestUnhandledState"
				.formatted(supplierRequest.getId().toString(), "CONFIRMED", "PLACED")),
			hasAuditDataProperty("patronRequestId", patronRequest.getId().toString()),
			hasAuditDataProperty("resourceType", "SupplierRequest"),
			hasAuditDataProperty("resourceId", supplierRequest.getId().toString()),
			hasAuditDataProperty("fromState", "PLACED"),
			hasAuditDataProperty("toState", "CONFIRMED")
		));
	}
}
