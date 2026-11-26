package org.olf.dcb.request.fulfilment;

import java.util.UUID;

import org.olf.dcb.storage.InactiveSupplierRequestRepository;
import org.olf.dcb.storage.PatronIdentityRepository;
import org.olf.dcb.storage.SupplierRequestRepository;

import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

/**
 * DCB's own record of a virtual patron, removed once the library's system no longer has it. Finished
 * supplier requests that name it lose the link, because the identity row cannot go while they hold it.
 */
@Singleton
public class VirtualIdentityRecords {
	private final SupplierRequestRepository supplierRequestRepository;
	private final InactiveSupplierRequestRepository inactiveSupplierRequestRepository;
	private final PatronIdentityRepository patronIdentityRepository;

	public VirtualIdentityRecords(SupplierRequestRepository supplierRequestRepository,
		InactiveSupplierRequestRepository inactiveSupplierRequestRepository,
		PatronIdentityRepository patronIdentityRepository) {

		this.supplierRequestRepository = supplierRequestRepository;
		this.inactiveSupplierRequestRepository = inactiveSupplierRequestRepository;
		this.patronIdentityRepository = patronIdentityRepository;
	}

	@Transactional
	public Mono<Void> forget(UUID virtualIdentityId) {
		return Mono.from(supplierRequestRepository.updateClearVirtualIdentity(virtualIdentityId))
			.then(Mono.from(inactiveSupplierRequestRepository.updateClearVirtualIdentity(virtualIdentityId)))
			.then(Mono.from(patronIdentityRepository.delete(virtualIdentityId)));
	}
}
