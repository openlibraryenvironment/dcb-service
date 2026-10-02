package org.olf.dcb.request.fulfilment;

import static io.micronaut.core.util.CollectionUtils.isEmpty;
import static java.util.Collections.emptyList;
import static org.olf.dcb.core.model.EventType.FAILED_CHECK;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static services.k_int.utils.StringUtils.truncate;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.UnhandledExceptionProblem;
import org.olf.dcb.core.model.Event;
import org.olf.dcb.storage.EventLogRepository;
import org.olf.dcb.utils.CollectionUtils;
import org.zalando.problem.Problem;

import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class PatronRequestPreflightChecksService {
	private final Collection<PreflightCheck> checks;
	private final EventLogRepository eventLogRepository;

	public PatronRequestPreflightChecksService(Collection<PreflightCheck> checks,
		EventLogRepository eventLogRepository) {

		this.checks = checks;
		this.eventLogRepository = eventLogRepository;
	}

	public Mono<PlacePatronRequestCommand> check(PlacePatronRequestCommand command) {
		log.info("Perform preflight checks {}", command);

		return performChecks(command)
			// Has to go before flatMap that possibly raises error
			.onErrorMap(UnhandledExceptionProblem::new)
			.doOnError(UnhandledExceptionProblem.class, unhandledExceptionProblem ->
				log.error("Unhandled error in preflight checks, message: {}, additional info: {}",
					getValue(unhandledExceptionProblem, Problem::getDetail, "No Message"),
					getValue(unhandledExceptionProblem, Problem::getParameters, Map.of())))
			.flatMap(results -> {
				if (allPassed(results)) {
					log.info("request passed preflight {}", command);
					return Mono.just(command);
				}

				// It's worth logging the failures as it might be a sign of some fundamental systems issue
				log.warn("request {} failed preflight {}", command, results);

				return reportFailedChecksInEventLog(results, command)
					.flatMap(reportedResults -> Mono.error(
						new PreflightCheckFailedException(failedChecksOnly(reportedResults))));
			});
	}

	private Mono<List<CheckResult>> performChecks(PlacePatronRequestCommand command) {
		return Flux.fromIterable(checks)
			.doOnNext(check -> log.info("Preflight check: {}", check))
			.concatMap(check -> check.check(command))
			.reduce(CollectionUtils::concatenate);
	}

	private static boolean allPassed(List<CheckResult> results) {
		if (isEmpty(results)) {
			log.warn("No preflight check results returned");
			return true;
		}

		return results.stream().allMatch(CheckResult::getPassed);
	}

	private static List<FailedPreflightCheck> failedChecksOnly(List<CheckResult> reportedResults) {
		if (isEmpty(reportedResults)) {
			log.warn("No preflight check results returned");
			return emptyList();
		}

		return reportedResults.stream()
			.filter(CheckResult::getFailed)
			.map(FailedPreflightCheck::fromResult)
			.toList();
	}

	private Mono<List<CheckResult>> reportFailedChecksInEventLog(List<CheckResult> results,
		PlacePatronRequestCommand command) {

		return Flux.fromIterable(failedChecksOnly(results))
			.concatMap(result -> eventLogRepository.save(eventFrom(result, command)))
			.then(Mono.just(results));
	}

	/**
	 * A refused request never becomes a patron request, so this row is the only record that it
	 * was attempted, and the description alone said neither which cluster nor which library.
	 * The context goes in additional_data rather than the summary because event_summary is
	 * varchar(128) and these descriptions already reach 104 - anything prefixed truncates away
	 * the reason the request was refused. No patron identifier in either: this is a durable
	 * row, not a support ticket.
	 */
	private static Event eventFrom(FailedPreflightCheck failedCheck, PlacePatronRequestCommand command) {
		final var clusterId = getValueOrNull(command, PlacePatronRequestCommand::getCitation,
			PlacePatronRequestCommand.Citation::getBibClusterId);

		final var agencyCode = getValueOrNull(command, PlacePatronRequestCommand::getRequestor,
			PlacePatronRequestCommand.Requestor::getAgencyCode);

		final var code = getValue(failedCheck, FailedPreflightCheck::getCode, "UNKNOWN_CHECK");
		final var description = getValue(failedCheck, FailedPreflightCheck::getDescription, "");

		final var context = new HashMap<String, Object>();
		context.put("code", code);
		context.put("clusterId", getValue(clusterId, Object::toString, "unknown"));
		context.put("agencyCode", getValue(agencyCode, "unknown"));
		context.put("description", description);

		return Event.builder()
			.id(UUID.randomUUID())
			.type(FAILED_CHECK)
			.summary(truncate(description, 128))
			.additionalData(context)
			.build();
	}
}
