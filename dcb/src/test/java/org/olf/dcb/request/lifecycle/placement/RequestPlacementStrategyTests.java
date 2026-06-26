package org.olf.dcb.request.lifecycle.placement;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.BorrowingAgencyService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;
import org.olf.dcb.request.lifecycle.DeclarativeRequestTransport;
import org.olf.dcb.request.lifecycle.DeclarativeTransportRequest;
import org.olf.dcb.request.lifecycle.DeclarativeTransportResponse;
import org.olf.dcb.request.lifecycle.LifecycleCapabilitiesConfiguration;
import org.olf.dcb.request.lifecycle.LifecycleCapabilityConfigurationException;
import org.olf.dcb.request.lifecycle.LifecycleCapabilityResolver;
import org.olf.dcb.request.lifecycle.LifecycleOperation;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.olf.dcb.request.lifecycle.StrategyType;
import org.olf.dcb.request.lifecycle.TrackingMode;
import org.olf.dcb.request.lifecycle.iso18626.Iso18626BorrowingRequestStrategy;
import org.olf.dcb.request.lifecycle.iso18626.Iso18626SupplyingRequestStrategy;

import reactor.core.publisher.Mono;

class RequestPlacementStrategyTests {
	@Test
	void supplyingResolverDefaultsToImperativeStrategy() {
		final var imperativeStrategy = mock(ImperativeSupplyingAgencyRequestStrategy.class);
		final var resolver = new SupplyingAgencyRequestStrategyResolver(
			imperativeStrategy, List.of(), defaultCapabilityResolver());
		final var context = new RequestWorkflowContext();

		final var strategy = resolver.resolve(context, LifecycleOperation.PLACE_REQUEST);

		assertThat(strategy, sameInstance(imperativeStrategy));
	}

	@Test
	void borrowingResolverDefaultsToImperativeStrategyForPlaceAndRevise() {
		final var imperativeStrategy = mock(ImperativeBorrowingAgencyRequestStrategy.class);
		final var resolver = new BorrowingAgencyRequestStrategyResolver(
			imperativeStrategy, List.of(), defaultCapabilityResolver());
		final var context = new RequestWorkflowContext();

		assertThat(resolver.resolve(context, LifecycleOperation.PLACE_REQUEST),
			sameInstance(imperativeStrategy));
		assertThat(resolver.resolve(context, LifecycleOperation.REVISE_REQUEST),
			sameInstance(imperativeStrategy));
	}

	@Test
	void capabilityResolverDefaultsMissingConfigToImperativeAndScheduledPoll() {
		final var resolver = defaultCapabilityResolver();

		assertThat(resolver.placementStrategy(LifecycleRole.SUPPLIER),
			is(StrategyType.IMPERATIVE));
		assertThat(resolver.placementStrategy(LifecycleRole.BORROWER),
			is(StrategyType.IMPERATIVE));
		assertThat(resolver.trackingMode(LifecycleRole.SUPPLIER),
			is(TrackingMode.SCHEDULED_POLL));
		assertThat(resolver.trackingMode(LifecycleRole.BORROWER),
			is(TrackingMode.SCHEDULED_POLL));
	}

	@Test
	void explicitImperativeConfigSelectsImperativeStrategy() {
		final var configuration = new LifecycleCapabilitiesConfiguration();
		configuration.getSupplyingAgencyRequest()
			.setStrategy(StrategyType.IMPERATIVE);
		configuration.getBorrowingAgencyRequest()
			.setStrategy(StrategyType.IMPERATIVE);
		final var resolver = new LifecycleCapabilityResolver(configuration);

		assertThat(resolver.placementStrategy(LifecycleRole.SUPPLIER),
			is(StrategyType.IMPERATIVE));
		assertThat(resolver.placementStrategy(LifecycleRole.BORROWER),
			is(StrategyType.IMPERATIVE));
	}

