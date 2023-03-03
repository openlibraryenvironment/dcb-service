package org.olf.reshare.dcb.request.resolution;

import java.util.List;

import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.storage.SupplierRequestRepository;

import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
public class SupplierRequestService {
	private final SupplierRequestRepository supplierRequestRepository;

	public SupplierRequestService(SupplierRequestRepository supplierRequestRepository) {
		this.supplierRequestRepository = supplierRequestRepository;
	}

	public Mono<List<SupplierRequest>> findAllSupplierRequestsFor(
		PatronRequest patronRequest) {

		return Flux.from(supplierRequestRepository
			.findAllByPatronRequest(patronRequest))
			.collectList();
	}
}
