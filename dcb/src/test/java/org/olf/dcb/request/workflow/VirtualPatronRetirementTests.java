package org.olf.dcb.request.workflow;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;

import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

/**
 * Whether a virtual patron is retired depends on the borrower's other requests in the database;
 * the lender's system is mocked.
 */
@DcbTest
@TestInstance(PER_CLASS)
class VirtualPatronRetirementTests {
	private static final String LENDER = "RETIRE_LENDER";

	@Inject
	private PatronRequestRepository patronRequestRepository;
	@Inject
	private PatronRequestAuditService patronRequestAuditService;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;

	private HostLmsClient lender;
	private VirtualPatronRetirement retirement;
	private Patron patron;

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		patron = patronFixture.savePatron("home");

		lender = mock(HostLmsClient.class);
		when(lender.retireVirtualPatron(any())).thenReturn(Mono.just("EXPIRED"));
		final var hostLmsService = mock(HostLmsService.class);
		when(hostLmsService.getClientFor(LENDER)).thenReturn(Mono.just(lender));

		retirement = new VirtualPatronRetirement(patronRequestRepository, hostLmsService, patronRequestAuditService);
	}

	@AfterAll
	void afterAll() {
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
	}

	@Test
	void retiresTheVirtualPatronWhenTheBorrowerHasNoOtherOpenRequest() {
		saveRequest(Status.FINALISED);
		final var finishing = saveRequest(Status.COMPLETED);

		singleValueFrom(retirement.retireIfIdle(contextFor(finishing)));

		verify(lender).retireVirtualPatron(any());
		assertThat(auditsOf(finishing), hasItem(VirtualPatronRetirement.RETIRED));
	}

	@Test
	void keepsTheVirtualPatronWhileTheBorrowerHasAnotherOpenRequest() {
		saveRequest(Status.LOANED);
		final var finishing = saveRequest(Status.COMPLETED);

		singleValueFrom(retirement.retireIfIdle(contextFor(finishing)));

		verify(lender, never()).retireVirtualPatron(any());
		assertThat(auditsOf(finishing), not(hasItem(VirtualPatronRetirement.RETIRED)));
	}

	private PatronRequest saveRequest(Status status) {
		return patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(patron)
			.status(status)
			.build());
	}

	private static RequestWorkflowContext contextFor(PatronRequest request) {
		return new RequestWorkflowContext()
			.setPatronRequest(request)
			.setLenderSystemCode(LENDER)
			.setPatronVirtualIdentity(PatronIdentity.builder().localId("virtual-1").build());
	}

	private List<String> auditsOf(PatronRequest request) {
		return patronRequestsFixture.findAuditEntries(request).stream()
			.map(PatronRequestAudit::getBriefDescription)
			.toList();
	}
}
