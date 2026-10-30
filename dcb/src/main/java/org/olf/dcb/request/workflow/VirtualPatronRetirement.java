package org.olf.dcb.request.workflow;

import static org.olf.dcb.request.fulfilment.PatronRequestAuditService.auditThrowable;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;

import java.util.HashMap;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.storage.PatronRequestRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * When a request finishes and the borrower has no other open request, the lender's system retires
 * the virtual patron, if it does anything for that. Never fails the finalisation.
 */
@Slf4j
@Singleton
public class VirtualPatronRetirement {
	static final String RETIRED = "Virtual patron : retired, the borrower has no open requests";
	static final String NOT_RETIRED = "Virtual patron : could not be retired";

	private final PatronRequestRepository patronRequestRepository;
	private final HostLmsService hostLmsService;
	private final PatronRequestAuditService patronRequestAuditService;

	public VirtualPatronRetirement(PatronRequestRepository patronRequestRepository,
		HostLmsService hostLmsService, PatronRequestAuditService patronRequestAuditService) {

		this.patronRequestRepository = patronRequestRepository;
		this.hostLmsService = hostLmsService;
		this.patronRequestAuditService = patronRequestAuditService;
	}

	public Mono<RequestWorkflowContext> retireIfIdle(RequestWorkflowContext ctx) {
		final var patronRequest = ctx.getPatronRequest();
		final var virtualIdentity = ctx.getPatronVirtualIdentity();
		final var lender = ctx.getLenderSystemCode();
		final var patronId = getValueOrNull(patronRequest, PatronRequest::getPatron, Patron::getId);

		if (virtualIdentity == null || lender == null || patronId == null) {
			return Mono.just(ctx);
		}

		return Mono.from(patronRequestRepository.countOpenRequestsForPatronExcluding(patronId, patronRequest.getId()))
			.filter(open -> open == 0)
			.flatMap(none -> hostLmsService.getClientFor(lender))
			.flatMap(client -> client.retireVirtualPatron(virtualIdentity))
			.flatMap(result -> audit(patronRequest, RETIRED, lender, null))
			.onErrorResume(error -> audit(patronRequest, NOT_RETIRED, lender, error))
			.thenReturn(ctx);
	}

	private Mono<Void> audit(PatronRequest patronRequest, String message, String lender, Throwable error) {
		final var auditData = new HashMap<String, Object>();
		auditData.put("lenderSystemCode", lender);

		if (error != null) {
			log.warn("Could not retire the virtual patron for patron request {} at {}", patronRequest.getId(), lender, error);
			auditThrowable(auditData, "Throwable", error);
		}

		return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData)
			.then()
			.onErrorResume(auditError -> Mono.empty());
	}
}
