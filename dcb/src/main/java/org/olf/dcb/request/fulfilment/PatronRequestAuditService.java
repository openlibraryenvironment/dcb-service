package org.olf.dcb.request.fulfilment;

import static java.util.Optional.empty;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static services.k_int.utils.StringUtils.truncate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.olf.dcb.storage.PatronRequestRepository;

import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
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

		return stateTransitionMetrics(patronRequest, from, to, builder)
			.flatMap(this::buildAndSaveAuditMessage);
	}

	private Mono<PatronRequestAudit.PatronRequestAuditBuilder> stateTransitionMetrics(
		PatronRequest patronRequest, Status from, Status to,
		PatronRequestAudit.PatronRequestAuditBuilder builder) {
		
		return fetchLastStateChangeAudit(patronRequest)
			.flatMap(pr -> {
				
				if (pr.getPatronRequestAudits() == null) {
					log.warn("Unable to add transition metrics because audit were null.");
					return Mono.just(builder);
				}

				final var lastStateChangeAudit = pr.getPatronRequestAudits().get(0);
				final var isStateChange = from.equals(to);

				return Mono
					.just(addCurrentStateTimestamp(builder, lastStateChangeAudit, isStateChange))
					.map(b -> addNextExpectedStatus(to, b, lastStateChangeAudit, isStateChange))
					.map(b -> addOutOfSequenceFlag(to, b, lastStateChangeAudit, isStateChange));
			});
	}

	private static PatronRequestAudit.PatronRequestAuditBuilder addOutOfSequenceFlag(
		Status to, PatronRequestAudit.PatronRequestAuditBuilder builder,
		PatronRequestAudit lastStateChangeAudit, Boolean isStateChange) {

		if (!isStateChange) {
			// use last audit flag if no status change
			builder.outOfSequenceFlag(lastStateChangeAudit.getOutOfSequenceFlag());

		} else if (lastStateChangeAudit.getNextExpectedStatus() != null &&
			!to.equals(lastStateChangeAudit.getNextExpectedStatus()) ) {

			// the to status wasn't what we expected
			builder.outOfSequenceFlag(Boolean.TRUE);

		} else {
			// everything seems to check out
			builder.outOfSequenceFlag(Boolean.FALSE);

		}

		return builder;
	}

	private static PatronRequestAudit.PatronRequestAuditBuilder addNextExpectedStatus(
		Status to, PatronRequestAudit.PatronRequestAuditBuilder builder,
		PatronRequestAudit lastStateChangeAudit, Boolean isStateChange) {

		if (isStateChange) {
			// get next status (happy path)
			builder.nextExpectedStatus(to.getNextExpectedStatus());

		} else {
			// we use the last expected status when it's not a state change
			builder.nextExpectedStatus(lastStateChangeAudit.getNextExpectedStatus());

		}

		return builder;
	}

	private static PatronRequestAudit.PatronRequestAuditBuilder addCurrentStateTimestamp(
		PatronRequestAudit.PatronRequestAuditBuilder builder, PatronRequestAudit lastStateChangeAudit,
		Boolean isStateChange) {

		if (isStateChange) {
			// new to status
			builder.currentStatusStamp(Instant.now());
			builder.elapsedTimeInCurrentStatus(Duration.ZERO.getSeconds());

		} else {
			// get last state change timestamp
			builder.currentStatusStamp(lastStateChangeAudit.getCurrentStatusStamp());

			final var duration = Duration.between(lastStateChangeAudit.getAuditDate(), Instant.now());
			builder.elapsedTimeInCurrentStatus(duration.getSeconds());

		}

		return builder;
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

	private Mono<PatronRequest> fetchLastStateChangeAudit(PatronRequest patronRequest) {
		Status previousStatus = patronRequest.getPreviousStatus();

		if (previousStatus == null) {
			log.debug("previous status was null could not fetch last state change audit.");
			return Mono.just(patronRequest);
		}

		return Flux.from(patronRequestAuditRepository.findAllByPatronRequestAndToStatusEquals(patronRequest, previousStatus))
			.filter(patronRequestAudit -> patronRequestAudit.getFromStatus() != patronRequestAudit.getToStatus())
			.collectSortedList(Comparator.comparing(PatronRequestAudit::getAuditDate).reversed())
			.map(audits -> {
				if (!audits.isEmpty()) {
					patronRequest.setPatronRequestAudits(Collections.singletonList(audits.get(0)));
				}
				return patronRequest;
			})
			// handle cases we didn't fetch the last state change audit
			.onErrorResume(NullPointerException.class, ex -> Mono.just(patronRequest))
			.defaultIfEmpty(patronRequest);
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
