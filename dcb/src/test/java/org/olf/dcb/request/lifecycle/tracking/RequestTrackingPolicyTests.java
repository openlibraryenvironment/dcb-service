package org.olf.dcb.request.lifecycle.tracking;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.WorkflowConstants;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.lifecycle.LifecycleCapabilitiesConfiguration;
import org.olf.dcb.request.lifecycle.LifecycleCapabilityResolver;
import org.olf.dcb.request.lifecycle.LifecycleRole;
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
			.setProtocol("ncip-v202");
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
			.setProtocol("ncip-v202");
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
			.setProtocol("ncip-v202");
		configuration.getBorrowerTracking()
			.setMode(TrackingMode.EVENT_DRIVEN);
		configuration.getBorrowerTracking()
			.setProtocol("ncip-v202");
		final var policy = policyFor(configuration);

		assertThat(policy.schedulesAutomaticPolls(contextIn(
			PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY)), is(false));
	}

	@Test
	void dualEventDrivenTrackingSuppressesPollingAcrossDirectLifecycle() {
		final var configuration = new LifecycleCapabilitiesConfiguration();
		configuration.getSupplierTracking()
			.setMode(TrackingMode.EVENT_DRIVEN);
		configuration.getSupplierTracking()
			.setProtocol("ncip-v202");
		configuration.getBorrowerTracking()
			.setMode(TrackingMode.EVENT_DRIVEN);
		configuration.getBorrowerTracking()
			.setProtocol("ncip-v202");
		final var policy = policyFor(configuration);

		final var scheduled = List.of(
			PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY,
			PatronRequest.Status.CONFIRMED,
			PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY,
			PatronRequest.Status.PICKUP_TRANSIT,
			PatronRequest.Status.RECEIVED_AT_PICKUP,
			PatronRequest.Status.READY_FOR_PICKUP,
			PatronRequest.Status.LOANED,
			PatronRequest.Status.RETURN_TRANSIT)
			.stream()
			.map(RequestTrackingPolicyTests::contextIn)
			.map(policy::schedulesAutomaticPolls)
			.toList();

		assertThat(scheduled, everyItem(is(false)));
	}

	@Test
	void pickupAnywhereKeepsPollingForImperativePickupRole() {
		final var configuration = dualEventDrivenConfiguration();
		final var policy = policyFor(configuration);
		final var context = contextIn(PatronRequest.Status.PICKUP_TRANSIT);
		context.getPatronRequest().setActiveWorkflow(
			WorkflowConstants.PICKUP_ANYWHERE_WORKFLOW);

		assertThat(policy.schedulesAutomaticPolls(context), is(true));
		assertThat(policy.modeFor(context), is(TrackingMode.HYBRID));
		assertThat(policy.schedulesAutomaticPolls(
			context, LifecycleRole.SUPPLIER), is(false));
		assertThat(policy.schedulesAutomaticPolls(
			context, LifecycleRole.BORROWER), is(false));
		assertThat(policy.schedulesAutomaticPolls(
			context, LifecycleRole.PICKUP), is(true));
	}

	private static DefaultRequestTrackingPolicy defaultPolicy() {
		return policyFor(new LifecycleCapabilitiesConfiguration());
	}

	private static LifecycleCapabilitiesConfiguration dualEventDrivenConfiguration() {
		final var configuration = new LifecycleCapabilitiesConfiguration();
		configuration.getSupplierTracking().setMode(TrackingMode.EVENT_DRIVEN);
		configuration.getSupplierTracking().setProtocol("ncip-v202");
		configuration.getBorrowerTracking().setMode(TrackingMode.EVENT_DRIVEN);
		configuration.getBorrowerTracking().setProtocol("ncip-v202");
		return configuration;
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
