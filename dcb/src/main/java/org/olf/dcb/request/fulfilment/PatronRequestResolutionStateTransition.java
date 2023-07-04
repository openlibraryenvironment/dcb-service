package org.olf.dcb.request.fulfilment;

import io.micronaut.context.annotation.Prototype;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.resolution.PatronRequestResolutionService;
import org.olf.dcb.request.resolution.Resolution;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import static org.olf.dcb.request.fulfilment.PatronRequestStatusConstants.PATRON_VERIFIED;

import java.util.Optional;

@Prototype
public class PatronRequestResolutionStateTransition implements PatronRequestStateTransition {
	private static final Logger log = LoggerFactory.getLogger(PatronRequestResolutionStateTransition.class);

	private final PatronRequestResolutionService patronRequestResolutionService;
	private final PatronRequestRepository patronRequestRepository;
	private final SupplierRequestRepository supplierRequestRepository;
	private final PatronRequestTransitionErrorService errorService;


	public PatronRequestResolutionStateTransition(
		PatronRequestResolutionService patronRequestResolutionService,
		PatronRequestRepository patronRequestRepository,
		SupplierRequestRepository supplierRequestRepository,
		PatronRequestTransitionErrorService errorService) {

		this.patronRequestResolutionService = patronRequestResolutionService;
		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestRepository = supplierRequestRepository;
		this.errorService = errorService;
	}

	public String getGuardCondition() {
		return "state=="+PATRON_VERIFIED;
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
			.onErrorResume(error -> errorService.moveRequestToErrorStatus(error, patronRequest));
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
}
