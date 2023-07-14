package org.olf.dcb.request.workflow;

import static org.olf.dcb.core.model.PatronRequest.Status.PATRON_VERIFIED;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;

import java.util.Optional;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
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
	public Mono<PatronRequest> attempt(PatronRequest patronRequest) {
		log.debug("attempt({})", patronRequest);

		return patronRequestResolutionService.resolvePatronRequest(patronRequest)
			.doOnSuccess(resolution -> log.debug("Resolved to: {}", resolution))
			.doOnError(error -> log.error("Error occurred during resolution: {}", error.getMessage()))
			.flatMap(this::updatePatronRequest)
			.flatMap(this::saveSupplierRequest)
			.map(Resolution::getPatronRequest)
			.flatMap(this::createAuditEntry)
			.transform(patronRequestWorkflowServiceProvider.get().getErrorTransformerFor(patronRequest));
	}

	private Mono<PatronRequest> createAuditEntry(PatronRequest patronRequest) {
		
		if (patronRequest.getStatus() == Status.ERROR) return Mono.just(patronRequest);
		
		return patronRequestAuditService.addAuditEntry(patronRequest, PATRON_VERIFIED, RESOLVED).thenReturn(patronRequest);
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
	public boolean isApplicableFor(PatronRequest pr) {

		return pr.getStatus() == PATRON_VERIFIED;
	}
}