	@Test
	void declarativeIso18626ConfigSelectsDeclarativeStrategy() {
		final var configuration = new LifecycleCapabilitiesConfiguration();
		configuration.getSupplyingAgencyRequest()
			.setStrategy(StrategyType.DECLARATIVE);
		configuration.getSupplyingAgencyRequest()
			.setProtocol("iso18626");
		final var resolver = new LifecycleCapabilityResolver(configuration);

		assertThat(resolver.placementStrategy(LifecycleRole.SUPPLIER),
			is(StrategyType.DECLARATIVE));
	}

	@Test
	void declarativeConfigWithoutProtocolFailsFast() {
		final var configuration = new LifecycleCapabilitiesConfiguration();
		configuration.getSupplyingAgencyRequest()
			.setStrategy(StrategyType.DECLARATIVE);
		final var resolver = new LifecycleCapabilityResolver(configuration);

		final var error = assertThrows(
			LifecycleCapabilityConfigurationException.class,
			() -> resolver.placementStrategy(LifecycleRole.SUPPLIER));

		assertThat(error.getMessage(), containsString("explicit protocol"));
	}

	@Test
	void explicitDeclarativePlacementFailsWhenNoDeclarativeStrategyIsAvailable() {
		final var configuration = new LifecycleCapabilitiesConfiguration();
		configuration.getSupplyingAgencyRequest()
			.setStrategy(StrategyType.DECLARATIVE);
		configuration.getSupplyingAgencyRequest()
			.setProtocol("iso18626");
		final var resolver = new SupplyingAgencyRequestStrategyResolver(
			mock(ImperativeSupplyingAgencyRequestStrategy.class),
			List.of(),
			new LifecycleCapabilityResolver(configuration));

		final var error = assertThrows(
			LifecycleCapabilityConfigurationException.class,
			() -> resolver.resolve(new RequestWorkflowContext(),
				LifecycleOperation.PLACE_REQUEST));

		assertThat(error.getMessage(),
			containsString("declarative request strategy is not available"));
	}

	@Test
	void explicitDeclarativePlacementSelectsAvailableSupplyingStrategy() {
		final var configuration = new LifecycleCapabilitiesConfiguration();
		configuration.getSupplyingAgencyRequest()
			.setStrategy(StrategyType.DECLARATIVE);
		configuration.getSupplyingAgencyRequest()
			.setProtocol("iso18626");
		final var declarativeStrategy = mock(SupplyingAgencyRequestStrategy.class);
		when(declarativeStrategy.type()).thenReturn(StrategyType.DECLARATIVE);
		final var resolver = new SupplyingAgencyRequestStrategyResolver(
			mock(ImperativeSupplyingAgencyRequestStrategy.class),
			List.of(declarativeStrategy),
			new LifecycleCapabilityResolver(configuration));

		final var strategy = resolver.resolve(new RequestWorkflowContext(),
			LifecycleOperation.PLACE_REQUEST);

		assertThat(strategy, sameInstance(declarativeStrategy));
	}

	@Test
	void explicitDeclarativePlacementSelectsAvailableBorrowingStrategy() {
		final var configuration = new LifecycleCapabilitiesConfiguration();
		configuration.getBorrowingAgencyRequest()
			.setStrategy(StrategyType.DECLARATIVE);
		configuration.getBorrowingAgencyRequest()
			.setProtocol("iso18626");
		final var declarativeStrategy = mock(BorrowingAgencyRequestStrategy.class);
		when(declarativeStrategy.type()).thenReturn(StrategyType.DECLARATIVE);
		final var resolver = new BorrowingAgencyRequestStrategyResolver(
			mock(ImperativeBorrowingAgencyRequestStrategy.class),
			List.of(declarativeStrategy),
			new LifecycleCapabilityResolver(configuration));

		final var strategy = resolver.resolve(new RequestWorkflowContext(),
			LifecycleOperation.PLACE_REQUEST);

		assertThat(strategy, sameInstance(declarativeStrategy));
	}

	@Test
	void eventDrivenTrackingRequiresProtocol() {
		final var configuration = new LifecycleCapabilitiesConfiguration();
		configuration.getSupplierTracking()
			.setMode(TrackingMode.EVENT_DRIVEN);
		final var resolver = new LifecycleCapabilityResolver(configuration);

		final var error = assertThrows(
			LifecycleCapabilityConfigurationException.class,
			() -> resolver.trackingMode(LifecycleRole.SUPPLIER));

		assertThat(error.getMessage(), containsString("explicit protocol"));
	}

