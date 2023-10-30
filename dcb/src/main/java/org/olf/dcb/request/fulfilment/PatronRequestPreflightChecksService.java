package org.olf.dcb.request.fulfilment;

import java.util.List;

import graphql.com.google.common.collect.Streams;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
public class PatronRequestPreflightChecksService {
	private	final PreflightCheck preflightCheck;

	public PatronRequestPreflightChecksService(PreflightCheck preflightCheck) {
		this.preflightCheck = preflightCheck;
	}

	public Mono<PlacePatronRequestCommand> check(PlacePatronRequestCommand command) {
		final var checks = List.of(preflightCheck);

		return Flux.fromIterable(checks)
			.concatMap(check -> check.check(command))
			.reduce(PatronRequestPreflightChecksService::concatenateChecks)
			.flatMap(results -> {
				if (results.stream().allMatch(CheckResult::getPassed)) {
					return Mono.just(command);
				}

				final var failedChecks = results.stream()
					.filter(result -> !result.getPassed())
					.map(result -> FailedPreflightCheck.builder()
						.failureDescription(result.getFailureDescription())
						.build())
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
}
