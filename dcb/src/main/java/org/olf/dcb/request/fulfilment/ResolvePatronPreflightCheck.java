package org.olf.dcb.request.fulfilment;

import static org.olf.dcb.request.fulfilment.CheckResult.failed;
import static org.olf.dcb.request.fulfilment.CheckResult.passed;

import java.util.List;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.UnknownHostLmsException;
import org.olf.dcb.core.interaction.PatronNotFoundInHostLmsException;
import org.olf.dcb.core.interaction.shared.NoPatronTypeMappingFoundException;
import org.olf.dcb.core.interaction.shared.UnableToConvertLocalPatronTypeException;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@Singleton
@Requires(property = "dcb.requests.preflight-checks.resolve-patron.enabled", defaultValue = "true", notEquals = "false")
public class ResolvePatronPreflightCheck implements PreflightCheck {
	private final HostLmsService hostLmsService;

	public ResolvePatronPreflightCheck(HostLmsService hostLmsService) {
		this.hostLmsService = hostLmsService;
	}

	@Override
	public Mono<List<CheckResult>> check(PlacePatronRequestCommand command) {
		final var localSystemCode = command.getRequestorLocalSystemCode();

		return hostLmsService.getClientFor(localSystemCode)
			.flatMap(client -> client.getPatronByLocalId(command.getRequestorLocalId()))
			.map(localPatron -> passed())
			.onErrorResume(PatronNotFoundInHostLmsException.class, this::patronNotFound)
			.onErrorResume(NoPatronTypeMappingFoundException.class, this::noPatronTypeMappingFound)
			.onErrorResume(UnableToConvertLocalPatronTypeException.class, this::nonNumericPatronType)
			.onErrorReturn(UnknownHostLmsException.class, unknownHostLms(localSystemCode))
			.map(List::of);
	}

	private Mono<CheckResult> patronNotFound(PatronNotFoundInHostLmsException error) {
		return Mono.just(failed(error.getMessage()));
	}

	private Mono<CheckResult> noPatronTypeMappingFound(NoPatronTypeMappingFoundException error) {
		return Mono.just(failed(
			"Local patron type \"%s\" from \"%s\" is not mapped to a DCB canonical patron type"
				.formatted(error.getLocalPatronType(), error.getHostLmsCode())));
	}

	private Mono<CheckResult> nonNumericPatronType(UnableToConvertLocalPatronTypeException error) {
		return Mono.just(failed("Local patron \"%s\" from \"%s\" has non-numeric patron type \"%s\""
			.formatted(error.getLocalId(), error.getLocalSystemCode(), error.getLocalPatronTypeCode())));
	}

	private static CheckResult unknownHostLms(String localSystemCode) {
		return failed("\"%s\" is not a recognised Host LMS".formatted(localSystemCode));
	}
}
