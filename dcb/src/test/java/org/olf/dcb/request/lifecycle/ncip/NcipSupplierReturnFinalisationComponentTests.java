package org.olf.dcb.request.lifecycle.ncip;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.k_int.peerauth.service.PeerJwksResolver;
import io.micronaut.context.BeanProvider;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.ncip.NcipProtocol;
import org.olf.dcb.core.interaction.ncip.NcipSchemaPath;
import org.olf.dcb.core.interaction.ncip.NcipSchemaValidator;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.core.svc.AlarmsService;
import org.olf.dcb.request.fulfilment.BorrowingAgencyService;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.PickupAgencyService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.request.fulfilment.SupplyingAgencyService;
import org.olf.dcb.request.lifecycle.evidence.DefaultLifecycleEvidenceIngestor;
import org.olf.dcb.request.lifecycle.evidence.DefaultLifecycleEvidenceProjector;
import org.olf.dcb.request.lifecycle.evidence.LifecycleEvidenceIdempotencyGuard;
import org.olf.dcb.request.lifecycle.LifecycleRole;
import org.olf.dcb.request.lifecycle.ncip.peerauth.DcbPeerAuthProperties;
import org.olf.dcb.request.lifecycle.ncip.peerauth.NcipPeerAuthGuard;
import org.olf.dcb.request.lifecycle.tracking.DefaultRequestTrackingPolicy;
import org.olf.dcb.request.lifecycle.tracking.InboundLifecycleMessageHandler;
import org.olf.dcb.request.workflow.CleanupService;
import org.olf.dcb.request.workflow.FinaliseRequestTransition;
import org.olf.dcb.request.workflow.HandleSupplierItemAvailable;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.SupplierRequestsFixture;
import org.olf.dcb.tracking.TrackingHelpers;
import reactor.core.publisher.Mono;

@DcbTest
class NcipSupplierReturnFinalisationComponentTests {
	private final NcipSchemaValidator validator = new NcipSchemaValidator(
		NcipSchemaPath.schemaPath());
	@Inject
	private PatronRequestRepository patronRequestRepository;
	@Inject
	private SupplierRequestRepository supplierRequestRepository;
	@Inject
	private PatronRequestAuditRepository auditRepository;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;

	@BeforeEach
	void cleanDatabase() {
		patronRequestsFixture.deleteAll();
	}