	@Test
	void eventDrivenIso18626TrackingCanBeSelected() {
		final var configuration = new LifecycleCapabilitiesConfiguration();
		configuration.getSupplierTracking()
			.setMode(TrackingMode.EVENT_DRIVEN);
		configuration.getSupplierTracking()
			.setProtocol("iso18626");
		final var resolver = new LifecycleCapabilityResolver(configuration);

		assertThat(resolver.trackingMode(LifecycleRole.SUPPLIER),
			is(TrackingMode.EVENT_DRIVEN));
	}

	@Test
	void imperativeSupplyingStrategyDelegatesToSupplyingAgencyService() {
		final var supplyingAgencyService = mock(SupplyingAgencyService.class);
		final var strategy = new ImperativeSupplyingAgencyRequestStrategy(
			supplyingAgencyService);
		final var patronRequest = new PatronRequest();
		final var supplierRequest = new SupplierRequest();
		final var context = new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setSupplierRequest(supplierRequest);

		when(supplyingAgencyService.placePatronRequestAtSupplyingAgency(
			patronRequest))
			.thenReturn(Mono.just(patronRequest));

		final var result = singleValueFrom(strategy.place(context));

		assertThat(strategy.type(), is(StrategyType.IMPERATIVE));
		assertThat(result.patronRequest(), sameInstance(patronRequest));
		assertThat(result.supplierRequest(), sameInstance(supplierRequest));
		verify(supplyingAgencyService).placePatronRequestAtSupplyingAgency(
			patronRequest);
	}

	@Test
	void imperativeBorrowingStrategyDelegatesPlaceToBorrowingAgencyService() {
		final var borrowingAgencyService = mock(BorrowingAgencyService.class);
		final var strategy = new ImperativeBorrowingAgencyRequestStrategy(
			borrowingAgencyService);
		final var context = new RequestWorkflowContext();
		final var patronRequest = new PatronRequest();

		when(borrowingAgencyService.placePatronRequestAtBorrowingAgency(context))
			.thenReturn(Mono.just(patronRequest));

		final var result = singleValueFrom(strategy.place(context));

		assertThat(strategy.type(), is(StrategyType.IMPERATIVE));
		assertThat(result.patronRequest(), sameInstance(patronRequest));
		verify(borrowingAgencyService).placePatronRequestAtBorrowingAgency(
			context);
	}

	@Test
	void imperativeBorrowingStrategyDelegatesReviseToBorrowingAgencyService() {
		final var borrowingAgencyService = mock(BorrowingAgencyService.class);
		final var strategy = new ImperativeBorrowingAgencyRequestStrategy(
			borrowingAgencyService);
		final var context = new RequestWorkflowContext();
		final var patronRequest = new PatronRequest();

		when(borrowingAgencyService.updatePatronRequestAtBorrowingAgency(context))
			.thenReturn(Mono.just(patronRequest));

		final var result = singleValueFrom(strategy.revise(context));

		assertThat(strategy.type(), is(StrategyType.IMPERATIVE));
		assertThat(result.patronRequest(), sameInstance(patronRequest));
		verify(borrowingAgencyService).updatePatronRequestAtBorrowingAgency(
			context);
	}

