package org.olf.dcb.request.fulfilment;

import static io.micronaut.core.util.CollectionUtils.isEmpty;
import static java.util.Collections.emptyList;
import static org.olf.dcb.core.model.EventType.FAILED_CHECK;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static services.k_int.utils.StringUtils.truncate;

import java.util.Collection;
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
	 * was attempted. The description alone did not say which cluster or which library, and there
	 * is nowhere else to look; the code and those two are prefixed so the row can be found and
	 * the attempt reproduced. No patron identifier: this is a durable row, not a support ticket.
	 */
	private static Event eventFrom(FailedPreflightCheck failedCheck, PlacePatronRequestCommand command) {
		final var clusterId = getValueOrNull(command, PlacePatronRequestCommand::getCitation,
			PlacePatronRequestCommand.Citation::getBibClusterId);

		final var agencyCode = getValueOrNull(command, PlacePatronRequestCommand::getRequestor,
			PlacePatronRequestCommand.Requestor::getAgencyCode);

		final var summary = "%s cluster=%s agency=%s : %s".formatted(
			getValue(failedCheck, FailedPreflightCheck::getCode, "UNKNOWN_CHECK"),
			getValue(clusterId, Object::toString, "unknown"),
			getValue(agencyCode, "unknown"),
			getValue(failedCheck, FailedPreflightCheck::getDescription, ""));

		return Event.builder()
			.id(UUID.randomUUID())
			.type(FAILED_CHECK)
			.summary(truncate(summary, 128))
			.build();
	}
}
