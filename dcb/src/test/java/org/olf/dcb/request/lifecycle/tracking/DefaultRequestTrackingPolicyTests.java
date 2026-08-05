package org.olf.dcb.request.lifecycle.tracking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.LifecycleCapabilitiesConfiguration;
import org.olf.dcb.request.lifecycle.LifecycleCapabilityResolver;
import org.olf.dcb.request.lifecycle.TrackingMode;

/**
 * PR-7: tracking policy + poll suppression. Pure unit test.
 *
 * The default must preserve behaviour (scheduled poll) so nothing changes for
 * existing hosts; an event-driven host suppresses the scheduled poll.
 */
class DefaultRequestTrackingPolicyTests {
	private final LifecycleCapabilitiesConfiguration globalConfig =
		new LifecycleCapabilitiesConfiguration();

	private final DefaultRequestTrackingPolicy policy =
		new DefaultRequestTrackingPolicy(new LifecycleCapabilityResolver(globalConfig));

	private static RequestWorkflowContext context(
		PatronRequest.Status status, Map<String, Object> borrowerHostConfig) {

		final var patronRequest = mock(PatronRequest.class);
		when(patronRequest.getStatus()).thenReturn(status);

		final var borrowerHost = mock(DataHostLms.class);
		when(borrowerHost.getCode()).thenReturn("BORROWER");
		when(borrowerHost.getClientConfig()).thenReturn(borrowerHostConfig);

		return new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setPatronSystem(borrowerHost);
	}

	@Test
	void defaultConfigSchedulesPollsSoBehaviourIsUnchanged() {
		final var ctx = context(
			PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY, Map.of());

		assertEquals(TrackingMode.SCHEDULED_POLL, policy.modeFor(ctx));
		assertTrue(policy.schedulesAutomaticPolls(ctx));
	}

	@Test
	void untrackedStatusStillSchedules() {
		final var ctx = context(PatronRequest.Status.SUBMITTED_TO_DCB, Map.of());

		assertTrue(policy.schedulesAutomaticPolls(ctx));
	}

	@Test
	void suppressesPollWhenAllTrackedRolesAreEventDriven() {
		// Supplier tracking event-driven instance-wide...
		final var supplierTracking =
			new LifecycleCapabilitiesConfiguration.TrackingCapability();
		supplierTracking.setMode(TrackingMode.EVENT_DRIVEN);
		supplierTracking.setProtocol("ncip-v202");
		globalConfig.setSupplierTracking(supplierTracking);

		// ...and borrower tracking event-driven on the borrower host itself.
		final var ctx = context(
			PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY,
			Map.of("capabilities", Map.of(
				"borrower-tracking",
				Map.of("mode", "event-driven", "protocol", "ncip-v202"))));

		assertEquals(TrackingMode.EVENT_DRIVEN, policy.modeFor(ctx));
		assertFalse(policy.schedulesAutomaticPolls(ctx));
	}
}
