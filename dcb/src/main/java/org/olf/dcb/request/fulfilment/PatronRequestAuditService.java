package org.olf.dcb.request.fulfilment;

import static java.util.Optional.empty;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static services.k_int.utils.StringUtils.truncate;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.request.workflow.PatronRequestStateTransition;
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

		if (auditData.isPresent()) { // use the existing map to add metrics
			builder.auditData( updateAuditData(auditData.get(), patronRequest) );
		} else { // use a new map to add metrics
			builder.auditData( updateAuditData(new HashMap<>(), patronRequest) );
		}

		message.ifPresent(value -> builder.briefDescription(truncate(value, 254)));

		return buildAndSaveAuditMessage(builder);
	}

	private Map<String, Object> updateAuditData(Map<String, Object> auditData, PatronRequest patronRequest) {
		putIfNotNull(auditData, "previousStatus", patronRequest.getPreviousStatus());
		putIfNotNull(auditData, "autoPollCountForCurrentStatus", patronRequest.getAutoPollCountForCurrentStatus());
		putIfNotNull(auditData, "manualPollCountForCurrentStatus", patronRequest.getManualPollCountForCurrentStatus());
		putIfNotNull(auditData, "currentStatusTimestamp", patronRequest.getCurrentStatusTimestamp());
		putIfNotNull(auditData, "nextExpectedStatus", patronRequest.getNextExpectedStatus());
		putIfNotNull(auditData, "outOfSequenceFlag", patronRequest.getOutOfSequenceFlag());
		putIfNotNull(auditData, "elapsedTimeInCurrentStatus", patronRequest.getElapsedTimeInCurrentStatus());
		return auditData;
	}

	private void putIfNotNull(Map<String, Object> map, String key, Object value) {
		if (value != null) {
			map.put(key, value);
		}
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

	public Mono<? extends PatronRequest> auditActionEmpty(
		PatronRequestStateTransition action, RequestWorkflowContext ctx,
		HashMap<String, Object> auditData) {

		return auditActionFailed(action, ctx, auditData, "EMPTY", "applyTransition caught an unhandled empty.")
			.then(Mono.empty()); // Resume the empty after auditing
	}

	public Mono<? extends PatronRequest> auditActionError(
		PatronRequestStateTransition action, RequestWorkflowContext ctx,
		HashMap<String, Object> auditData, Throwable error) {

		return auditActionFailed(action, ctx, auditData, "ERROR", error.toString())
			.then(Mono.error(error)); // Resume the error after auditing
	}

	public Mono<PatronRequest> auditTrackingError(
		String message, PatronRequest patronRequest, HashMap<String, Object> auditData) {

		return auditEntry(patronRequest, message, auditData)
			.flatMap(pr -> Mono.from(patronRequestRepository.saveOrUpdate(pr)));
	}

	public Mono<PatronRequest> auditActionAttempted(
		PatronRequestStateTransition action, RequestWorkflowContext ctx,
		HashMap<String, Object> auditData) {

		return auditEntry(ctx.getPatronRequest(), "Action attempted : " + action.getName(), auditData);
	}

	public Function<RequestWorkflowContext, Mono<? extends PatronRequest>> auditActionCompleted(
		PatronRequestStateTransition action, HashMap<String, Object> auditData) {

		final var message = "Action completed : " + action.getName();
		log.info("{}", message);

		return chainContext -> addAuditEntry(
				chainContext.getPatronRequest(),
				chainContext.getPatronRequestStateOnEntry(),
				chainContext.getPatronRequest().getStatus(),
				Optional.of(message),
				Optional.of(auditData))
			.flatMap(audit -> Mono.from(patronRequestRepository.saveOrUpdate(audit.getPatronRequest())));
	}

	private Mono<PatronRequest> auditEntry(
		PatronRequest patronRequest, String message, HashMap<String, Object> auditData) {

		log.info("{}", message);

		return addAuditEntry(
			patronRequest,
			patronRequest.getStatus(),
			patronRequest.getStatus(),
			Optional.of(message),
			Optional.of(auditData)
		).flatMap(audit -> Mono.from(patronRequestRepository.saveOrUpdate(audit.getPatronRequest())));
	}

	private Mono<PatronRequest> auditActionFailed(
		PatronRequestStateTransition action, RequestWorkflowContext ctx,
		HashMap<String, Object> auditData, String key, String value) {

		auditData.put(key, value);

		return auditEntry(ctx.getPatronRequest(), "Action failed : " + action.getName(), auditData);
	}
}
