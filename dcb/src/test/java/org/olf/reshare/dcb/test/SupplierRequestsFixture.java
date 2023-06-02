package org.olf.reshare.dcb.test;

import io.micronaut.context.annotation.Prototype;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.storage.SupplierRequestRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Prototype
public class SupplierRequestsFixture {

	private final SupplierRequestRepository supplierRequestRepository;

	public SupplierRequestsFixture(
		SupplierRequestRepository supplierRequestRepository) {
		this.supplierRequestRepository = supplierRequestRepository;
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
					.build()
			))
			.block();
	}
}
