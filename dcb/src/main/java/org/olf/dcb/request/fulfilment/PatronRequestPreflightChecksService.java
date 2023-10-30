package org.olf.dcb.request.fulfilment;

import java.util.Collection;
import java.util.List;

import graphql.com.google.common.collect.Streams;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
public class PatronRequestPreflightChecksService {
	private	final Collection<PreflightCheck> checks;

	public PatronRequestPreflightChecksService(Collection<PreflightCheck> checks) {
		this.checks = checks;
	}

	public Mono<PlacePatronRequestCommand> check(PlacePatronRequestCommand command) {
		return Flux.fromIterable(checks)
			.concatMap(check -> check.check(command))
			.reduce(PatronRequestPreflightChecksService::concatenateChecks)
			.flatMap(results -> {
				if (allPassed(results)) {
					return Mono.just(command);
				}

				final var failedChecks = results.stream()
					.filter(CheckResult::getFailed)
					.map(FailedPreflightCheck::fromResult)
					.toList();

				return Mono.error(PreflightCheckFailedException.builder()
					.failedChecks(failedChecks)
					.build());
			});
	}

	private static List<CheckResult> concatenateChecks(List<CheckResult> firstChecks,
		List<CheckResult> secondChecks) {

		return Streams.concat(firstChecks.stream(), secondChecks.stream()).toList();
	}

	private static boolean allPassed(List<CheckResult> results) {
		return results.stream().allMatch(CheckResult::getPassed);
	}
}
