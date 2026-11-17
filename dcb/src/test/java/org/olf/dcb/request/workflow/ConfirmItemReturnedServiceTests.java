package org.olf.dcb.request.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_AVAILABLE;
import static org.olf.dcb.security.RoleNames.CONSORTIUM_ADMIN;
import static org.olf.dcb.security.RoleNames.LIBRARY_ADMIN;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.PatronRequestService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.security.AgencyClaims;
import org.olf.dcb.security.CallerScope;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.zalando.problem.ThrowableProblem;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ConfirmItemReturnedServiceTests {
	private static final String SUPPLIER = "SUPPLIER_AG";

	private PatronRequestService patronRequestService;
	private RequestWorkflowContextHelper contextHelper;
	private SupplierRequestRepository supplierRequestRepository;
	private PatronRequestAuditService auditService;
	private PatronRequestWorkflowService workflowService;
	private ConfirmItemReturnedService service;

	@BeforeEach
	void beforeEach() {
		patronRequestService = mock(PatronRequestService.class);
		contextHelper = mock(RequestWorkflowContextHelper.class);
		supplierRequestRepository = mock(SupplierRequestRepository.class);
		auditService = mock(PatronRequestAuditService.class);
		workflowService = mock(PatronRequestWorkflowService.class);

		when(supplierRequestRepository.saveOrUpdate(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));
		when(auditService.addAuditEntry(any(PatronRequest.class), anyString(), anyMap()))
			.thenReturn(Mono.just(new PatronRequestAudit()));
		when(workflowService.progressUsing(any(RequestWorkflowContext.class)))
			.thenAnswer(call -> Mono.just(call.getArgument(0)));

		service = new ConfirmItemReturnedService(patronRequestService, contextHelper,
			supplierRequestRepository, auditService, workflowService);
	}

	@Test
	void aParkedRequestIsReleasedThroughTheWorkflow() {
		final var request = request(Status.AWAITING_RETURN_TO_SUPPLIER);
		final var supplierRequest = supplierRequest("CANCELLED");
		stored(request, supplierRequest, Status.FINALISED);

		StepVerifier.create(service.confirm(request.getId(), consortium()))
			.expectNext(request.getId())
			.verifyComplete();

		assertThat(supplierRequest.getLocalItemStatus(), is(ITEM_AVAILABLE));
		assertThat(supplierRequest.getRawLocalItemStatus(), is(ConfirmItemReturnedService.CONFIRMED_RAW_STATUS));
		verify(supplierRequestRepository).saveOrUpdate(supplierRequest);
		verify(workflowService).progressUsing(any(RequestWorkflowContext.class));
	}

	@Test
	void aReturnInTransitIsCompletedThroughTheWorkflow() {
		final var request = request(Status.RETURN_TRANSIT);
		stored(request, supplierRequest("TRANSIT"), Status.COMPLETED);

		StepVerifier.create(service.confirm(request.getId(), consortium()))
			.expectNext(request.getId())
			.verifyComplete();
	}

	@Test
	void theAuditNamesTheSupplyingLibraryNotThePerson() {
		final var request = request(Status.AWAITING_RETURN_TO_SUPPLIER);
		stored(request, supplierRequest("CANCELLED"), Status.FINALISED);

		StepVerifier.create(service.confirm(request.getId(), library(SUPPLIER)))
			.expectNext(request.getId())
			.verifyComplete();

		@SuppressWarnings("unchecked")
		final ArgumentCaptor<Map<String, Object>> auditData = ArgumentCaptor.forClass(Map.class);
		verify(auditService).addAuditEntry(eq(request), eq(ConfirmItemReturnedService.CONFIRMED), auditData.capture());
		assertThat(auditData.getValue(), hasEntry("confirmedBy", (Object) SUPPLIER));
	}

	@Test
	void anotherLibraryIsRefusedAndNothingIsRecorded() {
		final var request = request(Status.AWAITING_RETURN_TO_SUPPLIER);
		stored(request, supplierRequest("CANCELLED"), Status.FINALISED);

		expectProblem(service.confirm(request.getId(), library("BORROWER_AG")), 403);
		verifyNothingRecorded();
	}

	@Test
	void aLibraryAskingAboutAnUnknownRequestIsRefusedNotTold() {
		final var id = UUID.randomUUID();
		when(patronRequestService.findById(id)).thenReturn(Mono.empty());

		expectProblem(service.confirm(id, library(SUPPLIER)), 403);
	}

	@Test
	void consortiumStaffAskingAboutAnUnknownRequestAreTold() {
		final var id = UUID.randomUUID();
		when(patronRequestService.findById(id)).thenReturn(Mono.empty());

		expectProblem(service.confirm(id, consortium()), 404);
	}

	@ParameterizedTest
	@EnumSource(value = Status.class, names = {
		"PICKUP_TRANSIT", "RECEIVED_AT_PICKUP", "READY_FOR_PICKUP", "LOANED", "CANCELLED", "ERROR",
		"REQUEST_PLACED_AT_SUPPLYING_AGENCY", "FINALISED"
	})
	void aRequestNotWaitingForItsItemIsAConflict(Status status) {
		final var request = request(status);
		stored(request, supplierRequest("TRANSIT"), status);

		expectProblem(service.confirm(request.getId(), consortium()), 409);
		verifyNothingRecorded();
	}

	@Test
	void aRequestWithNoActiveSupplierRequestIsAConflict() {
		final var request = request(Status.AWAITING_RETURN_TO_SUPPLIER);
		stored(request, null, Status.AWAITING_RETURN_TO_SUPPLIER);

		expectProblem(service.confirm(request.getId(), consortium()), 409);
		verifyNothingRecorded();
	}

	@Test
	void aConfirmationTheWorkflowDoesNotActOnIsAServerError() {
		final var request = request(Status.AWAITING_RETURN_TO_SUPPLIER);
		stored(request, supplierRequest("CANCELLED"), Status.AWAITING_RETURN_TO_SUPPLIER);

		expectProblem(service.confirm(request.getId(), consortium()), 500);
	}

	private void stored(PatronRequest request, SupplierRequest supplierRequest, Status statusAfterwards) {
		final var afterwards = PatronRequest.builder().id(request.getId()).status(statusAfterwards).build();

		when(patronRequestService.findById(request.getId())).thenReturn(Mono.just(request), Mono.just(afterwards));
		when(contextHelper.fromPatronRequest(request)).thenReturn(Mono.just(new RequestWorkflowContext()
			.setPatronRequest(request)
			.setSupplierRequest(supplierRequest)));
	}

	private void verifyNothingRecorded() {
		verify(supplierRequestRepository, never()).saveOrUpdate(any());
		verify(workflowService, never()).progressUsing(any(RequestWorkflowContext.class));
	}

	private static PatronRequest request(Status status) {
		return PatronRequest.builder()
			.id(UUID.randomUUID())
			.status(status)
			.build();
	}

	private static SupplierRequest supplierRequest(String itemStatus) {
		return SupplierRequest.builder()
			.id(UUID.randomUUID())
			.hostLmsCode("SUPPLIER_LMS")
			.localItemId("item-1")
			.localAgency(SUPPLIER)
			.localItemStatus(itemStatus)
			.build();
	}

	private static CallerScope consortium() {
		return CallerScope.from(List.of(CONSORTIUM_ADMIN), Map.of());
	}

	private static CallerScope library(String agencyCode) {
		return CallerScope.from(List.of(LIBRARY_ADMIN), Map.of(AgencyClaims.CODE, List.of(agencyCode)));
	}

	private static void expectProblem(Mono<UUID> result, int statusCode) {
		StepVerifier.create(result)
			.expectErrorSatisfies(error -> {
				assertThat(error, instanceOf(ThrowableProblem.class));
				assertThat(((ThrowableProblem) error).getStatus().getStatusCode(), is(statusCode));
			})
			.verify();
	}
}
