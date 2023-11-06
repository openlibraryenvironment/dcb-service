package org.olf.dcb.request.fulfilment;

import java.util.List;

import org.olf.dcb.core.HostLmsService;

import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
public class ResolvePatronPreflightCheck implements PreflightCheck {
	private final HostLmsService hostLmsService;

	public ResolvePatronPreflightCheck(HostLmsService hostLmsService) {
		this.hostLmsService = hostLmsService;
	}

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		return Mono.just(List.of(CheckResult.passed()));
	}
}
