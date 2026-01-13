package org.olf.dcb.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.olf.dcb.storage.PatronRequestRepository;
import org.reactivestreams.Publisher;

import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class PatronRequestsFixture {
	private final DataAccess dataAccess = new DataAccess();

	private final PatronRequestRepository patronRequestRepository;
	private final SupplierRequestsFixture supplierRequestsFixture;
	private final InactiveSupplierRequestsFixture inactiveSupplierRequestsFixture;
	private final PatronRequestAuditRepository patronRequestAuditRepository;

	public void deleteAll() {
		deleteAllAuditEntries();
		supplierRequestsFixture.deleteAll();
		inactiveSupplierRequestsFixture.deleteAll();
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
		return singleValueFrom(patronRequestRepository.findById(id));
	}

	public PatronRequest savePatronRequest(PatronRequest patronRequest){
		return singleValueFrom(patronRequestRepository.save(patronRequest));
	}

	public PatronRequestAudit findOnlyAuditEntry(PatronRequest patronRequest) {
		final var entries = findAuditEntries(patronRequest);

		log.debug("found audit log entries: {}", entries);

		assertThat("Should only have single audit entry", entries, hasSize(1));

		return entries.get(0);
	}

	public List<PatronRequestAudit> findAuditEntries(PatronRequest patronRequest) {
		return manyValuesFrom(patronRequestAuditRepository.findByPatronRequest(patronRequest));
	}
}
