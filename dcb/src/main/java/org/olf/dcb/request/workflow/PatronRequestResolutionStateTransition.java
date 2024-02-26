package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.model.PatronRequest.Status.PATRON_VERIFIED;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;

import java.util.List;
import java.util.Optional;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.RequestWorkflowContext;
import org.olf.dcb.request.resolution.PatronRequestResolutionService;
import org.olf.dcb.request.resolution.Resolution;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;

@Prototype
public class PatronRequestResolutionStateTransition implements PatronRequestStateTransition {
	private static final Logger log = LoggerFactory.getLogger(PatronRequestResolutionStateTransition.class);

	private final PatronRequestResolutionService patronRequestResolutionService;
	private final PatronRequestRepository patronRequestRepository;
	private final SupplierRequestRepository supplierRequestRepository;
	private final PatronRequestAuditService patronRequestAuditService;
	
	// Provider to prevent circular reference exception by allowing lazy access to this singleton.
	private final BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider;

	private static final List<Status> possibleSourceStatus = List.of(Status.PATRON_VERIFIED);
	
	public PatronRequestResolutionStateTransition(
		PatronRequestResolutionService patronRequestResolutionService,
		PatronRequestRepository patronRequestRepository,
		SupplierRequestRepository supplierRequestRepository, PatronRequestAuditService patronRequestAuditService, BeanProvider<PatronRequestWorkflowService> patronRequestWorkflowServiceProvider) {

		this.patronRequestResolutionService = patronRequestResolutionService;
		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestRepository = supplierRequestRepository;
		this.patronRequestAuditService = patronRequestAuditService;
		this.patronRequestWorkflowServiceProvider = patronRequestWorkflowServiceProvider;
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
			.flatMap(this::createAuditEntry)
			.transform(patronRequestWorkflowServiceProvider.get().getErrorTransformerFor(patronRequest))
			.thenReturn(ctx);
	}

	private Mono<PatronRequest> createAuditEntry(PatronRequest patronRequest) {

		// If we are already in an ERROR state, then just don't do anything more
		if (patronRequest.getStatus() == Status.ERROR) return Mono.just(patronRequest);
		
		// Regardless of what the outcome was, set the audit record to VERIFIED -> RESOLVED that seems not right... Because
		// The result of patronRequestResolutionService.resolvePatronRequest may be NO_ITEMS_AVAILABLE_AT_ANY_LENDER...
		log.debug("createAuditEntry {} {}-> {}",patronRequest.getId(),PATRON_VERIFIED, patronRequest.getStatus());

		return patronRequestAuditService.addAuditEntry(patronRequest, PATRON_VERIFIED, patronRequest.getStatus()).thenReturn(patronRequest);
	}
	
	private Mono<Resolution> updatePatronRequest(Resolution resolution) {
		log.debug("updatePatronRequest({})", resolution);

		return updatePatronRequest(resolution.getPatronRequest())
			.map(patronRequest -> new Resolution(patronRequest, resolution.getOptionalSupplierRequest()));
	}

	private Mono<PatronRequest> updatePatronRequest(PatronRequest patronRequest) {
		log.debug("updatePatronRequest({})", patronRequest);

		return Mono.from(patronRequestRepository.update(patronRequest));
	}

	private Mono<Resolution> saveSupplierRequest(Resolution resolution) {
		log.debug("saveSupplierRequest({})", resolution);

		if (resolution.getOptionalSupplierRequest().isEmpty()) {
			return Mono.just(resolution);
		}

		return saveSupplierRequest(resolution.getOptionalSupplierRequest().get())
			.map(supplierRequest -> new Resolution(resolution.getPatronRequest(),
				Optional.of(supplierRequest)));
	}

	private Mono<? extends SupplierRequest> saveSupplierRequest(SupplierRequest supplierRequest) {
		log.debug("saveSupplierRequest({})", supplierRequest);

		return Mono.from(supplierRequestRepository.save(supplierRequest));
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
