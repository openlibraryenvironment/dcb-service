package org.olf.dcb.request.fulfilment;

import java.util.List;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.HostLmsService.UnknownHostLmsException;
import org.olf.dcb.core.interaction.PatronNotFoundInHostLmsException;

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
			.flatMap(hostLmsService::getClientFor)
			.flatMap(client -> client.getPatronByLocalId(command.getRequestor().getLocalId()))
			.map(localPatron -> CheckResult.passed())
			.onErrorResume(PatronNotFoundInHostLmsException.class,
				error -> Mono.just(CheckResult.failed(error.getMessage())))
			.onErrorReturn(UnknownHostLmsException.class,
				CheckResult.failed("\"" + localSystemCode + "\" is not a recognised host LMS"))
			.map(List::of);
	}
}
