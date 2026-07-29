package org.olf.dcb.request.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.PickupAgencyService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;
import org.olf.dcb.request.lifecycle.LifecycleCapabilitiesConfiguration;
import org.olf.dcb.request.lifecycle.LifecycleCapabilityResolver;
import org.olf.dcb.storage.SupplierRequestRepository;

import reactor.core.publisher.Mono;

/**
 * The declarative cancellation guard suppresses imperative supplier cleanup.
 * If it ever misfires on an imperative host, the real hold is never cancelled
 * in the supplying LMS and we leave a zombie record behind - so the negative
 * cases below matter more than the positive one.
 *
 * The guard resolves the SUPPLIER placement strategy rather than reading the
 * supplier request's protocol string, precisely because Foundation is an
 * imperative NCIP adapter and would otherwise look declarative.
 */
class DeclarativeCancellationGuardTests {
	private static final String SKIP_CLEANUP_MESSAGE =
		"Declarative supplier cancellation is not implemented; skipping imperative supplier cleanup.";
	private static final String SKIP_VERIFY_MESSAGE =
		"Declarative supplier cancellation verification is not implemented; skipping imperative supplier verification.";

	private final PatronRequestAuditService auditService = mock(PatronRequestAuditService.class);
	private final HostLmsService hostLmsService = mock(HostLmsService.class);
	private final SupplierRequestRepository supplierRequestRepository = mock(SupplierRequestRepository.class);
	private final SupplyingAgencyService supplyingAgencyService = mock(SupplyingAgencyService.class);
	private final PickupAgencyService pickupAgencyService = mock(PickupAgencyService.class);

	private final CancelledPatronRequestTransition transition =
		new CancelledPatronRequestTransition(
			auditService,
			hostLmsService,
			supplierRequestRepository,
			supplyingAgencyService,
			pickupAgencyService,
			new LifecycleCapabilityResolver(new LifecycleCapabilitiesConfiguration()));

	// This module runs JUnit with per_class test instance lifecycle, so these
	// mock fields are shared across every method. Without a reset the imperative
	// cases' interactions leak into the declarative case's never() verifications.
	@BeforeEach
	void resetMocks() {
		reset(auditService, hostLmsService, supplierRequestRepository,
			supplyingAgencyService, pickupAgencyService);
	}

	@Test
	void declarativeSupplierCleanupIsAuditedWithoutImperativeCancellation() {
		final var context = context(
			declarativeSupplierHost(),
			supplierRequest().setProtocol("ncip-v202"));
		final var patronRequest = context.getPatronRequest();

		transition.attempt(context).block();

		assertThat(patronRequest.getStatus(), is(PatronRequest.Status.CANCELLED));
		verify(supplyingAgencyService, never()).cancelHold(context);
		verify(hostLmsService, never()).getClientFor("supplier-host");
		verify(auditService).addAuditEntry(eq(patronRequest), eq(SKIP_CLEANUP_MESSAGE), anyMap());
		verify(auditService).addAuditEntry(eq(patronRequest), eq(SKIP_VERIFY_MESSAGE), anyMap());
	}

	@Test
	void imperativeSupplierRequestIsCancelledAtTheSupplyingSystem() {
		final var context = context(imperativeSupplierHost(), supplierRequest());

		when(supplyingAgencyService.cancelHold(context)).thenReturn(Mono.just(context));
		stubSupplierRequestLookup();

		transition.attempt(context).block();

		verify(supplyingAgencyService).cancelHold(context);
		verify(hostLmsService).getClientFor("supplier-host");
		verify(auditService, never()).addAuditEntry(
			eq(context.getPatronRequest()), eq(SKIP_CLEANUP_MESSAGE), anyMap());
	}

	/**
	 * The exact regression the strategy-based discriminator exists to prevent.
	 * Foundation is an imperative adapter that speaks NCIP; a protocol string on
	 * the supplier request must not be mistaken for declarative placement.
	 */
	@Test
	void foundationHostWithNcipProtocolIsStillCancelledImperatively() {
		final var context = context(
			imperativeSupplierHost(),
			supplierRequest().setProtocol("NCIP"));

		when(supplyingAgencyService.cancelHold(context)).thenReturn(Mono.just(context));
		stubSupplierRequestLookup();

		transition.attempt(context).block();

		verify(supplyingAgencyService).cancelHold(context);
		verify(auditService, never()).addAuditEntry(
			eq(context.getPatronRequest()), eq(SKIP_CLEANUP_MESSAGE), anyMap());
	}

	/**
	 * An unresolvable lender host must fail towards cleanup, not away from it.
	 */
	@Test
	void unknownSupplierHostFallsBackToImperativeCancellation() {
		final var context = context(null, supplierRequest());

		when(supplyingAgencyService.cancelHold(context)).thenReturn(Mono.just(context));
		stubSupplierRequestLookup();

		transition.attempt(context).block();

		verify(supplyingAgencyService).cancelHold(context);
		verify(auditService, never()).addAuditEntry(
			eq(context.getPatronRequest()), eq(SKIP_CLEANUP_MESSAGE), anyMap());
	}

	private RequestWorkflowContext context(DataHostLms lenderSystem, SupplierRequest supplierRequest) {
		final var patronRequest = new PatronRequest()
			.setId(UUID.randomUUID())
			.setStatus(PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY)
			.setLocalRequestStatus(HOLD_MISSING);

		when(auditService.addAuditEntry(eq(patronRequest), anyString(), anyMap()))
			.thenReturn(Mono.empty());

		return new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setSupplierRequest(supplierRequest)
			.setLenderSystem(lenderSystem);
	}

	/**
	 * The imperative path verifies cancellation by re-reading the hold from the
	 * supplying system. Reported as still PLACED so verification does not try to
	 * persist a status change.
	 */
	private void stubSupplierRequestLookup() {
		final var client = mock(HostLmsClient.class);

		when(hostLmsService.getClientFor("supplier-host")).thenReturn(Mono.just(client));
		when(client.getRequest(any(HostLmsRequest.class)))
			.thenReturn(Mono.just(HostLmsRequest.builder()
				.localId("supplier-remote-request")
				.status("PLACED")
				.build()));
	}

	private static SupplierRequest supplierRequest() {
		return new SupplierRequest()
			.setHostLmsCode("supplier-host")
			.setLocalId("supplier-remote-request")
			.setLocalStatus("PLACED");
	}

	private static DataHostLms imperativeSupplierHost() {
		return host(Map.of());
	}

	private static DataHostLms declarativeSupplierHost() {
		return host(Map.of("capabilities", Map.of(
			"supplying-agency-request",
			Map.of("strategy", "declarative", "protocol", "ncip-v202"))));
	}

	private static DataHostLms host(Map<String, Object> clientConfig) {
		return DataHostLms.builder()
			.id(UUID.randomUUID())
			.code("supplier-host")
			.name("supplier-host")
			.clientConfig(clientConfig)
			.build();
	}
}
