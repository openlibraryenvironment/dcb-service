package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.model.PatronRequest.Status.PATRON_VERIFIED;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;

import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.PatronRequestService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.resolution.PatronRequestResolutionService;
import org.olf.dcb.request.resolution.Resolution;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.storage.SupplierRequestRepository;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Prototype
public class PatronRequestResolutionStateTransition implements PatronRequestStateTransition {
	private final PatronRequestResolutionService patronRequestResolutionService;
	private final PatronRequestAuditService patronRequestAuditService;
	
	// Provider to prevent circular reference exception by allowing lazy access to this singleton.
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;
	private final BeanProvider<PatronRequestService> patronRequestServiceProvider;
	private final SupplierRequestService supplierRequestService;

	private static final List<Status> possibleSourceStatus = List.of(PATRON_VERIFIED);

	public PatronRequestResolutionStateTransition(
		PatronRequestResolutionService patronRequestResolutionService,
		SupplierRequestRepository supplierRequestRepository,
		PatronRequestAuditService patronRequestAuditService,
		BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider,
		BeanProvider<PatronRequestService> patronRequestServiceProvider,
		SupplierRequestService supplierRequestService) {

		this.patronRequestResolutionService = patronRequestResolutionService;
		this.patronRequestAuditService = patronRequestAuditService;
		this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
		this.patronRequestServiceProvider = patronRequestServiceProvider;
		this.supplierRequestService = supplierRequestService;
	}

	@Override
	public Mono<RequestWorkflowContext> attempt(RequestWorkflowContext ctx) {
		PatronRequest patronRequest = ctx.getPatronRequest();

		log.info("PatronRequestResolutionStateTransition attempt for {}", patronRequest);

		return patronRequestResolutionService.resolvePatronRequest(patronRequest)
			.doOnSuccess(resolution -> log.debug("Resolved to: {}", resolution))
			.doOnError(error -> log.error("Error occurred during resolution: {}", error.getMessage()))
			// Trail switching these so we can set current supplier request on patron request
			.flatMap(this::saveSupplierRequest)
			.flatMap(this::updatePatronRequest)
			.map(Resolution::getPatronRequest)
			.transform(patronRequestWorkflowServiceProvider.get().getErrorTransformerFor(patronRequest))
			.thenReturn(ctx);
	}

	private Mono<Resolution> updatePatronRequest(Resolution resolution) {
		log.debug("updatePatronRequest({})", resolution);

		final var patronRequestService = patronRequestServiceProvider.get();

		return patronRequestService.updatePatronRequest(resolution.getPatronRequest())
			.map(patronRequest -> new Resolution(patronRequest, resolution.getOptionalSupplierRequest()));
	}

	private Mono<Resolution> saveSupplierRequest(Resolution resolution) {
		log.debug("saveSupplierRequest({})", resolution);

		if (resolution.getOptionalSupplierRequest().isEmpty()) {
			return Mono.just(resolution);
		}

		return supplierRequestService.saveSupplierRequest(resolution.getOptionalSupplierRequest().get())
			.map(supplierRequest -> new Resolution(resolution.getPatronRequest(),
				Optional.of(supplierRequest)));
	}

	@Override
	public boolean isApplicableFor(RequestWorkflowContext ctx) {
		return getPossibleSourceStatus().contains(ctx.getPatronRequest().getStatus());
	}

	@Override
	public List<Status> getPossibleSourceStatus() {
		return possibleSourceStatus;
	}

	// getTargetStatus tells us where we're trying to get to BUT be aware that the transitions can have error states so the Status
	// outcome can be OTHER than the status listed here. This is used for goal-seeking when trying to work out which transitions to
	// apply - it's not a statement of what the status WILL be after applying the transition. n this case - NO_ITEMS_AT_ANY_SUPPLIER can
	// happen
	@Override
	public Optional<Status> getTargetStatus() {
		return Optional.of(RESOLVED);
	}

  @Override
  public String getName() {
    return "PatronRequestResolutionStateTransition";
  }

  @Override
  public boolean attemptAutomatically() {
    return true;
  }
}
