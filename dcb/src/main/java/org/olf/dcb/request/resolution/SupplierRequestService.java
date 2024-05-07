package org.olf.dcb.request.resolution;

import java.util.List;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.storage.SupplierRequestRepository;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class SupplierRequestService {
	private final SupplierRequestRepository supplierRequestRepository;

	public SupplierRequestService(SupplierRequestRepository supplierRequestRepository) {
		this.supplierRequestRepository = supplierRequestRepository;
	}

	public Mono<List<SupplierRequest>> findAllSupplierRequestsFor(PatronRequest patronRequest) {
		return Flux.from(supplierRequestRepository.findAllByPatronRequestAndIsActive(patronRequest, true))
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

	public Mono<? extends SupplierRequest> saveSupplierRequest(SupplierRequest supplierRequest) {
		log.debug("saveSupplierRequest({})", supplierRequest);

		return Mono.from(supplierRequestRepository.save(supplierRequest));
	}

	public Mono<SupplierRequest> updateSupplierRequest(SupplierRequest supplierRequest) {
		log.debug("updateSupplierRequest({})", supplierRequest);

		return Mono.from(supplierRequestRepository.update(supplierRequest));
	}
}
