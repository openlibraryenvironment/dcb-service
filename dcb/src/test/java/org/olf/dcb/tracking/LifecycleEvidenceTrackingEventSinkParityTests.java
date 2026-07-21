package org.olf.dcb.tracking;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasAuditDataProperty;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasBriefDescription;

import java.util.Map;
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
class LifecycleEvidenceTrackingEventSinkParityTests {
	@Inject
	HostLmsReactions hostLmsReactions;

	@Inject
	LifecycleEvidenceTrackingEventSink lifecycleEvidenceTrackingEventSink;

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
		resetData();
	}

	@Test
	void preservesBorrowerRequestProjectionAndAuditShape() {
		assertBorrowerRequestProjection(hostLmsReactions);
		resetData();
		assertBorrowerRequestProjection(lifecycleEvidenceTrackingEventSink);
	}

	@Test
	void preservesBorrowerVirtualItemProjectionAndAuditShape() {
		assertBorrowerVirtualItemProjection(hostLmsReactions);
		resetData();
		assertBorrowerVirtualItemProjection(lifecycleEvidenceTrackingEventSink);
	}

	@Test
	void preservesSupplierItemProjectionAndAuditShape() {
		assertSupplierItemProjection(hostLmsReactions);
		resetData();
		assertSupplierItemProjection(lifecycleEvidenceTrackingEventSink);
	}

	@Test
	void preservesPickupRequestProjectionAndAuditShape() {
		assertPickupRequestProjection(hostLmsReactions);
		resetData();
		assertPickupRequestProjection(lifecycleEvidenceTrackingEventSink);
	}

	@Test
	void preservesPickupItemProjectionAndAuditShape() {
		assertPickupItemProjection(hostLmsReactions);
		resetData();
		assertPickupItemProjection(lifecycleEvidenceTrackingEventSink);
	}

	private void assertBorrowerRequestProjection(TrackingEventSink sink) {
		final var patronRequest = savePatronRequest(PatronRequest.builder()
			.id(UUID.randomUUID())
			.localRequestStatus("PLACED")
			.localRequestStatusRepeat(Long.valueOf(3))
			.build());

		project(sink, StateChange.builder()
			.resourceType("PatronRequest")
			.resource(patronRequest)
			.resourceId(patronRequest.getId().toString())
			.fromState("PLACED")
			.toState("MISSING")
			.patronRequestId(patronRequest.getId())
			.additionalProperties(Map.of("toRawStatus", "missing"))
			.build());

		final var updatedPatronRequest =
			patronRequestsFixture.findById(patronRequest.getId());

		assertThat(updatedPatronRequest.getLocalRequestStatus(), is("MISSING"));
		assertThat(updatedPatronRequest.getLocalRequestStatusRepeat(),
			is(Long.valueOf(0)));
		assertThat(updatedPatronRequest.getLocalRequestLastCheckTimestamp(),
			notNullValue());
		assertPollingAudit(patronRequest, "PatronRequest",
			patronRequest.getId().toString(), "PLACED", "MISSING", "BORROWER",
			"REQUEST");
	}

	private void assertBorrowerVirtualItemProjection(TrackingEventSink sink) {
		final var patronRequest = savePatronRequest(PatronRequest.builder()
			.id(UUID.randomUUID())
			.localItemStatus("TRANSIT")
			.localItemStatusRepeat(Long.valueOf(2))
			.localRenewalCount(Integer.valueOf(0))
			.build());

		project(sink, StateChange.builder()
			.resourceType("BorrowerVirtualItem")
			.resource(patronRequest)
			.resourceId(patronRequest.getId().toString())
			.fromState("TRANSIT")
			.toState("LOANED")
			.fromRenewalCount(Integer.valueOf(0))
			.toRenewalCount(Integer.valueOf(1))
			.patronRequestId(patronRequest.getId())
			.build());

		final var updatedPatronRequest =
			patronRequestsFixture.findById(patronRequest.getId());

		assertThat(updatedPatronRequest.getLocalItemStatus(), is("LOANED"));
		assertThat(updatedPatronRequest.getLocalItemStatusRepeat(),
			is(Long.valueOf(0)));
		assertThat(updatedPatronRequest.getLocalItemLastCheckTimestamp(),
			notNullValue());
		assertThat(updatedPatronRequest.getLocalRenewalCount(),
			is(Integer.valueOf(1)));
		assertPollingAudit(patronRequest, "BorrowerVirtualItem",
			patronRequest.getId().toString(), "TRANSIT", "LOANED", "BORROWER",
			"ITEM");
		assertThat(patronRequestsFixture.findOnlyAuditEntry(patronRequest),
			allOf(
				hasAuditDataProperty("fromRenewalCount", Integer.valueOf(0)),
				hasAuditDataProperty("toRenewalCount", Integer.valueOf(1))));
	}

	private void assertSupplierItemProjection(TrackingEventSink sink) {
		final var patronRequest = savePatronRequest(PatronRequest.builder()
			.id(UUID.randomUUID())
			.build());
		final var supplierRequest = saveSupplierRequest(patronRequest,
			SupplierRequest.builder()
				.localItemStatus("AVAILABLE")
				.localItemStatusRepeat(Long.valueOf(4))
				.localRenewalCount(Integer.valueOf(0))
				.localHoldCount(Integer.valueOf(1))
				.localRenewable(Boolean.TRUE));

		project(sink, StateChange.builder()
			.resourceType("SupplierItem")
			.resource(supplierRequest)
			.resourceId(supplierRequest.getId().toString())
			.fromState("AVAILABLE")
			.toState("TRANSIT")
			.fromRenewalCount(Integer.valueOf(0))
			.toRenewalCount(Integer.valueOf(2))
			.fromHoldCount(Integer.valueOf(1))
			.toHoldCount(Integer.valueOf(3))
			.renewable(Boolean.FALSE)
			.patronRequestId(patronRequest.getId())
			.build());

		final var updatedSupplierRequest =
			supplierRequestsFixture.findById(supplierRequest.getId());

		assertThat(updatedSupplierRequest.getLocalItemStatus(), is("TRANSIT"));
		assertThat(updatedSupplierRequest.getLocalItemStatusRepeat(),
			is(Long.valueOf(0)));
		assertThat(updatedSupplierRequest.getLocalItemLastCheckTimestamp(),
			notNullValue());
		assertThat(updatedSupplierRequest.getLocalRenewalCount(),
			is(Integer.valueOf(2)));
		assertThat(updatedSupplierRequest.getLocalHoldCount(),
			is(Integer.valueOf(3)));
		assertThat(updatedSupplierRequest.getLocalRenewable(), is(Boolean.FALSE));
		assertPollingAudit(patronRequest, "SupplierItem",
			supplierRequest.getId().toString(), "AVAILABLE", "TRANSIT",
			"SUPPLIER", "ITEM");
		assertThat(patronRequestsFixture.findOnlyAuditEntry(patronRequest),
			allOf(
				hasAuditDataProperty("fromRenewalCount", Integer.valueOf(0)),
				hasAuditDataProperty("toRenewalCount", Integer.valueOf(2)),
				hasAuditDataProperty("fromLocalHoldCount", Integer.valueOf(1)),
				hasAuditDataProperty("toLocalHoldCount", Integer.valueOf(3))));
	}

	private void assertPickupRequestProjection(TrackingEventSink sink) {
		final var patronRequest = savePatronRequest(PatronRequest.builder()
			.id(UUID.randomUUID())
			.pickupRequestStatus("PLACED")
			.pickupRequestStatusRepeat(Long.valueOf(2))
			.build());

		project(sink, StateChange.builder()
			.resourceType("PickupRequest")
			.resource(patronRequest)
			.resourceId(patronRequest.getId().toString())
			.fromState("PLACED")
			.toState("READY")
			.patronRequestId(patronRequest.getId())
			.build());

		final var updatedPatronRequest =
			patronRequestsFixture.findById(patronRequest.getId());

		assertThat(updatedPatronRequest.getPickupRequestStatus(), is("READY"));
		assertThat(updatedPatronRequest.getPickupRequestStatusRepeat(),
			is(Long.valueOf(0)));
		assertThat(updatedPatronRequest.getPickupRequestLastCheckTimestamp(),
			notNullValue());
		assertPollingAudit(patronRequest, "PickupRequest",
			patronRequest.getId().toString(), "PLACED", "READY", "PICKUP",
			"REQUEST");
	}

	private void assertPickupItemProjection(TrackingEventSink sink) {
		final var patronRequest = savePatronRequest(PatronRequest.builder()
			.id(UUID.randomUUID())
			.pickupItemStatus("TRANSIT")
			.pickupItemStatusRepeat(Long.valueOf(5))
			.build());

		project(sink, StateChange.builder()
			.resourceType("PickupItem")
			.resource(patronRequest)
			.resourceId(patronRequest.getId().toString())
			.fromState("TRANSIT")
			.toState("HOLDSHELF")
			.patronRequestId(patronRequest.getId())
			.build());

		final var updatedPatronRequest =
			patronRequestsFixture.findById(patronRequest.getId());

		assertThat(updatedPatronRequest.getPickupItemStatus(), is("HOLDSHELF"));
		assertThat(updatedPatronRequest.getPickupItemStatusRepeat(),
			is(Long.valueOf(0)));
		assertThat(updatedPatronRequest.getPickupItemLastCheckTimestamp(),
			notNullValue());
		assertPollingAudit(patronRequest, "PickupItem",
			patronRequest.getId().toString(), "TRANSIT", "HOLDSHELF", "PICKUP",
			"ITEM");
	}

	private void assertPollingAudit(PatronRequest patronRequest,
		String resourceType, String resourceId, String fromState, String toState,
		String role, String resource) {

		assertThat(patronRequestsFixture.findOnlyAuditEntry(patronRequest),
			allOf(
				notNullValue(),
				hasBriefDescription("to %s from %s - %s(%s)"
					.formatted(toState, fromState, resourceType, resourceId)),
				hasAuditDataProperty("patronRequestId",
					patronRequest.getId().toString()),
				hasAuditDataProperty("source", "POLLING"),
				hasAuditDataProperty("role", role),
				hasAuditDataProperty("resource", resource),
				hasAuditDataProperty("resourceType", resourceType),
				hasAuditDataProperty("resourceId", resourceId),
				hasAuditDataProperty("fromState", fromState),
				hasAuditDataProperty("toState", toState)));
	}

	private PatronRequest savePatronRequest(PatronRequest patronRequest) {
		return patronRequestsFixture.savePatronRequest(patronRequest);
	}

	private SupplierRequest saveSupplierRequest(PatronRequest patronRequest,
		SupplierRequest.SupplierRequestBuilder<?, ?> builder) {

		return supplierRequestsFixture.saveSupplierRequest(builder
			.id(UUID.randomUUID())
			.patronRequest(patronRequest)
			.hostLmsCode("supplying-host-lms")
			.localItemId("item-1")
			.build());
	}

	private static void project(TrackingEventSink sink, StateChange stateChange) {
		singleValueFrom(sink.onTrackingEvent(stateChange));
	}

	private void resetData() {
		supplierRequestsFixture.deleteAll();
		patronRequestsFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms("supplying-host-lms");
	}
}
