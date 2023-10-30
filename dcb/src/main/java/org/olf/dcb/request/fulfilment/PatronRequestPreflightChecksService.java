package org.olf.dcb.request.fulfilment;

import java.util.List;

import graphql.com.google.common.collect.Streams;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
public class PatronRequestPreflightChecksService {
	private	final PickupLocationPreflightCheck pickupLocationPreflightCheck;

	public PatronRequestPreflightChecksService(PickupLocationPreflightCheck pickupLocationPreflightCheck) {
		this.pickupLocationPreflightCheck = pickupLocationPreflightCheck;
	}

	public Mono<PlacePatronRequestCommand> check(PlacePatronRequestCommand command) {
		return Flux.just(pickupLocationPreflightCheck)
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
