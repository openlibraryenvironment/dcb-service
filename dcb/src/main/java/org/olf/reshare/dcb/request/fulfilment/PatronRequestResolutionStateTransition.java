package org.olf.reshare.dcb.request.fulfilment;

import java.util.Objects;

import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.request.resolution.PatronRequestResolutionService;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.olf.reshare.dcb.storage.SupplierRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
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
	public Mono<Void> attempt(PatronRequest patronRequest) {
		log.debug("makeTransition({})", patronRequest);

		return patronRequestResolutionService.resolvePatronRequest(patronRequest)
			.flatMap(this::updatePatronRequest)
			.flatMap(this::saveSupplierRequest)
			.then();
	}

	private Mono<SupplierRequest> updatePatronRequest(SupplierRequest supplierRequest) {
		log.debug("updatePatronRequest {}", supplierRequest.getPatronRequest().getStatusCode());

		return Mono.from(patronRequestRepository.update(supplierRequest.getPatronRequest()))
			.then(Mono.just(supplierRequest));
	}

	private Mono<PatronRequest> saveSupplierRequest(SupplierRequest supplierRequest) {
		log.debug("saveSupplierRequest {}", supplierRequest);

		return Mono.from(supplierRequestRepository.save(supplierRequest))
			.then(Mono.just(Objects.requireNonNull(supplierRequest.getPatronRequest())));
	}
}
