package org.olf.dcb.core.api.discovery;

import static io.micronaut.http.MediaType.APPLICATION_JSON;
import static org.olf.dcb.security.RoleNames.DISCOVERY_SERVICE;

import java.util.Map;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.PatronNotFoundInHostLmsException;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.svc.AgencyService;
import org.olf.dcb.security.discovery.DiscoveryServiceProperties;
import org.olf.dcb.security.discovery.DiscoveryTrustedServiceStore;
import org.olf.dcb.storage.PatronIdentityRepository;

import io.micronaut.core.annotation.Nullable;
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
 * The borrower an institution sign-in names, looked up at that institution's library — see
 * docs/discovery-service-approach.md section 6.4.
 */
@Controller("/discovery/patrons")
@Secured(DISCOVERY_SERVICE)
@Tag(name = "Discovery API")
@Slf4j
public class DiscoveryPatronResolveController {

	private final AgencyService agencyService;
	private final HostLmsService hostLmsService;
	private final DiscoveryServiceProperties properties;
	private final PatronIdentityRepository patronIdentityRepository;
	private final DiscoveryTrustedServiceStore trustedServiceStore;
	private final PatronResolveRateLimiter rateLimiter;

	public DiscoveryPatronResolveController(AgencyService agencyService,
		HostLmsService hostLmsService, DiscoveryServiceProperties properties,
		PatronIdentityRepository patronIdentityRepository,
		DiscoveryTrustedServiceStore trustedServiceStore, PatronResolveRateLimiter rateLimiter) {

		this.agencyService = agencyService;
		this.hostLmsService = hostLmsService;
		this.properties = properties;
		this.patronIdentityRepository = patronIdentityRepository;
		this.trustedServiceStore = trustedServiceStore;
		this.rateLimiter = rateLimiter;
	}

	@Serdeable
	public record ResolvePatronCommand(@NotBlank String agencyCode, @NotBlank String principal) {
	}

	@Serdeable
	public record ResolvedPatron(String systemCode, String patronId, String agencyCode,
		@Nullable String homeLocationCode) {
	}

	@Operation(summary = "The borrower an institution sign-in names",
		description = "Looks the principal up at the agency's library system, as that system "
			+ "matches a sign-in name, and answers with the patron's identity there: no name "
			+ "and no barcode. 404 when the library has no such patron. One live read against "
			+ "that library per call, time-bounded, and at most "
			+ "dcb.discovery.patron-resolves-per-minute calls per trusted service (429 after). "
			+ "The principal travels in the body so it never reaches a URL or an access log.")
	@Post(uri = "/resolve", consumes = APPLICATION_JSON, produces = APPLICATION_JSON)
	public Mono<HttpResponse<?>> resolve(@Body @Valid ResolvePatronCommand command,
		Authentication caller) {

		// Assertions are refused while discovery is off, so the step that produces one is too.
		if (!properties.isEnabled()) {
			return Mono.just(forbidden("DISCOVERY_DISABLED", "This DCB accepts no discovery patrons."));
		}

		final var service = trustedServiceStore.findByCaller(caller);

		if (service.isEmpty()) {
			return Mono.just(forbidden("NOT_A_TRUSTED_SERVICE",
				"This client is not a trusted discovery service."));
		}

		if (!rateLimiter.tryAcquire(service.get().getServiceId())) {
			return Mono.just(HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
				.header("Retry-After", String.valueOf(PatronResolveRateLimiter.WINDOW.toSeconds()))
				.body(Map.of("code", "TOO_MANY_RESOLVES", "message",
					"This service has resolved its limit of sign-ins for this minute.")));
		}

		return agencyService.findByCode(command.agencyCode())
			.filter(agency -> agency.getHostLms() != null)
			.flatMap(agency -> lookUp(agency, command.principal()))
			.<HttpResponse<?>>map(HttpResponse::ok)
			.defaultIfEmpty(HttpResponse.notFound())
			.onErrorResume(error -> {
				// The class only: an adapter's message may quote the principal it was given.
				log.warn("Could not resolve a patron at agency {}: {}", command.agencyCode(),
					error.getClass().getSimpleName());

				return Mono.just(HttpResponse.status(HttpStatus.BAD_GATEWAY)
					.body(Map.of("code", "LIBRARY_UNAVAILABLE",
						"message", "The patron's library could not be asked.")));
			});
	}

	private static HttpResponse<?> forbidden(String code, String message) {
		return HttpResponse.status(HttpStatus.FORBIDDEN).body(Map.of("code", code, "message", message));
	}

	private Mono<ResolvedPatron> lookUp(DataAgency agency, String principal) {
		final var systemCode = agency.getHostLms().getCode();

		return hostLmsService.getClientFor(agency.getHostLms())
			.flatMap(client -> client.getPatronByUsername(principal))
			.timeout(properties.getPatronResolveTimeout())
			.onErrorResume(PatronNotFoundInHostLmsException.class, notFound -> Mono.empty())
			.filter(DiscoveryPatronResolveController::isBorrower)
			// A virtual patron DCB created at this library can match a sign-in name too; resolving
			// to it would let a sign-in act as some other library's borrower
			.filterWhen(patron -> isNotVirtualPatron(patron.getLocalId().getFirst(), agency.getHostLms()))
			.map(patron -> new ResolvedPatron(systemCode, patron.getLocalId().getFirst(),
				agency.getCode(), patron.getLocalHomeLibraryCode()));
	}

	private Mono<Boolean> isNotVirtualPatron(String localId, DataHostLms hostLms) {
		return Mono.from(patronIdentityRepository
				.existsByLocalIdAndHostLmsAndHomeIdentity(localId, hostLms, false))
			.map(isVirtual -> !isVirtual);
	}

	private static boolean isBorrower(Patron patron) {
		return !Boolean.TRUE.equals(patron.getIsDeleted())
			&& patron.getLocalId() != null
			&& !patron.getLocalId().isEmpty();
	}
}
