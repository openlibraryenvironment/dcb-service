package org.olf.dcb.request.fulfilment;

import static java.util.Optional.empty;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static services.k_int.utils.StringUtils.truncate;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.olf.dcb.storage.PatronRequestRepository;

import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class PatronRequestAuditService {
	private final PatronRequestAuditRepository patronRequestAuditRepository;
	private final PatronRequestRepository patronRequestRepository;

	public PatronRequestAuditService(PatronRequestAuditRepository patronRequestAuditRepository,
		PatronRequestRepository patronRequestRepository) {

		this.patronRequestAuditRepository = patronRequestAuditRepository;
		this.patronRequestRepository = patronRequestRepository;
	}

	private void log(PatronRequestAudit auditEntry) {
		log.debug("AUDIT LOG {}: {}",
			auditEntry.getToStatus() == ERROR ? "Unsuccessful transition" : "Successful transition",
			auditEntry);
	}

	public Mono<PatronRequestAudit> addAuditEntry(PatronRequest patronRequest,
		Status from, Status to) {

		return addAuditEntry(patronRequest, from, to, empty(), empty());
	}

	public Mono<PatronRequestAudit> addAuditEntry(PatronRequest patronRequest,
		Status from, Status to, Optional<String> message, Optional<Map<String, Object>> auditData) {

		var builder = PatronRequestAudit.builder()
			.id(UUID.randomUUID())
			.patronRequest(patronRequest)
			.auditDate(Instant.now())
			.fromStatus(from)
			.toStatus(to);

		if (auditData.isPresent()) {
			builder.auditData(auditData.orElse(null));
		}

		message.ifPresent(value -> builder.briefDescription(truncate(value, 254)));

		return buildAndSaveAuditMessage(builder);
	}

	@Transactional
	protected Mono<PatronRequestAudit> buildAndSaveAuditMessage(
		PatronRequestAudit.PatronRequestAuditBuilder builder) {

		final var pra = builder.build();

		return Mono.just(pra)
			.flatMap(auditEntry -> Mono.from(patronRequestAuditRepository.save(auditEntry))
				.cast(PatronRequestAudit.class))
			.doOnSuccess(this::log)
			.doOnError(error -> log.error("Error attempting to write audit for {}", pra, error));
	}

	public Mono<PatronRequestAudit> addAuditEntry(UUID patronRequestId,
		String message, Map<String, Object> auditData) {

		return Mono.from(patronRequestRepository.findById(patronRequestId))
			.flatMap(pr -> addAuditEntry(pr, pr.getStatus(), pr.getStatus(),
				Optional.ofNullable(message), Optional.ofNullable(auditData)));
	}

	public Mono<PatronRequestAudit> addAuditEntry(PatronRequest pr, String message) {
		return this.addAuditEntry(pr, pr.getStatus(), pr.getStatus(),
			Optional.ofNullable(message), empty());
	}

	public Mono<PatronRequestAudit> addErrorAuditEntry(PatronRequest patronRequest,
		String message) {

		return addErrorAuditEntry(patronRequest, patronRequest.getStatus(), message, Map.of());
	}

	public Mono<PatronRequestAudit> addErrorAuditEntry(
		PatronRequest patronRequest, Status fromStatus, String message,
		Map<String, Object> data) {

		return addAuditEntry(patronRequest, fromStatus, ERROR,
			Optional.ofNullable(message), Optional.ofNullable(data));
	}
}