	@Test
	void supplierItemCheckedInProgressesRequestThroughFinalisation() {
		final var patronRequestId = UUID.randomUUID();
		final var patronRequest = PatronRequest.builder()
			.id(patronRequestId)
			.status(PatronRequest.Status.RETURN_TRANSIT)
			.patronHostlmsCode("borrower-host")
			.localRequestId("borrower-request")
			.localItemId("borrower-item")
			.build();
		final var supplierRequest = SupplierRequest.builder()
			.id(UUID.randomUUID())
			.patronRequest(patronRequest)
			.hostLmsCode("supplier-host")
			.localId(patronRequestId + ":SUPPLIER")
			.localItemId("supplier-item")
			.protocol(NcipProtocol.PROTOCOL)
			.isActive(true)
			.build();
		patronRequestsFixture.savePatronRequest(patronRequest);
		supplierRequestsFixture.saveSupplierRequest(supplierRequest);

		final var auditService = new PatronRequestAuditService(
			auditRepository, patronRequestRepository);

		final var contextHelper = mock(RequestWorkflowContextHelper.class);
		when(contextHelper.fromPatronRequest(any(PatronRequest.class)))
			.thenAnswer(invocation -> {
				final PatronRequest loadedPatronRequest = invocation.getArgument(0);
				return Mono.from(supplierRequestRepository.findById(supplierRequest.getId()))
					.map(loadedSupplierRequest -> new RequestWorkflowContext()
						.setPatronRequest(loadedPatronRequest)
						.setSupplierRequest(loadedSupplierRequest)
						.setPatronRequestStateOnEntry(
							loadedPatronRequest.getStatus()));
			});

		final var hostLmsService = mock(HostLmsService.class);
		final var borrowerHostLmsClient = mock(HostLmsClient.class);
		when(hostLmsService.getClientFor("borrower-host"))
			.thenReturn(Mono.just(borrowerHostLmsClient));
		when(borrowerHostLmsClient.updateItemStatus(
			any(HostLmsItem.class), any(HostLmsClient.CanonicalItemState.class)))
			.thenReturn(Mono.empty());

		final var cleanupService = mock(CleanupService.class);
		when(cleanupService.cleanup(any(RequestWorkflowContext.class)))
			.thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
		final var borrowingAgencyService = mock(BorrowingAgencyService.class);
		when(borrowingAgencyService.getItem(any(PatronRequest.class)))
			.thenReturn(Mono.empty());
		final var supplyingAgencyService = mock(SupplyingAgencyService.class);
		when(supplyingAgencyService.getRequest(any(), any())).thenReturn(Mono.empty());

		final var supplierReceivedTransition = new HandleSupplierItemAvailable(
			patronRequestRepository,
			supplierRequestRepository,
			mock(BeanProvider.class),
			auditService,
			contextHelper,
			hostLmsService);
		final var finaliseTransition = new FinaliseRequestTransition(
			auditService,
			supplyingAgencyService,
			borrowingAgencyService,
			mock(PickupAgencyService.class),
			cleanupService);
		final var trackingPolicy = mock(DefaultRequestTrackingPolicy.class);
		when(trackingPolicy.schedulesAutomaticPolls(any())).thenReturn(false);
		final var workflowService = new PatronRequestWorkflowService(
			List.of(supplierReceivedTransition, finaliseTransition),
			patronRequestRepository,
			auditService,
			contextHelper,
			mock(TrackingHelpers.class),
			trackingPolicy,
			mock(AlarmsService.class));
		final var projector = new DefaultLifecycleEvidenceProjector(
			patronRequestRepository,
			supplierRequestRepository,
			contextHelper,
			auditService);
		final var handler = new InboundLifecycleMessageHandler(
			new DefaultLifecycleEvidenceIngestor(
				projector,
				workflowService,
				new LifecycleEvidenceIdempotencyGuard()));
		final var controller = new NcipController(
			handler,
			new NcipInboundXmlMapper(),
			new NcipResponseBuilder(),
			validator,
			disabledPeerAuthGuard());

		final var itemCheckedInXml = supplierItemCheckedIn(patronRequestId);
		assertDoesNotThrow(() -> validator.validate(itemCheckedInXml));
		final var inboundMessage = new NcipInboundXmlMapper().map(itemCheckedInXml);
		assertThat(inboundMessage.role(), is(LifecycleRole.SUPPLIER));
		assertThat(inboundMessage.hostLmsCode(), is("supplier-system"));
		assertThat(inboundMessage.correlationId(),
			is(patronRequestId + ":SUPPLIER"));
		assertThat(inboundMessage.itemId(), is("supplier-item"));
		assertThat(inboundMessage.protocolProperties().get("fromAgencyId"),
			is("supplier-agency"));
		assertThat(inboundMessage.protocolProperties().get("toAgencyId"),
			is("dcb-agency"));

		final var response = controller.receive(itemCheckedInXml).block();

		assertThat(response.body(), containsString("<ItemCheckedInResponse"));
		assertDoesNotThrow(() -> validator.validate(response.body()));
		assertThat(supplierRequestsFixture.findById(supplierRequest.getId())
			.getLocalItemStatus(), is(HostLmsItem.ITEM_RECEIVED));
		final var persistedPatronRequest = patronRequestsFixture.findById(patronRequestId);
		assertThat(persistedPatronRequest.getStatus(),
			is(PatronRequest.Status.FINALISED));
		final var savedAudits = patronRequestsFixture.findAuditEntries(
			persistedPatronRequest);
		assertThat(savedAudits.stream()
			.map(audit -> audit.getFromStatus() + "->" + audit.getToStatus())
			.toList(), hasItem("RETURN_TRANSIT->COMPLETED"));
		assertThat(savedAudits.stream()
			.map(audit -> audit.getFromStatus() + "->" + audit.getToStatus())
			.toList(), hasItem("COMPLETED->FINALISED"));
		verify(borrowerHostLmsClient).updateItemStatus(
			any(HostLmsItem.class),
			org.mockito.ArgumentMatchers.eq(HostLmsClient.CanonicalItemState.COMPLETED));
		verify(cleanupService).cleanup(any(RequestWorkflowContext.class));
		verify(supplyingAgencyService, never()).getPatron(any(RequestWorkflowContext.class));
	}

	private static String supplierItemCheckedIn(UUID patronRequestId) {
		return """
			<NCIPMessage xmlns="http://www.niso.org/2008/ncip" xmlns:ncip="http://www.niso.org/2008/ncip" ncip:version="2.02">
			  <ItemCheckedIn>
			    <InitiationHeader>
			      <FromSystemId>supplier-system</FromSystemId>
			      <FromAgencyId><AgencyId>supplier-agency</AgencyId></FromAgencyId>
			      <ToSystemId>dcb-system</ToSystemId>
			      <ToAgencyId><AgencyId>dcb-agency</AgencyId></ToAgencyId>
			    </InitiationHeader>
			    <UserId>
			      <AgencyId>supplier-agency</AgencyId>
			      <UserIdentifierValue>supplier-user</UserIdentifierValue>
			    </UserId>
			    <ItemId><ItemIdentifierValue>supplier-item</ItemIdentifierValue></ItemId>
			    <Ext>
			      <RequestId>
			        <RequestIdentifierValue>%s:SUPPLIER</RequestIdentifierValue>
			      </RequestId>
			    </Ext>
			  </ItemCheckedIn>
			</NCIPMessage>
			""".formatted(patronRequestId);
	}

	private static NcipPeerAuthGuard disabledPeerAuthGuard() {
		final var resolver = mock(NcipPeerHostLmsResolver.class);
		when(resolver.findBySystemId(any())).thenAnswer(invocation -> reactor.core.publisher.Mono.just(
			DataHostLms.builder()
				.code("supplier-host")
				.clientConfig(Map.of(
					"ncip-system-id", invocation.getArgument(0),
					"ncip-peer-auth-mode", "INSECURE"))
				.build()));
		return new NcipPeerAuthGuard(
			new DcbPeerAuthProperties(),
			resolver,
			mock(PeerJwksResolver.class),
			Runnable::run,
			new NcipResponseBuilder());
	}
}
