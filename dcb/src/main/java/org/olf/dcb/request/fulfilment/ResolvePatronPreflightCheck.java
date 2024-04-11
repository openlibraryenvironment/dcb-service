package org.olf.dcb.request.fulfilment;

import static org.olf.dcb.request.fulfilment.CheckResult.failed;
import static org.olf.dcb.request.fulfilment.CheckResult.passed;

import java.util.List;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.UnknownHostLmsException;
import org.olf.dcb.core.interaction.Patron;
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
		final var hostLmsCode = command.getRequestorLocalSystemCode();
		final var localPatronId = command.getRequestorLocalId();

		return hostLmsService.getClientFor(hostLmsCode)
			.flatMap(client -> client.getPatronByLocalId(localPatronId))
			.map(patron -> checkEligibility(localPatronId, patron, hostLmsCode))
			.onErrorResume(PatronNotFoundInHostLmsException.class, this::patronNotFound)
			.onErrorResume(NoPatronTypeMappingFoundException.class, this::noPatronTypeMappingFound)
			.onErrorResume(UnableToConvertLocalPatronTypeException.class, this::nonNumericPatronType)
			.onErrorReturn(UnknownHostLmsException.class, unknownHostLms(hostLmsCode))
			.map(List::of);
	}

	private CheckResult checkEligibility(String localPatronId, Patron patron, String hostLmsCode) {
		// Uses the incoming local patron ID
		// rather than the list of IDs that could be returned from the Host LMS
		// in order to avoid having to choose (and potential data leakage)

		return patron.isEligible()
			? passed()
			: failed("PATRON_INELIGIBLE",
				"Patron \"%s\" from \"%s\" is of type \"%s\" which is \"%s\" for consortial borrowing"
					.formatted(localPatronId, hostLmsCode, patron.getLocalPatronType(), patron.getCanonicalPatronType()));
	}

	private Mono<CheckResult> patronNotFound(PatronNotFoundInHostLmsException error) {
		return Mono.just(failed("PATRON_NOT_FOUND", error.getMessage()));
	}

	private Mono<CheckResult> noPatronTypeMappingFound(NoPatronTypeMappingFoundException error) {
		return Mono.just(failed("PATRON_TYPE_NOT_MAPPED",
			"Local patron type \"%s\" from \"%s\" is not mapped to a DCB canonical patron type"
				.formatted(error.getLocalPatronType(), error.getHostLmsCode())));
	}

	private Mono<CheckResult> nonNumericPatronType(UnableToConvertLocalPatronTypeException error) {
		return Mono.just(failed("LOCAL_PATRON_TYPE_IS_NON_NUMERIC",
			"Local patron \"%s\" from \"%s\" has non-numeric patron type \"%s\""
				.formatted(error.getLocalId(), error.getLocalSystemCode(), error.getLocalPatronTypeCode())
		));
	}

	private static CheckResult unknownHostLms(String localSystemCode) {
		return failed("UNKNOWN_BORROWING_HOST_LMS",
			"\"%s\" is not a recognised Host LMS".formatted(localSystemCode));
	}
}
