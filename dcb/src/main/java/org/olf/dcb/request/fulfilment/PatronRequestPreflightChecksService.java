package org.olf.dcb.request.fulfilment;

import static org.olf.dcb.core.model.EventType.FAILED_CHECK;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Event;
import org.olf.dcb.storage.EventLogRepository;

import graphql.com.google.common.collect.Streams;
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
		log.info("Perform preflight checks {}",command);

		return performChecks(command)
			.flatMap(results -> {
				if (allPassed(results)) {
					log.info("request passed preflight {}", command);
					return Mono.just(command);
				}

				// It's worth logging the failures as it might be a sign of some fundamental systems issue
				log.warn("request {} failed preflight {}",command, results);

				return reportFailedChecksInEventLog(results)
					.flatMap(reportedResults ->
						Mono.error(PreflightCheckFailedException.from(reportedResults)));
			});
	}

	private Mono<List<CheckResult>> performChecks(PlacePatronRequestCommand command) {
		return Flux.fromIterable(checks)
			.doOnNext(check -> {
				log.info("Preflight check: {}", check);
			})
			.concatMap(check -> check.check(command))
			.reduce(PatronRequestPreflightChecksService::concatenateChecks);
	}

	private static List<CheckResult> concatenateChecks(List<CheckResult> firstChecks,
		List<CheckResult> secondChecks) {

		return Streams.concat(firstChecks.stream(), secondChecks.stream()).toList();
	}

	private static boolean allPassed(List<CheckResult> results) {
		return results.stream().allMatch(CheckResult::getPassed);
	}

	private Mono<List<CheckResult>> reportFailedChecksInEventLog(List<CheckResult> results) {
		return Flux.fromIterable(results)
			.filter(CheckResult::getFailed)
			.concatMap(result -> eventLogRepository.save(eventFrom(result)))
			.then(Mono.just(results));
	}

	private static Event eventFrom(CheckResult result) {
		return Event.builder()
			.id(UUID.randomUUID())
			.type(FAILED_CHECK)
			.summary(result.getFailureDescription())
			.build();
	}
}
