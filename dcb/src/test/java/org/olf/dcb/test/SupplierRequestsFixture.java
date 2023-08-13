package org.olf.dcb.test;

import io.micronaut.context.annotation.Prototype;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.resolution.SupplierRequestService;
import org.olf.dcb.storage.SupplierRequestRepository;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.UUID;

@Prototype
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
		String localItemId, String localLocationCode, String localItemBarcode, String hostLmsCode) {
		Mono.from(supplierRequestRepository.save(
				SupplierRequest
					.builder()
					.id(supplierRequestId)
					.patronRequest(patronRequest)
					.localItemId(localItemId)
					.localItemLocationCode(localLocationCode)
					.localItemBarcode(localItemBarcode)
					.hostLmsCode(hostLmsCode)
					.build()))
			.block();
	}

	public List<SupplierRequest> findAllFor(PatronRequest patronRequest) {
		return manyValuesFrom(supplierRequestRepository.findAllByPatronRequest(patronRequest));
	}

	public SupplierRequest findFor(PatronRequest patronRequest) {
		return singleValueFrom(supplierRequestService.findSupplierRequestFor(patronRequest));
	}

	public void deleteAll() {
		dataAccess.deleteAll(supplierRequestRepository.findAll(), this::deleteSupplierRequest);
	}

	private Publisher<Void> deleteSupplierRequest(SupplierRequest supplierRequest) {
		return supplierRequestRepository.delete(supplierRequest.getId());
	}
}
