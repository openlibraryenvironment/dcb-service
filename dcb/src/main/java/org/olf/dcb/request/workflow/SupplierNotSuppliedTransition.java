package org.olf.dcb.request.workflow;

import java.util.Optional;
import java.util.stream.Stream;

import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;


/**
 * SupplierNotSuppliedTransition - Called in response to a request in state NOT_SUPPLIED_CURRENT_SUPPLIER 
 * the needed action is to see if there are other possible suppliers for which there is not an outstanding 
 * supplier request, and if so, re-enter the workflow at the PlaceRequestAtSupplyingAgency step with that
 * new provider
 */
@Prototype
public class SupplierNotSuppliedTransition implements PatronRequestStateTransition {
	private static final Logger log = LoggerFactory.getLogger(SupplierNotSuppliedTransition.class);

	// Provider to prevent circular reference exception by allowing lazy access to
	// this singleton.
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;
	private final PatronRequestAuditService patronRequestAuditService;

	
	public SupplierNotSuppliedTransition (
		BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider,
    PatronRequestAuditService patronRequestAuditService
    ) {
		this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
    this.patronRequestAuditService = patronRequestAuditService;
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		return ctx.getPatronRequest().getStatus() == Status.NOT_SUPPLIED_CURRENT_SUPPLIER;
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {

		PatronRequest patronRequest = ctx.getPatronRequest();

		log.info("SupplierNotSuppliedTransition firing for {}",patronRequest);
		Status old_state = patronRequest.getStatus();

		// For now, return ERROR
    patronRequest.setStatus(Status.ERROR);
    return patronRequestAuditService.addAuditEntry(patronRequest, old_state, Status.ERROR)
			.thenReturn(ctx);
	}

	@Override
	public Optional<Status> getTargetStatus() {
		return Optional.of(Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY);
	}
	
	@Override
	public boolean attemptAutomatically() {
		return true;
	}

	@Override
	public String getName() {
		return "SupplierNotSuppliedTransition";
	}
}

