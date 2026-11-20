package org.olf.dcb.core.api;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static org.olf.dcb.security.RoleNames.LIBRARY_ADMIN;

import java.util.Map;

import org.olf.dcb.core.patroncheck.PatronCheckRateLimiter;
import org.olf.dcb.core.patroncheck.PatronCheckService;
import org.olf.dcb.core.svc.AgencyService;
import org.olf.dcb.security.AgencyClaims;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Library staff asking why one of their own patrons would be refused, without seeing the patron's
 * record. The rules are placement's own ({@link org.olf.dcb.request.fulfilment.PatronEligibilityChecks}),
 * and the answer gives reasons from {@link org.olf.dcb.core.patroncheck.PatronCheckReason}.
 */
@Controller("/patrons/eligibility-checks")
@Secured(LIBRARY_ADMIN)
@Tag(name = "Patron checks")
@Slf4j
@Requires(property = "dcb.patron-checks.enabled", notEquals = StringUtils.FALSE)
public class PatronCheckController {

	private final AgencyService agencyService;
	private final PatronCheckService patronCheckService;
	private final PatronCheckRateLimiter rateLimiter;

	public PatronCheckController(AgencyService agencyService,
		PatronCheckService patronCheckService, PatronCheckRateLimiter rateLimiter) {

		this.agencyService = agencyService;
		this.patronCheckService = patronCheckService;
		this.rateLimiter = rateLimiter;
	}

	/**
	 * @param agencyCode which of the caller's libraries to ask; optional when the token names one
	 * @param identifier what the patron signs in with, usually the card barcode
	 */
	@Serdeable
	public record CheckPatronCommand(@Nullable String agencyCode, @NotBlank String identifier) {
		@Override
		public String toString() {
			return "CheckPatronCommand[agencyCode=%s, identifier=<redacted>]".formatted(agencyCode);
		}
	}

	@Operation(summary = "Why one of the caller's patrons would be refused",
		description = "Looks the identifier up at the caller's own library system and applies the "
			+ "rules placement applies. Answers ELIGIBLE, or REFUSED with coded reasons; never a "
			+ "name, contact or any other field of the patron's record. A patron of another library "
			+ "is NOT_FOUND. The identifier travels in the body so it never reaches a URL or an "
			+ "access log, and is never logged. At most dcb.patron-checks.per-caller-per-minute "
			+ "checks per member of staff and dcb.patron-checks.per-host-lms-per-minute per library "
			+ "system (429 with Retry-After).")
	@Secured(LIBRARY_ADMIN)
	@Post(consumes = APPLICATION_JSON, produces = APPLICATION_JSON)
	public Mono<HttpResponse<?>> check(@Body @Valid CheckPatronCommand command,
		Authentication caller) {

		final var callerAgencies = AgencyClaims.from(caller.getAttributes());

		if (callerAgencies.isEmpty()) {
			return Mono.just(problem(HttpStatus.FORBIDDEN, "NO_AGENCY",
				"This account is not attached to a library."));
		}

		final var agencyCode = command.agencyCode() != null
			? command.agencyCode()
			: callerAgencies.size() == 1 ? callerAgencies.iterator().next() : null;

		if (agencyCode == null) {
			return Mono.just(problem(HttpStatus.BAD_REQUEST, "AGENCY_REQUIRED",
				"This account administers more than one library: say which to ask."));
		}

		if (!callerAgencies.contains(agencyCode)) {
			return Mono.just(problem(HttpStatus.FORBIDDEN, "AGENCY_NOT_PERMITTED",
				"Patrons can only be checked at your own library."));
		}

		if (!rateLimiter.tryAcquireForCaller(caller.getName())) {
			return Mono.just(tooMany("TOO_MANY_CHECKS",
				"You have reached the limit of patron checks for this minute."));
		}

		return agencyService.findByCode(agencyCode)
			.filter(agency -> agency.getHostLms() != null)
			.flatMap(agency -> {
				if (!rateLimiter.tryAcquireForHostLms(agency.getHostLms().getCode())) {
					return Mono.just(tooMany("LIBRARY_SYSTEM_BUSY",
						"Your library system has answered its limit of patron checks for this minute."));
				}

				return patronCheckService.check(agency, callerAgencies, command.identifier())
					.<HttpResponse<?>>map(HttpResponse::ok)
					.onErrorResume(error -> {
						// The class only: an adapter's message may quote the identifier it was given.
						log.warn("Could not check a patron at agency {}: {}", agencyCode,
							error.getClass().getSimpleName());

						return Mono.just(problem(HttpStatus.BAD_GATEWAY, "LIBRARY_UNAVAILABLE",
							"Your library system could not be asked."));
					});
			})
			.defaultIfEmpty(problem(HttpStatus.NOT_FOUND, "LIBRARY_HAS_NO_SYSTEM",
				"Your library has no library system DCB can ask."));
	}

	private static HttpResponse<?> tooMany(String code, String message) {
		return HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
			.header("Retry-After", String.valueOf(PatronCheckRateLimiter.retryAfterSeconds()))
			.body(Map.of("code", code, "message", message));
	}

	private static HttpResponse<?> problem(HttpStatus status, String code, String message) {
		return HttpResponse.status(status).body(Map.of("code", code, "message", message));
	}
}