	@Test
	void isoSupplyingStrategySendsDeclarativeRequestAndReturnsCanonicalEvidence() {
		final var patronRequestId = UUID.randomUUID();
		final var transport = new CapturingTransport(new DeclarativeTransportResponse(
			"supplier-remote-request-1",
			"PLACED",
			"placed",
			"raw-message-1"));
		final var strategy = new Iso18626SupplyingRequestStrategy(transport);
		final var supplierRequest = new SupplierRequest()
			.setHostLmsCode("supplier-host")
			.setLocalAgency("supplier-agency");
		final var context = new RequestWorkflowContext()
			.setPatronRequest(new PatronRequest().setId(patronRequestId))
			.setSupplierRequest(supplierRequest);

		final var result = singleValueFrom(strategy.place(context));
		final var request = transport.onlyRequest();

		assertThat(request.protocol(), is("iso18626"));
		assertThat(request.role(), is(LifecycleRole.SUPPLIER));
		assertThat(request.operation(), is(LifecycleOperation.PLACE_REQUEST));
		assertThat(request.hostLmsCode(), is("supplier-host"));
		assertThat(request.agencyCode(), is("supplier-agency"));
		assertThat(request.correlationId(),
			is(patronRequestId + ":SUPPLIER"));
		assertThat(result.role(), is(LifecycleRole.SUPPLIER));
		assertThat(result.protocol(), is("iso18626"));
		assertThat(result.correlationId(),
			is(patronRequestId + ":SUPPLIER"));
		assertThat(result.remoteRequestId(), is("supplier-remote-request-1"));
		assertThat(result.status(), is("PLACED"));
		assertThat(result.rawStatus(), is("placed"));
		assertThat(result.rawMessageReference(), is("raw-message-1"));
		assertThat(result.localRequestId(), is("supplier-remote-request-1"));
		assertThat(result.localItemId(), nullValue());
		assertThat(result.localItemBarcode(), nullValue());
	}

	@Test
	void isoBorrowingStrategySendsDeclarativeRequestWithoutVirtualArtifacts() {
		final var patronRequestId = UUID.randomUUID();
		final var transport = new CapturingTransport(new DeclarativeTransportResponse(
			"borrower-remote-request-1",
			"PLACED",
			"placed",
			"raw-message-2"));
		final var strategy = new Iso18626BorrowingRequestStrategy(transport);
		final var context = new RequestWorkflowContext()
			.setPatronAgencyCode("borrower-agency")
			.setPatronRequest(new PatronRequest()
				.setId(patronRequestId)
				.setPatronHostlmsCode("borrower-host"));

		final var result = singleValueFrom(strategy.place(context));
		final var request = transport.onlyRequest();

		assertThat(request.protocol(), is("iso18626"));
		assertThat(request.role(), is(LifecycleRole.BORROWER));
		assertThat(request.operation(), is(LifecycleOperation.PLACE_REQUEST));
		assertThat(request.hostLmsCode(), is("borrower-host"));
		assertThat(request.agencyCode(), is("borrower-agency"));
		assertThat(request.correlationId(),
			is(patronRequestId + ":BORROWER"));
		assertThat(result.role(), is(LifecycleRole.BORROWER));
		assertThat(result.protocol(), is("iso18626"));
		assertThat(result.correlationId(),
			is(patronRequestId + ":BORROWER"));
		assertThat(result.remoteRequestId(), is("borrower-remote-request-1"));
		assertThat(result.localRequestId(), is("borrower-remote-request-1"));
		assertThat(result.createdVirtualBib(), is(false));
		assertThat(result.createdVirtualItem(), is(false));
		assertThat(result.localBibId(), nullValue());
		assertThat(result.localItemId(), nullValue());
	}

	@Test
	void supplyingProjectorAppliesCompatibilityEvidence() {
		final var patronRequest = new PatronRequest();
		final var supplierRequest = new SupplierRequest();
		final var context = new RequestWorkflowContext()
			.setPatronRequest(patronRequest)
			.setSupplierRequest(supplierRequest);
		final var projector = new SupplyingAgencyRequestProjector();

		projector.apply(context, new SupplyingAgencyRequestResult(
			null,
			null,
			"supplier-host",
			"supplier-request-1",
			"PLACED",
			"placed",
			"supplier-item-1",
			"supplier-barcode-1"));

		assertThat(context.getPatronRequest(), sameInstance(patronRequest));
		assertThat(context.getSupplierRequest(), sameInstance(supplierRequest));
		assertThat(patronRequest.getStatus(),
			is(PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY));
		assertThat(supplierRequest.getLocalId(), is("supplier-request-1"));
		assertThat(supplierRequest.getLocalStatus(), is("PLACED"));
		assertThat(supplierRequest.getRawLocalStatus(), is("placed"));
		assertThat(supplierRequest.getLocalItemId(), is("supplier-item-1"));
		assertThat(supplierRequest.getLocalItemBarcode(),
			is("supplier-barcode-1"));
	}

