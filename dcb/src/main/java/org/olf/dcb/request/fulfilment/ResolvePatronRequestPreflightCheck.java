package org.olf.dcb.request.fulfilment;

import static org.olf.dcb.request.fulfilment.CheckResult.passed;

import java.util.List;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
@Requires(property = "dcb.requests.preflight-checks.resolve-patron-request.enabled", defaultValue = "true", notEquals = "false")
public class ResolvePatronRequestPreflightCheck implements PreflightCheck {
	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		return Mono.just(List.of(passed()));
	}
}
