package org.olf.reshare.dcb.test;

import static org.olf.reshare.dcb.test.PublisherUtils.manyValuesFrom;
import static org.olf.reshare.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.UUID;

import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.request.resolution.SupplierRequestService;
import org.olf.reshare.dcb.storage.SupplierRequestRepository;
import org.reactivestreams.Publisher;

import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Mono;

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
