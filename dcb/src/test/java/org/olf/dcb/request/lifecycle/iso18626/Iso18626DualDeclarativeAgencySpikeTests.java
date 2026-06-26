package org.olf.dcb.request.lifecycle.iso18626;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.request.lifecycle.DeclarativeRequestTransport;
import org.olf.dcb.request.lifecycle.DeclarativeTransportRequest;
import org.olf.dcb.request.lifecycle.DeclarativeTransportResponse;
import org.olf.dcb.request.lifecycle.LifecycleCapabilitiesConfiguration;
import org.olf.dcb.request.lifecycle.LifecycleCapabilityResolver;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.olf.dcb.request.lifecycle.StrategyType;
import org.olf.dcb.request.lifecycle.TrackingMode;
import org.olf.dcb.request.lifecycle.placement.BorrowingAgencyRequestProjector;
import org.olf.dcb.request.lifecycle.placement.BorrowingAgencyRequestStrategyService;
import org.olf.dcb.request.lifecycle.placement.BorrowingAgencyRequestStrategyResolver;
import org.olf.dcb.request.lifecycle.placement.ImperativeBorrowingAgencyRequestStrategy;
import org.olf.dcb.request.lifecycle.placement.ImperativeSupplyingAgencyRequestStrategy;
import org.olf.dcb.request.lifecycle.placement.SupplyingAgencyRequestProjector;
import org.olf.dcb.request.lifecycle.placement.SupplyingAgencyRequestStrategyService;
import org.olf.dcb.request.lifecycle.placement.SupplyingAgencyRequestStrategyResolver;
import org.olf.dcb.request.lifecycle.tracking.DefaultRequestTrackingPolicy;
import org.olf.dcb.request.lifecycle.tracking.InboundLifecycleMessage;
import org.olf.dcb.request.lifecycle.tracking.InboundLifecycleMessageHandler;
import org.olf.dcb.request.lifecycle.tracking.InboundLifecycleMessageIdempotencyGuard;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import reactor.core.publisher.Mono;

class Iso18626DualDeclarativeAgencySpikeTests {
	@Test
	void supplierAndBorrowerCanBothUseDeclarativeEventDrivenPath() {
		final var configuration = dualDeclarativeConfiguration();
		final var capabilityResolver = new LifecycleCapabilityResolver(configuration);
		final var trackingPolicy = new DefaultRequestTrackingPolicy(
			capabilityResolver);
		final var transport = new RoleAwareTransport();
		final var patronRequestId = UUID.randomUUID();
		final var patronRequest = new PatronRequest()
			.setId(patronRequestId)
			.setPatronHostlmsCode("borrower-host")
			.setStatus(PatronRequest.Status.RESOLVED);
		final var supplierRequest = new SupplierRequest()
			.setHostLmsCode("supplier-host")
			.setLocalAgency("supplier-agency");
		final var context = new RequestWorkflowContext()
			.setPatronAgencyCode("borrower-agency")
			.setPatronRequest(patronRequest)
			.setSupplierRequest(supplierRequest);
		final var supplierPlacement = supplyingPlacementService(
			capabilityResolver, transport);
		final var borrowerPlacement = borrowingPlacementService(
			capabilityResolver, transport);
		final var inboundHandler = inboundHandlerFor(context);

		supplierPlacement.place(context).block();
		assertThat(patronRequest.getStatus(),
			is(PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY));
		assertThat(supplierRequest.getLocalId(), is("supplier-remote-request"));
		assertThat(supplierRequest.getLocalStatus(), is("PLACED"));
		assertThat(supplierRequest.getProtocol(), is("iso18626"));
		assertThat(trackingPolicy.schedulesAutomaticPolls(context), is(false));
		assertThat(patronRequest.getNextScheduledPoll(), nullValue());

		inboundHandler.handle(new InboundLifecycleMessage(
			"iso18626",
			LifecycleRole.SUPPLIER,
			LifecycleOperation.PLACE_REQUEST,
			"supplier-host",
			"supplier-remote-request",
			patronRequestId + ":SUPPLIER",
			"CONFIRMED",
			"confirmed",
			"supplier-item",
			"supplier-barcode",
			Instant.parse("2026-06-26T12:10:00Z"),
			"supplier-confirmation-message")).block();
		assertThat(supplierRequest.getLocalStatus(), is("CONFIRMED"));
		assertThat(supplierRequest.getLocalItemId(), is("supplier-item"));
		assertThat(patronRequest.getStatus(), is(PatronRequest.Status.CONFIRMED));

		borrowerPlacement.place(context).block();
		assertThat(patronRequest.getStatus(),
			is(PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY));
		assertThat(patronRequest.getLocalRequestId(),
			is("borrower-remote-request"));
		assertThat(patronRequest.getLocalRequestStatus(), is("PLACED"));
		assertThat(patronRequest.getLocalItemId(), nullValue());
		assertThat(trackingPolicy.schedulesAutomaticPolls(context), is(false));
		assertThat(patronRequest.getNextScheduledPoll(), nullValue());

		inboundHandler.handle(new InboundLifecycleMessage(
			"iso18626",
			LifecycleRole.BORROWER,
			LifecycleOperation.PLACE_REQUEST,
			"borrower-host",
			"borrower-remote-request",
			patronRequestId + ":BORROWER",
			"CONFIRMED",
			"confirmed",
			null,
			null,
			Instant.parse("2026-06-26T12:11:00Z"),
			"borrower-confirmation-message")).block();
		assertThat(patronRequest.getLocalRequestStatus(), is("CONFIRMED"));
		assertThat(patronRequest.getProtocol(), is("iso18626"));
		assertThat(patronRequest.getNextScheduledPoll(), nullValue());
		assertThat(transport.roles(), contains(
			LifecycleRole.SUPPLIER,
			LifecycleRole.BORROWER));
	}

