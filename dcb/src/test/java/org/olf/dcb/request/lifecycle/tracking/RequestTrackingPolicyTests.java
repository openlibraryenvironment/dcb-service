package org.olf.dcb.request.lifecycle.tracking;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.LifecycleCapabilitiesConfiguration;
import org.olf.dcb.request.lifecycle.LifecycleCapabilityResolver;
import org.olf.dcb.request.lifecycle.TrackingMode;

class RequestTrackingPolicyTests {
	@Test
	void missingCapabilityConfigKeepsAutomaticPollingScheduled() {
		final var policy = defaultPolicy();
		final var context = contextIn(
			PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY);

		assertThat(policy.schedulesAutomaticPolls(context), is(true));
		assertThat(policy.modeFor(context), is(TrackingMode.SCHEDULED_POLL));
	}

	@Test
	void supplierEventDrivenTrackingSuppressesSupplierPlacementPolling() {
		final var configuration = new LifecycleCapabilitiesConfiguration();
		configuration.getSupplierTracking()
			.setMode(TrackingMode.EVENT_DRIVEN);
		configuration.getSupplierTracking()
			.setProtocol("iso18626");
		final var policy = policyFor(configuration);

		assertThat(policy.schedulesAutomaticPolls(contextIn(
			PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY)), is(false));
	}

	@Test
	void borrowerPlacementStillPollsWhenSupplierOrBorrowerNeedsScheduledTracking() {
		final var configuration = new LifecycleCapabilitiesConfiguration();
		configuration.getSupplierTracking()
			.setMode(TrackingMode.EVENT_DRIVEN);
		configuration.getSupplierTracking()
			.setProtocol("iso18626");
		final var policy = policyFor(configuration);

		assertThat(policy.schedulesAutomaticPolls(contextIn(
			PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY)), is(true));
	}

	@Test
	void dualEventDrivenTrackingSuppressesBorrowerPlacementPolling() {
		final var configuration = new LifecycleCapabilitiesConfiguration();
		configuration.getSupplierTracking()
			.setMode(TrackingMode.EVENT_DRIVEN);
		configuration.getSupplierTracking()
			.setProtocol("iso18626");
		configuration.getBorrowerTracking()
			.setMode(TrackingMode.EVENT_DRIVEN);
		configuration.getBorrowerTracking()
			.setProtocol("iso18626");
		final var policy = policyFor(configuration);

		assertThat(policy.schedulesAutomaticPolls(contextIn(
			PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY)), is(false));
	}

	@Test
	void statusesOutsideDeclarativeSpikeContinueToSchedulePolling() {
		final var configuration = new LifecycleCapabilitiesConfiguration();
		configuration.getSupplierTracking()
			.setMode(TrackingMode.EVENT_DRIVEN);
		configuration.getSupplierTracking()
			.setProtocol("iso18626");
		configuration.getBorrowerTracking()
			.setMode(TrackingMode.EVENT_DRIVEN);
		configuration.getBorrowerTracking()
			.setProtocol("iso18626");
		final var policy = policyFor(configuration);

		assertThat(policy.schedulesAutomaticPolls(contextIn(
			PatronRequest.Status.REQUEST_PLACED_AT_PICKUP_AGENCY)), is(true));
	}

	private static DefaultRequestTrackingPolicy defaultPolicy() {
		return policyFor(new LifecycleCapabilitiesConfiguration());
	}

	private static DefaultRequestTrackingPolicy policyFor(
		LifecycleCapabilitiesConfiguration configuration) {

		return new DefaultRequestTrackingPolicy(
			new LifecycleCapabilityResolver(configuration));
	}

	private static RequestWorkflowContext contextIn(PatronRequest.Status status) {
		return new RequestWorkflowContext()
			.setPatronRequest(new PatronRequest().setStatus(status));
	}
}
