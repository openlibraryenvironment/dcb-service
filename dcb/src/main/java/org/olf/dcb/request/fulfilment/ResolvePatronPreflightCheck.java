package org.olf.dcb.request.fulfilment;

import java.util.List;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.HostLmsService.UnknownHostLmsException;
import org.olf.dcb.core.interaction.PatronNotFoundInHostLmsException;
import org.olf.dcb.core.interaction.shared.NoPatronTypeMappingFoundException;
import org.olf.dcb.core.interaction.shared.UnableToConvertLocalPatronTypeException;

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
			.flatMap(client -> client.getPatronByLocalId(command.getRequestorLocalId()))
			.map(localPatron -> CheckResult.passed())
			.onErrorResume(PatronNotFoundInHostLmsException.class,
				error -> Mono.just(CheckResult.failed(error.getMessage())))
			.onErrorResume(NoPatronTypeMappingFoundException.class,
				error -> Mono.just(CheckResult.failed(
					"Local patron type \"" + error.getLocalPatronTypeCode()
						+ "\" from \"" + error.getHostLmsCode() + "\" is not mapped to a DCB canonical patron type")))
			.onErrorResume(UnableToConvertLocalPatronTypeException.class,
				error -> Mono.just(CheckResult.failed(
						"Local patron \"" + error.getLocalId() + "\" from \""
							+ error.getLocalSystemCode() + "\" has non-numeric patron type \"" + error.getLocalPatronTypeCode() + "\""
					)))
			.onErrorReturn(UnknownHostLmsException.class,
				CheckResult.failed("\"" + localSystemCode + "\" is not a recognised host LMS"))
			.map(List::of);
	}
}
