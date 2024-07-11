package org.olf.dcb.request.fulfilment;

import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.request.workflow.PatronRequestStateTransition;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.olf.dcb.storage.PatronRequestRepository;
import org.zalando.problem.Problem;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.micronaut.core.util.StringUtils.isNotEmpty;
import static java.util.Optional.empty;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static services.k_int.utils.StringUtils.truncate;

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
		log.debug("AUDIT LOGGED: {}", auditEntry);
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
		putIfNotNull(auditData, "previousStatus", getValueOrNull(patronRequest, PatronRequest::getPreviousStatus));
		putIfNotNull(auditData, "autoPollCountForCurrentStatus", getValueOrNull(patronRequest, PatronRequest::getAutoPollCountForCurrentStatus));
		putIfNotNull(auditData, "manualPollCountForCurrentStatus", getValueOrNull(patronRequest, PatronRequest::getManualPollCountForCurrentStatus));
		putIfNotNull(auditData, "currentStatusTimestamp", getValueOrNull(patronRequest, PatronRequest::getCurrentStatusTimestamp));
		putIfNotNull(auditData, "nextExpectedStatus", getValueOrNull(patronRequest, PatronRequest::getNextExpectedStatus));
		putIfNotNull(auditData, "outOfSequenceFlag", getValueOrNull(patronRequest, PatronRequest::getOutOfSequenceFlag));
		putIfNotNull(auditData, "elapsedTimeInCurrentStatus", getValueOrNull(patronRequest, PatronRequest::getElapsedTimeInCurrentStatus));
		return auditData;
	}

	private static void putOrNullFallback(Map<String, Object> map, String key, Object value) {
		if (value != null) {
			map.put(key, value);
		} else {
			map.put(key, "Value was null");
		}
	}

	private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
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
			.doOnError(error -> log.error("Error attempting to write audit for {}", pra, error))
			// protect against audit failures
			.onErrorResume(error -> Mono.just(pra));
	}

	public Mono<PatronRequestAudit> addAuditEntry(UUID patronRequestId,
		String message, Map<String, Object> auditData) {

		return Mono.from(patronRequestRepository.findById(patronRequestId))
			.flatMap(pr -> addAuditEntry(pr, pr.getStatus(), pr.getStatus(),
				Optional.ofNullable(message), Optional.ofNullable(auditData)));
	}

	public Mono<PatronRequestAudit> addAuditEntry(PatronRequest patronRequest,
		String message, Map<String, Object> auditData) {

		return addAuditEntry(patronRequest, patronRequest.getStatus(), patronRequest.getStatus(),
				Optional.ofNullable(message), Optional.ofNullable(auditData));
	}

	public Mono<PatronRequestAudit> addAuditEntry(PatronRequest pr, String message) {
		return this.addAuditEntry(pr, pr.getStatus(), pr.getStatus(),
			Optional.ofNullable(message), empty());
	}

	public Mono<PatronRequestAudit> addErrorAuditEntry(PatronRequest patronRequest,
		String message) {

		return addErrorAuditEntry(patronRequest, patronRequest.getStatus(), message, new HashMap<>());
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

		auditData.put("Empty", "applyTransition caught an unhandled empty in return chain");

		return auditActionFailed(action, ctx, auditData)
			.then(Mono.empty()); // Resume the empty after auditing
	}

	public Mono<PatronRequest> auditActionError(PatronRequestStateTransition action, RequestWorkflowContext ctx,
		HashMap<String, Object> auditData, Throwable error) {

		if (error instanceof Problem problem) {
			if (isNotEmpty(problem.getDetail())) {
				auditData.put("detail", problem.getDetail());
			}

			auditData.putAll(problem.getParameters());
		} else {

			auditData.put("errorType", Objects.toString(error.getClass().getSimpleName(), "Error type not available"));
			auditData.put("errorMessage", Objects.toString(error.getMessage(), "No error message available"));
			auditData.put("stackTrace", error.getStackTrace());
		}

		return auditActionFailed(action, ctx, auditData)
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

	private Mono<PatronRequest> auditActionFailed(PatronRequestStateTransition action, RequestWorkflowContext ctx,
		HashMap<String, Object> auditData) {

		return auditEntry(ctx.getPatronRequest(), "Action failed : " + action.getName(), auditData);
	}

	public static Mono<HashMap<String, Object>> auditThrowableMonoWrap(
		HashMap<String, Object> auditData, String key, Throwable error) {

		return Mono.just(auditThrowable(auditData, key, error));
	}

	public static HashMap<String, Object> auditThrowable(HashMap<String, Object> auditData, String key, Throwable error) {
		try {
			// Extract relevant stack trace information
			List<Map<String, Object>> stackTraceList = Arrays.stream(error.getStackTrace())
				.limit(5)  // Limit to the first 5 elements for brevity
				.map(stackTraceElement -> {
					Map<String, Object> stackTraceElementMap = new HashMap<>();
					stackTraceElementMap.put("methodName", stackTraceElement.getMethodName());
					stackTraceElementMap.put("fileName", stackTraceElement.getFileName());
					stackTraceElementMap.put("lineNumber", stackTraceElement.getLineNumber());
					stackTraceElementMap.put("nativeMethod", stackTraceElement.isNativeMethod());
					stackTraceElementMap.put("className", stackTraceElement.getClassName());
					return stackTraceElementMap;
				})
				.collect(Collectors.toList());

			// Create the error map with the condensed stack trace
			Map<String, Object> errorMap = new HashMap<>();
			errorMap.put("message", error.getMessage());
			errorMap.put("localizedMessage", error.getLocalizedMessage());
			errorMap.put("stackTrace", stackTraceList);
			errorMap.put("cause", error.getCause() != null ? error.getCause().toString() : null);

			auditData.put(key, errorMap);
		} catch (Exception e) {
			auditData.put("Failed to convert error to map", e.toString());
			auditData.put(key, error.toString());
		}

		return auditData;
	}

	public static HashMap<String, Object> putAuditData(HashMap<String, Object> auditData, String key, Object value) {

		putOrNullFallback(auditData, key, value);

		return auditData;
	}
}