	@Test
	void borrowingProjectorAppliesCompatibilityEvidenceForRealArtifacts() {
		final var patronRequest = new PatronRequest();
		final var context = new RequestWorkflowContext()
			.setPatronRequest(patronRequest);
		final var projector = new BorrowingAgencyRequestProjector();

		projector.apply(context, new BorrowingAgencyRequestResult(
			null,
			"borrower-host",
			"borrower-request-1",
			"CONFIRMED",
			"confirmed",
			"virtual-bib-1",
			"virtual-item-1",
			"AVAILABLE",
			true,
			true));

		assertThat(context.getPatronRequest(), sameInstance(patronRequest));
		assertThat(patronRequest.getStatus(),
			is(PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY));
		assertThat(patronRequest.getLocalRequestId(), is("borrower-request-1"));
		assertThat(patronRequest.getLocalRequestStatus(), is("CONFIRMED"));
		assertThat(patronRequest.getRawLocalRequestStatus(), is("confirmed"));
		assertThat(patronRequest.getLocalBibId(), is("virtual-bib-1"));
		assertThat(patronRequest.getLocalItemId(), is("virtual-item-1"));
		assertThat(patronRequest.getLocalItemStatus(), is("AVAILABLE"));
	}

	@Test
	void borrowingProjectorDoesNotFabricateVirtualArtifactFields() {
		final var patronRequest = new PatronRequest()
			.setLocalBibId("existing-bib")
			.setLocalItemId("existing-item")
			.setLocalItemStatus("EXISTING");
		final var context = new RequestWorkflowContext()
			.setPatronRequest(patronRequest);
		final var projector = new BorrowingAgencyRequestProjector();

		projector.apply(context, new BorrowingAgencyRequestResult(
			null,
			"borrower-host",
			"borrower-request-1",
			"CONFIRMED",
			"confirmed",
			"declarative-bib-evidence",
			"declarative-item-evidence",
			"DECLARED",
			false,
			false));

		assertThat(patronRequest.getStatus(),
			is(PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY));
		assertThat(patronRequest.getLocalRequestId(), is("borrower-request-1"));
		assertThat(patronRequest.getLocalBibId(), is("existing-bib"));
		assertThat(patronRequest.getLocalItemId(), is("existing-item"));
		assertThat(patronRequest.getLocalItemStatus(), is("EXISTING"));
	}

	@Test
	void borrowingProjectorCanProjectRequestEvidenceWithoutArtifacts() {
		final var patronRequest = new PatronRequest();
		final var context = new RequestWorkflowContext()
			.setPatronRequest(patronRequest);
		final var projector = new BorrowingAgencyRequestProjector();

		projector.apply(context, new BorrowingAgencyRequestResult(
			null,
			"borrower-host",
			"borrower-request-1",
			"ACCEPTED",
			"accepted",
			null,
			null,
			null,
			false,
			false));

		assertThat(patronRequest.getStatus(),
			is(PatronRequest.Status.REQUEST_PLACED_AT_BORROWING_AGENCY));
		assertThat(patronRequest.getLocalRequestId(), is("borrower-request-1"));
		assertThat(patronRequest.getLocalRequestStatus(), is("ACCEPTED"));
		assertThat(patronRequest.getLocalBibId(), nullValue());
		assertThat(patronRequest.getLocalItemId(), nullValue());
	}

	private static LifecycleCapabilityResolver defaultCapabilityResolver() {
		return new LifecycleCapabilityResolver(
			new LifecycleCapabilitiesConfiguration());
	}

	private static class CapturingTransport implements DeclarativeRequestTransport {
		private final DeclarativeTransportResponse response;
		private final List<DeclarativeTransportRequest> requests = new ArrayList<>();

		CapturingTransport(DeclarativeTransportResponse response) {
			this.response = response;
		}

		@Override
		public Mono<DeclarativeTransportResponse> send(
			DeclarativeTransportRequest request) {

			requests.add(request);
			return Mono.just(response);
		}

		DeclarativeTransportRequest onlyRequest() {
			assertThat(requests.size(), is(1));
			return requests.getFirst();
		}
	}
}
