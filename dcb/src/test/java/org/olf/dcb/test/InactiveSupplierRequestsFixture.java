package org.olf.dcb.test;

import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;

import org.olf.dcb.core.model.InactiveSupplierRequest;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.storage.InactiveSupplierRequestRepository;
import org.reactivestreams.Publisher;

import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class InactiveSupplierRequestsFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final InactiveSupplierRequestRepository inactiveSupplierRequestRepository;

	public List<InactiveSupplierRequest> findAllFor(PatronRequest patronRequest) {
		return manyValuesFrom(inactiveSupplierRequestRepository.findAllByPatronRequest(patronRequest));
	}

	public void deleteAll() {
		dataAccess.deleteAll(inactiveSupplierRequestRepository.queryAll(), this::deleteInactiveSupplierRequest);
	}

	private Publisher<Void> deleteInactiveSupplierRequest(InactiveSupplierRequest inactiveSupplierRequest) {
		return inactiveSupplierRequestRepository.delete(inactiveSupplierRequest.getId());
	}

	public void save(InactiveSupplierRequest inactiveSupplierRequest) {
		singleValueFrom(inactiveSupplierRequestRepository.save(inactiveSupplierRequest));
	}
}
