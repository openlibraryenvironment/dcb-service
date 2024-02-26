package org.olf.dcb.request.resolution;

import java.util.List;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
public class SupplierRequestService {
	private static final Logger log = LoggerFactory.getLogger(SupplierRequestService.class);
	private final SupplierRequestRepository supplierRequestRepository;

	public SupplierRequestService(SupplierRequestRepository supplierRequestRepository) {
		this.supplierRequestRepository = supplierRequestRepository;
	}

	public Mono<List<SupplierRequest>> findAllSupplierRequestsFor(PatronRequest patronRequest) {
		return Flux.from(supplierRequestRepository.findAllByPatronRequestAndIsActive(patronRequest, Boolean.TRUE))
			.collectList();
	}

	// ToDo: This is not safe.. later on we will have multiple supplier requests for a patron request this method
	// is probably looking for the active supplier request
	public Mono<SupplierRequest> findSupplierRequestFor(PatronRequest patronRequest) {
		return findAllSupplierRequestsFor(patronRequest)
			.mapNotNull(supplierRequests ->
				supplierRequests.stream()
					.findFirst()
					.orElse(null));

			// There may be no supplier request yet for this patron request
			// .switchIfEmpty(Mono.error(() -> new RuntimeException("No SupplierRequests found for PatronRequest")));
	}

	public Mono<SupplierRequest> updateSupplierRequest(SupplierRequest supplierRequest) {
		log.debug("updateSupplierRequest: {}", supplierRequest);

		return Mono.from(supplierRequestRepository.update(supplierRequest));
	}
}