	private static SupplyingAgencyRequestStrategyService supplyingPlacementService(
		LifecycleCapabilityResolver capabilityResolver,
		DeclarativeRequestTransport transport) {

		final var strategy = new Iso18626SupplyingRequestStrategy(transport);
		final var resolver = new SupplyingAgencyRequestStrategyResolver(
			mock(ImperativeSupplyingAgencyRequestStrategy.class),
			List.of(strategy),
			capabilityResolver);

		return new SupplyingAgencyRequestStrategyService(
			resolver, new SupplyingAgencyRequestProjector());
	}

	private static BorrowingAgencyRequestStrategyService borrowingPlacementService(
		LifecycleCapabilityResolver capabilityResolver,
		DeclarativeRequestTransport transport) {

		final var strategy = new Iso18626BorrowingRequestStrategy(transport);
		final var resolver = new BorrowingAgencyRequestStrategyResolver(
			mock(ImperativeBorrowingAgencyRequestStrategy.class),
			List.of(strategy),
			capabilityResolver);

		return new BorrowingAgencyRequestStrategyService(
			resolver, new BorrowingAgencyRequestProjector());
	}

	private static InboundLifecycleMessageHandler inboundHandlerFor(
		RequestWorkflowContext context) {

		final var patronRequest = context.getPatronRequest();
		final var supplierRequest = context.getSupplierRequest();
		final var patronRequestRepository = mock(PatronRequestRepository.class);
		final var supplierRequestRepository = mock(SupplierRequestRepository.class);
		final var contextHelper = mock(RequestWorkflowContextHelper.class);
		final var workflowService = mock(PatronRequestWorkflowService.class);
		final var auditService = mock(PatronRequestAuditService.class);

		when(patronRequestRepository.findById(patronRequest.getId()))
			.thenReturn(Mono.just(patronRequest));
		when(patronRequestRepository.saveOrUpdate(patronRequest))
			.thenReturn(Mono.just(patronRequest));
		when(supplierRequestRepository.saveOrUpdate(supplierRequest))
			.thenReturn(Mono.just(supplierRequest));
		when(contextHelper.fromPatronRequest(patronRequest))
			.thenReturn(Mono.just(context));
		when(auditService.addAuditEntry(eq(patronRequest), anyString(), anyMap()))
			.thenReturn(Mono.empty());
		when(workflowService.progressUsing(context))
			.thenAnswer(invocation -> {
				if (supplierRequest.getLocalStatus() != null
					&& supplierRequest.getLocalStatus().equals("CONFIRMED")
					&& patronRequest.getStatus() == PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY) {
					patronRequest.setStatus(PatronRequest.Status.CONFIRMED);
				}

				return Mono.just(context);
			});

		return new InboundLifecycleMessageHandler(
			patronRequestRepository,
			supplierRequestRepository,
			contextHelper,
			workflowService,
			auditService,
			new InboundLifecycleMessageIdempotencyGuard());
	}

	private static LifecycleCapabilitiesConfiguration dualDeclarativeConfiguration() {
		final var configuration = new LifecycleCapabilitiesConfiguration();
		configuration.getSupplyingAgencyRequest()
			.setStrategy(StrategyType.DECLARATIVE);
		configuration.getSupplyingAgencyRequest()
			.setProtocol("iso18626");
		configuration.getBorrowingAgencyRequest()
			.setStrategy(StrategyType.DECLARATIVE);
		configuration.getBorrowingAgencyRequest()
			.setProtocol("iso18626");
		configuration.getSupplierTracking()
			.setMode(TrackingMode.EVENT_DRIVEN);
		configuration.getSupplierTracking()
			.setProtocol("iso18626");
		configuration.getBorrowerTracking()
			.setMode(TrackingMode.EVENT_DRIVEN);
		configuration.getBorrowerTracking()
			.setProtocol("iso18626");
		return configuration;
	}

	private static class RoleAwareTransport implements DeclarativeRequestTransport {
		private final List<DeclarativeTransportRequest> requests = new ArrayList<>();

		@Override
		public Mono<DeclarativeTransportResponse> send(
			DeclarativeTransportRequest request) {

			requests.add(request);
			return Mono.just(new DeclarativeTransportResponse(
				remoteRequestIdFor(request.role()),
				"PLACED",
				"placed",
				request.role() + "-placement-message"));
		}

		List<LifecycleRole> roles() {
			return requests.stream()
				.map(DeclarativeTransportRequest::role)
				.toList();
		}

		private static String remoteRequestIdFor(LifecycleRole role) {
			return switch (role) {
				case SUPPLIER -> "supplier-remote-request";
				case BORROWER -> "borrower-remote-request";
				case PICKUP -> "pickup-remote-request";
			};
		}
	}
}
