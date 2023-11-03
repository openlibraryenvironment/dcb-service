package org.olf.dcb.test;

import java.util.UUID;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.olf.dcb.storage.PatronRequestRepository;
import org.reactivestreams.Publisher;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Prototype
public class PatronRequestsFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final PatronRequestRepository patronRequestRepository;
	private final SupplierRequestsFixture supplierRequestsFixture;
	private final PatronRequestAuditRepository patronRequestAuditRepository;

	public PatronRequestsFixture(PatronRequestRepository patronRequestRepository,
		SupplierRequestsFixture supplierRequestsFixture,
		PatronRequestAuditRepository patronRequestAuditRepository) {

		this.patronRequestRepository = patronRequestRepository;
		this.supplierRequestsFixture = supplierRequestsFixture;
		this.patronRequestAuditRepository = patronRequestAuditRepository;
	}

	public PatronRequest findById(UUID id) {
		return Mono.from(patronRequestRepository.findById(id)).block();
	}

	public void savePatronRequest(PatronRequest patronRequest){
		Mono.from(patronRequestRepository.save(patronRequest)).block();
	}

	public void deleteAllPatronRequests() {
		supplierRequestsFixture.deleteAll();
		dataAccess.deleteAll(patronRequestRepository.queryAll(), this::deletePatronRequest);
	}

	private Publisher<Void> deletePatronRequest(PatronRequest patronRequest) {
		return patronRequestRepository.delete(patronRequest.getId());
	}

	public Flux<PatronRequestAudit> findAuditByPatronRequest(PatronRequest patronRequest) {
		return Flux.from(patronRequestAuditRepository.findByPatronRequest(patronRequest));
	}

	@Nullable
	public PatronRequestAudit findOnlyAuditEntry(PatronRequest patronRequest) {
		return findAuditByPatronRequest(patronRequest).blockFirst();
	}

	public void deleteAll() {
		patronRequestAuditRepository.deleteAll();
	}
}
