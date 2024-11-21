package org.olf.dcb.test;

import jakarta.inject.Singleton;
import org.olf.dcb.core.model.InactiveSupplierRequest;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.storage.InactiveSupplierRequestRepository;
import org.reactivestreams.Publisher;

import java.util.List;

import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;

@Singleton
public class InactiveSupplierRequestsFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final InactiveSupplierRequestRepository inactiveSupplierRequestRepository;

	public InactiveSupplierRequestsFixture(InactiveSupplierRequestRepository inactiveSupplierRequestRepository) {
		this.inactiveSupplierRequestRepository = inactiveSupplierRequestRepository;
	}

	public List<InactiveSupplierRequest> findAllFor(PatronRequest patronRequest) {
		return manyValuesFrom(inactiveSupplierRequestRepository.findAllByPatronRequest(patronRequest));
	}

	public void deleteAll() {
		dataAccess.deleteAll(inactiveSupplierRequestRepository.queryAll(), this::deleteInactiveSupplierRequest);
	}

	private Publisher<Void> deleteInactiveSupplierRequest(InactiveSupplierRequest inactiveSupplierRequest) {
		return inactiveSupplierRequestRepository.delete(inactiveSupplierRequest.getId());
	}
}
