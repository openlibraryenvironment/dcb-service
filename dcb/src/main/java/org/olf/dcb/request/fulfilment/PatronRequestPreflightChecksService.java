package org.olf.dcb.request.fulfilment;

import java.util.Collection;
import java.util.List;

import org.olf.dcb.storage.EventLogRepository;

import graphql.com.google.common.collect.Streams;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
public class PatronRequestPreflightChecksService {
	private	final Collection<PreflightCheck> checks;

	private final EventLogRepository eventLogRepository;

	public PatronRequestPreflightChecksService(Collection<PreflightCheck> checks,
		EventLogRepository eventLogRepository) {

		this.checks = checks;
		this.eventLogRepository = eventLogRepository;
	}

	public Mono<PlacePatronRequestCommand> check(PlacePatronRequestCommand command) {
		return performChecks(command)
			.flatMap(results -> {
				if (allPassed(results)) {
					return Mono.just(command);
				}

				return Mono.error(PreflightCheckFailedException.from(results));
			});
	}

	private Mono<List<CheckResult>> performChecks(PlacePatronRequestCommand command) {
		return Flux.fromIterable(checks)
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
}
