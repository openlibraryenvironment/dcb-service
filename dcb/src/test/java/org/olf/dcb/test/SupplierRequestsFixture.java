package org.olf.dcb.test;

import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.reactivestreams.Publisher;

import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Singleton;

@Singleton
public class SupplierRequestsFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final SupplierRequestRepository supplierRequestRepository;
	private final SupplierRequestService supplierRequestService;

	public SupplierRequestsFixture(SupplierRequestRepository supplierRequestRepository,
		SupplierRequestService supplierRequestService) {

		this.supplierRequestRepository = supplierRequestRepository;
		this.supplierRequestService = supplierRequestService;
	}


	public void saveSupplierRequest(UUID supplierRequestId, PatronRequest patronRequest,
		String localBibId, String localItemId, String localLocationCode, String localItemBarcode,
		String hostLmsCode, String supplyingLocalItemLocation) {

		saveSupplierRequest(supplierRequestId,patronRequest,localBibId,localItemId,localLocationCode,localItemBarcode,hostLmsCode, Boolean.TRUE, supplyingLocalItemLocation);
	}

	public void saveSupplierRequest(UUID supplierRequestId, PatronRequest patronRequest,
		String localBibId, String localItemId, String localLocationCode, String localItemBarcode,
		String hostLmsCode, Boolean isActive, String supplyingLocalItemLocation) {

		saveSupplierRequest(
			SupplierRequest
				.builder()
				.id(supplierRequestId)
				.patronRequest(patronRequest)
				.localItemId(localItemId)
				.localBibId(localBibId)
				.localItemLocationCode(localLocationCode)
				.localItemBarcode(localItemBarcode)
				.hostLmsCode(hostLmsCode)
				.isActive(isActive)
				.localItemLocationCode(supplyingLocalItemLocation)
				.build());
	}

	public SupplierRequest saveSupplierRequest(SupplierRequest supplierRequest) {
		return singleValueFrom(supplierRequestRepository.save(supplierRequest));
	}

	public SupplierRequest updateSupplierRequest(SupplierRequest supplierRequest) {
		return singleValueFrom(supplierRequestRepository.update(supplierRequest));
	}

	public SupplierRequest findById(UUID id) {
		return singleValueFrom(supplierRequestRepository.findById(id));
	}

	public Boolean exists(UUID id) {
		return singleValueFrom(supplierRequestRepository.existsById(id));
	}


	public List<SupplierRequest> findAllFor(PatronRequest patronRequest) {
		return manyValuesFrom(supplierRequestRepository.findAllByPatronRequest(patronRequest));
	}

	public SupplierRequest findFor(PatronRequest patronRequest) {
		return singleValueFrom(supplierRequestService.findSupplierRequestFor(patronRequest));
	}

	public void deleteAll() {
		dataAccess.deleteAll(supplierRequestRepository.queryAll(), this::deleteSupplierRequest);
	}

	private Publisher<Void> deleteSupplierRequest(SupplierRequest supplierRequest) {
		return supplierRequestRepository.delete(supplierRequest.getId());
	}
}
