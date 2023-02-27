package org.olf.reshare.dcb.test;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.olf.reshare.dcb.storage.SupplierRequestRepository;
import org.reactivestreams.Publisher;

import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;

@Singleton
public class PatronRequestsDataAccess {
	private final PatronRequestRepository patronRequestRepository;

	private final SupplierRequestRepository supplierRequestRepository;

	public PatronRequestsDataAccess(
		PatronRequestRepository patronRequestRepository,
		SupplierRequestRepository supplierRequestRepository) {

		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestRepository = supplierRequestRepository;
	}

	public List<SupplierRequest> getAllSupplierRequests() {
		return Flux.from(supplierRequestRepository.findAll()).collectList().block();
	}

	public List<PatronRequest> getAllPatronRequests() {
		return Flux.from(patronRequestRepository.findAll()).collectList().block();
	}

	public void deleteAllPatronRequests() {
		deleteAll(this::getAllSupplierRequests, this::deleteSupplierRequest);
		deleteAll(this::getAllPatronRequests, this::deletePatronRequest);
	}

	private Publisher<Void> deletePatronRequest(PatronRequest patronRequest) {
		return patronRequestRepository.delete(patronRequest.getId());
	}

	private Publisher<Void> deleteSupplierRequest(SupplierRequest supplierRequest) {
		return supplierRequestRepository.delete(supplierRequest.getId());
	}

	private <T> void deleteAll(Supplier<List<T>> allRecordsFetcher,
		Function<T, Publisher<Void>> deleteFunction) {

		final var allRecords = allRecordsFetcher.get();

		Flux.fromStream(allRecords.stream())
			.flatMap(deleteFunction)
			.then()
			.block();
	}
}
