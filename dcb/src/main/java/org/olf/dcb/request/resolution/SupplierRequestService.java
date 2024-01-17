package org.olf.dcb.request.resolution;

import jakarta.inject.Singleton;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Singleton
public class SupplierRequestService {
	private static final Logger log = LoggerFactory.getLogger(SupplierRequestService.class);
	private final SupplierRequestRepository supplierRequestRepository;

	public SupplierRequestService(SupplierRequestRepository supplierRequestRepository) {
		this.supplierRequestRepository = supplierRequestRepository;
	}

	public Mono<List<SupplierRequest>> findAllSupplierRequestsFor(PatronRequest patronRequest) {
		return Flux.from(supplierRequestRepository.findAllByPatronRequestAndIsActive(patronRequest, Boolean.TRUE))
		// return Flux.from(supplierRequestRepository.findAllByPatronRequest(patronRequest))
			.collectList();
	}

	// ToDo: This is not safe.. later on we will have multiple supplier requests for a patron request this method
	// is probably looking for the active supplier request
	public Mono<SupplierRequest> findSupplierRequestFor(PatronRequest patronRequest) {
		return findAllSupplierRequestsFor(patronRequest)
			.map(supplierRequests -> supplierRequests.get(0))
			.switchIfEmpty(Mono.error(() -> new RuntimeException("No SupplierRequests found for PatronRequest")));
	}

	public Mono<SupplierRequest> updateSupplierRequest(SupplierRequest supplierRequest) {
		log.debug("updateSupplierRequest: {}", supplierRequest);

		return Mono.from(supplierRequestRepository.update(supplierRequest));
	}
}
