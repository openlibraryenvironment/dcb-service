package org.olf.dcb.request.workflow;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;
import static org.olf.dcb.core.remediation.RemediationCode.FOLIO_REQUEST_POLICY_REFUSED;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.SupplierRefusedRequestProblem;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.remediation.RemediationCatalogue;
import org.olf.dcb.core.svc.AlarmsService;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContextHelper;
import org.olf.dcb.request.lifecycle.tracking.RequestTrackingPolicy;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.tracking.TrackingHelpers;

import io.micronaut.core.io.ResourceResolver;
import io.micronaut.serde.ObjectMapper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class RemediableErrorTests {
	private PatronRequestRepository repository;
	private PatronRequestAuditService auditService;
	private PatronRequestWorkflowService workflow;
	private PatronRequest patronRequest;
	private final AtomicReference<Map<String, Object>> errorAuditData = new AtomicReference<>();

	// The test instance is shared across tests (junit-platform.properties), so the mocks are not
	@BeforeEach
	void beforeEach() {
		repository = mock(PatronRequestRepository.class);
		auditService = mock(PatronRequestAuditService.class);
		when(repository.updateStatusWithError(any(), any())).thenReturn(Mono.empty());
		errorAuditData.set(null);
		when(auditService.addErrorAuditEntry(any(), any(), any(), any())).thenAnswer(invocation -> {
			errorAuditData.set(invocation.getArgument(3));
			return Mono.empty();
		});

		workflow = new PatronRequestWorkflowService(List.of(), repository, auditService,
			mock(RequestWorkflowContextHelper.class), mock(TrackingHelpers.class),
			mock(RequestTrackingPolicy.class), mock(AlarmsService.class),
			new RemediationCatalogue(new ResourceResolver(), ObjectMapper.getDefault()));

		patronRequest = PatronRequest.builder().id(randomUUID()).status(RESOLVED).build();
	}

	@Test
	void anErrorThatNamesItsRemediationRecordsWhatToDoAboutIt() {
		final var refusal = new SupplierRefusedRequestProblem("COLLEGE", FOLIO_REQUEST_POLICY_REFUSED,
			Map.of("hostLms", "COLLEGE", "requestType", "Hold", "patronGroup", "DCB patrons"), "raw", null, null);

		failWith(refusal);

		final var summary = "COLLEGE's request policy does not allow a Hold request for patron group "
			+ "\"DCB patrons\" on this item";

		verify(repository).updateStatusWithError(patronRequest.getId(), summary);

		final var auditData = auditDataOfTheErrorEntry(summary);
		assertThat(auditData, hasKey("remediation"));

		@SuppressWarnings("unchecked")
		final var remediation = (Map<String, Object>) auditData.get("remediation");
		assertThat(remediation, hasEntry("code", "FOLIO_REQUEST_POLICY_REFUSED"));
		assertThat(remediation, hasEntry("audience", "SUPPLYING_LIBRARY"));
		assertThat(remediation, hasEntry("summary", summary));
		assertThat(remediation, hasKey("nextSteps"));
	}

	@Test
	void anyOtherErrorKeepsItsOwnMessageAndNoRemediation() {
		failWith(new IllegalStateException("Something else went wrong"));

		verify(repository).updateStatusWithError(patronRequest.getId(), "Something else went wrong");
		assertThat(auditDataOfTheErrorEntry("Something else went wrong"), not(hasKey("remediation")));
	}

	private void failWith(RuntimeException error) {
		assertThrows(error.getClass(), () -> Flux.<PatronRequest>error(error)
			.transform(workflow.getErrorTransformerFor(patronRequest))
			.blockLast());
	}

	private Map<String, Object> auditDataOfTheErrorEntry(String message) {
		verify(auditService).addErrorAuditEntry(eq(patronRequest), eq(RESOLVED), eq(message), any());

		return errorAuditData.get();
	}
}
