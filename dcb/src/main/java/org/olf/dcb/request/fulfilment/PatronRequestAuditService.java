package org.olf.dcb.request.fulfilment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class PatronRequestAuditService {

	private static final Logger log = LoggerFactory.getLogger(PatronRequestAuditService.class);

	private final PatronRequestAuditRepository patronRequestAuditRepository;

	public PatronRequestAuditService(PatronRequestAuditRepository patronRequestAuditRepository) {
		this.patronRequestAuditRepository = patronRequestAuditRepository;
	}

	private void log(PatronRequestAudit auditEntry) {
		log.debug("{}: {}", auditEntry.getToStatus() == Status.ERROR ? "Unsuccessful transition" : "Successful transition",
				auditEntry);
	}

	public Mono<PatronRequestAudit> addAuditEntry(PatronRequest patronRequest, Status from, Status to) {
		return addAuditEntry(patronRequest, from, to, Optional.empty());
	}

	public Mono<PatronRequestAudit> addAuditEntry(PatronRequest patronRequest, Status from, Status to,
			Optional<String> message) {
		var builder = PatronRequestAudit.builder().id(UUID.randomUUID()).patronRequest(patronRequest)
				.auditDate(Instant.now()).fromStatus(from).toStatus(to);

		message.ifPresent(builder::briefDescription);

		return Mono.just(builder.build())
				.flatMap(auditEntry -> Mono.from(patronRequestAuditRepository.save(auditEntry))
						.cast(PatronRequestAudit.class))
				.doOnSuccess(this::log);
	}

	
	public Mono<PatronRequestAudit> addErrorAuditEntry(PatronRequest patronRequest, Status from, Throwable error) {
		return addAuditEntry(patronRequest, from, Status.ERROR, Optional.ofNullable(error.getMessage()));
	}
}
