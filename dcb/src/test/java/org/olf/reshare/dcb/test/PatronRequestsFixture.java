package org.olf.reshare.dcb.test;

import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.storage.PatronRequestRepository;
import org.olf.reshare.dcb.storage.SupplierRequestRepository;
import org.reactivestreams.Publisher;

import io.micronaut.context.annotation.Prototype;

@Prototype
public class PatronRequestsFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final PatronRequestRepository patronRequestRepository;
	private final SupplierRequestRepository supplierRequestRepository;

	public PatronRequestsFixture(
		PatronRequestRepository patronRequestRepository,
		SupplierRequestRepository supplierRequestRepository) {

		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestRepository = supplierRequestRepository;
	}

	public void deleteAllPatronRequests() {
		dataAccess.deleteAll(supplierRequestRepository.findAll(), this::deleteSupplierRequest);
		dataAccess.deleteAll(patronRequestRepository.findAll(), this::deletePatronRequest);
	}

	private Publisher<Void> deletePatronRequest(PatronRequest patronRequest) {
		return patronRequestRepository.delete(patronRequest.getId());
	}

	private Publisher<Void> deleteSupplierRequest(SupplierRequest supplierRequest) {
		return supplierRequestRepository.delete(supplierRequest.getId());
	}
}
