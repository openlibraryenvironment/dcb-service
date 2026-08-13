package org.olf.dcb.core.api;

import static io.micronaut.security.rules.SecurityRule.IS_ANONYMOUS;

import java.util.List;

import org.olf.dcb.storage.AgencyRepository;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.serde.annotation.Serdeable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Anonymous, non-sensitive library directory for discovery services: code,
 * host LMS code, name and lat/long only.
 *
 * Discovery will use it to resolve "the library nearest to me" into a concrete
 * hostLms code it can scope a search by, and to render the institution picker at
 * login.
 *
 * This is the ONLY anonymous endpoint in the discovery surface, so treat it as
 * such: one query, cached, and nothing added to the payload that is not already
 * public information about a library. It must never grow contact details,
 * configuration, or anything a competitor or an attacker could use to map the
 * consortium's internals.
 */
@Controller("/discovery/libraries")
@Secured(IS_ANONYMOUS)
@Tag(name = "Discovery API")
@Slf4j
public class DiscoveryLibrariesController {

	private final AgencyRepository agencyRepository;

	public DiscoveryLibrariesController(AgencyRepository agencyRepository) {
		this.agencyRepository = agencyRepository;
	}

	@Operation(summary = "List libraries",
		description = "Anonymous directory of libraries (code, host LMS code, name, lat/long) for discovery: "
			+ "the institution picker at login and 'nearest library' resolution. Coordinates may be null; "
			+ "consumers that need them (nearest library) filter accordingly. "
			+ "By default only libraries enabled for borrowing are returned, because a patron from a "
			+ "non-borrowing agency cannot place a request. Pass includeAll=true for the whole directory.")
	// ONE indexed query per call. Not cached: @Cacheable is used nowhere else in this
	// codebase and its interaction with a reactive return type is unproven here, which
	// is not a thing to introduce on a security branch. The pool exhaustion came from
	// 2N+1 queries, not from one — if this ever needs a cache, follow the hand-rolled
	// bounded-Caffeine idiom in LiveAvailabilityService, and rate-limit /discovery/**
	// at the ingress regardless.
	@Get
	public Mono<List<LibraryGeo>> list(
		@QueryValue(defaultValue = "false") boolean includeAll) {

		return Flux.from(agencyRepository.findLibraryDirectory(includeAll))
			.map(entry -> new LibraryGeo(entry.code(), entry.hostLmsCode(),
				entry.name(), entry.latitude(), entry.longitude()))
			.collectList();
	}

	@Serdeable
	public record LibraryGeo(
		String code,
		String hostLmsCode,
		String name,
		Double latitude,
		Double longitude) {
	}
}
