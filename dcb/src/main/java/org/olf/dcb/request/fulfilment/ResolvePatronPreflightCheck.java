package org.olf.dcb.request.fulfilment;

import java.util.List;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.HostLmsService.UnknownHostLmsException;

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
		final var localSystemCode = command.getRequestorLocalSystemCode();

		return hostLmsService.findByCode(localSystemCode)
			.map(hostLms -> CheckResult.passed())
			.onErrorReturn(UnknownHostLmsException.class,
				CheckResult.failed("\"" + localSystemCode + "\" is not a recognised host LMS"))
			.map(List::of);
	}
}
