package org.olf.reshare.dcb.request.fulfilment;

import io.micronaut.context.annotation.Prototype;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.request.resolution.PatronRequestResolutionService;
import org.olf.reshare.dcb.request.resolution.Resolution;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.olf.reshare.dcb.storage.SupplierRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.olf.reshare.dcb.request.fulfilment.SupplierRequestStatusCode.PENDING;

@Prototype
public class PatronRequestResolutionStateTransition implements PatronRequestStateTransition {
	private static final Logger log = LoggerFactory.getLogger(PatronRequestResolutionStateTransition.class);

	private final PatronRequestResolutionService patronRequestResolutionService;
	private final PatronRequestRepository patronRequestRepository;
	private final SupplierRequestRepository supplierRequestRepository;


	public PatronRequestResolutionStateTransition(
		PatronRequestResolutionService patronRequestResolutionService,
		PatronRequestRepository patronRequestRepository,
		SupplierRequestRepository supplierRequestRepository) {

		this.patronRequestResolutionService = patronRequestResolutionService;
		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestRepository = supplierRequestRepository;
	}

	@Override
	public Mono<PatronRequest> attempt(PatronRequest patronRequest) {
		log.debug("makeTransition({})", patronRequest);

		return patronRequestResolutionService.resolvePatronRequest(patronRequest)
			.doOnSuccess(resolution -> log.debug("Resolved to: {}", resolution))
			.doOnError(error -> log.error("Error occurred during resolution: {}", error.getMessage()))
			.flatMap(this::updatePatronRequest)
			.flatMap(this::setSupplierRequestStatus)
			.flatMap(this::saveSupplierRequest)
			.map(Resolution::getPatronRequest);
	}

	private Mono<Resolution> updatePatronRequest(Resolution resolution) {
		return updatePatronRequest(resolution.getPatronRequest())
			.map(patronRequest -> new Resolution(patronRequest, resolution.getOptionalSupplierRequest()));
	}

	private Mono<Resolution> setSupplierRequestStatus(Resolution resolution) {
		log.debug("setSupplierRequestStatus({})", resolution);
		return Mono.just(resolution)
			.map(res -> res.getOptionalSupplierRequest().orElseThrow())
			.doOnSuccess(supplierRequest -> supplierRequest.setStatusCode(PENDING))
			.map(supplierRequest -> new Resolution(resolution.getPatronRequest(), Optional.of(supplierRequest)))
			// for when there is no supplier request
			.onErrorResume(NoSuchElementException.class, error -> {
				log.debug("NoSuchElementException occurred: {}", error.getMessage());
				return Mono.empty();
			});
	}

	private Mono<PatronRequest> updatePatronRequest(PatronRequest patronRequest) {
		log.debug("updatePatronRequest {}", patronRequest);

		return Mono.from(patronRequestRepository.update(patronRequest));
	}

	private Mono<Resolution> saveSupplierRequest(Resolution resolution) {

		if (resolution.getOptionalSupplierRequest().isEmpty()) {
			return Mono.just(resolution);
		}

		return saveSupplierRequest(resolution.getOptionalSupplierRequest().get())
			.map(supplierRequest -> new Resolution(resolution.getPatronRequest(),
				Optional.of(supplierRequest)));
	}

	private Mono<? extends SupplierRequest> saveSupplierRequest(SupplierRequest supplierRequest) {
		log.debug("saveSupplierRequest {}", supplierRequest);

		return Mono.from(supplierRequestRepository.save(supplierRequest));
	}
}
