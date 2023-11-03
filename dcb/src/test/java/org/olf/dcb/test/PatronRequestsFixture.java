package org.olf.dcb.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.olf.dcb.storage.PatronRequestRepository;
import org.reactivestreams.Publisher;

import io.micronaut.context.annotation.Prototype;
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

	public void deleteAll() {
		deleteAllAuditEntries();
		supplierRequestsFixture.deleteAll();
		deleteAllPatronRequests();
	}

	private void deleteAllAuditEntries() {
		dataAccess.deleteAll(patronRequestAuditRepository.queryAll(), this::deleteAuditEntry);
	}

	private Publisher<Void> deleteAuditEntry(PatronRequestAudit auditEntry) {
		return patronRequestAuditRepository.delete(auditEntry.getId());
	}

	private void deleteAllPatronRequests() {
		dataAccess.deleteAll(patronRequestRepository.queryAll(), this::deletePatronRequest);
	}

	private Publisher<Void> deletePatronRequest(PatronRequest patronRequest) {
		return patronRequestRepository.delete(patronRequest.getId());
	}

	public PatronRequest findById(UUID id) {
		return Mono.from(patronRequestRepository.findById(id)).block();
	}

	public void savePatronRequest(PatronRequest patronRequest){
		Mono.from(patronRequestRepository.save(patronRequest)).block();
	}

	public PatronRequestAudit findOnlyAuditEntry(PatronRequest patronRequest) {
		final var entries = findAuditEntries(patronRequest);

		assertThat("Should only have single audit entry", entries, hasSize(1));

		return entries.get(0);
	}

	private List<PatronRequestAudit> findAuditEntries(PatronRequest patronRequest) {
		return manyValuesFrom(patronRequestAuditRepository
			.findByPatronRequest(patronRequest));
	}
}
