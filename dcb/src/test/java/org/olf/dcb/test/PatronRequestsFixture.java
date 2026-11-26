package org.olf.dcb.test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasSize;
import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import org.awaitility.core.ConditionTimeoutException;

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

	public PatronRequest awaitStatus(UUID patronRequestId, PatronRequest.Status status, Duration atMost) {
		return awaitRequest(patronRequestId, "status " + status, request -> status.equals(request.getStatus()), atMost);
	}

	/** On a timeout, the failure says where the request stopped rather than only that it did. */
	public PatronRequest awaitRequest(UUID patronRequestId, String expected, Predicate<PatronRequest> reached,
		Duration atMost) {

		try {
			return await().atMost(atMost).until(() -> findById(patronRequestId), reached);
		}
		catch (ConditionTimeoutException timeout) {
			throw new AssertionError("Request " + patronRequestId + " did not reach " + expected
				+ " within " + atMost + describe(patronRequestId), timeout);
		}
	}

	public String describe(UUID patronRequestId) {
		final var request = findById(patronRequestId);

		if (request == null) {
			return "\n  the request does not exist";
		}

		final var description = new StringBuilder()
			.append("\n  status=").append(request.getStatus())
			.append(" previousStatus=").append(request.getPreviousStatus())
			.append("\n  errorMessage=").append(request.getErrorMessage())
			.append("\n  localRequestStatus=").append(request.getLocalRequestStatus())
			.append(" localItemStatus=").append(request.getLocalItemStatus());

		supplierRequestsFixture.findAllFor(request).forEach(supplierRequest -> description
			.append("\n  supplierRequest hostLms=").append(supplierRequest.getHostLmsCode())
			.append(" active=").append(supplierRequest.getIsActive())
			.append(" localStatus=").append(supplierRequest.getLocalStatus())
			.append(" localItemStatus=").append(supplierRequest.getLocalItemStatus()));

		description.append("\n  audits, oldest first:");

		findAuditEntries(request).stream()
			.sorted(Comparator.comparing(PatronRequestAudit::getAuditDate,
				Comparator.nullsFirst(Comparator.naturalOrder())))
			.forEach(audit -> {
				description.append("\n    ").append(audit.getAuditDate())
					.append(" ").append(audit.getFromStatus())
					.append(" -> ").append(audit.getToStatus())
					.append(" : ").append(audit.getBriefDescription());

				// The audit data of the step that errored holds the host LMS response, which tells a mock that
				// did not match from one that did not answer
				if (audit.getToStatus() == PatronRequest.Status.ERROR && audit.getAuditData() != null) {
					description.append("\n        auditData=").append(audit.getAuditData());
				}
			});

		return description.toString();
	}
}
