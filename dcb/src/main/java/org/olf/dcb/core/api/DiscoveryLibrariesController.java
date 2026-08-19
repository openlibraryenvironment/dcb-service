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
 * host LMS code, name, lat/long and the library's patron-facing brand.
 *
 * Discovery will use it to resolve "the library nearest to me" into a concrete
 * hostLms code it can scope a search by, to render the institution picker at
 * login, and to render the library's level of the brand lockup.
 *
 * Treat this as what it is: one query, and nothing added to the payload that is
 * not already public information about a library. It must never grow contact
 * details, configuration, or anything a competitor or an attacker could use to
 * map the consortium's internals.
 *
 * <h2>Why the brand belongs here rather than on its own route</h2>
 *
 * Three strings per library, on a payload every consumer already fetches once and
 * caches. The alternative is a per-library brand lookup on every page view, which
 * is a request an anonymous caller controls the rate of — the exact shape of thing
 * this endpoint's history says not to add. A logo URL and its alt text are as
 * public as the library's name, which is already here.
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
		description = "Anonymous directory of libraries (code, host LMS code, name, lat/long, brand) for "
			+ "discovery: the institution picker at login, 'nearest library' resolution and the patron-facing "
			+ "brand lockup. Coordinates may be null; consumers that need them (nearest library) filter "
			+ "accordingly. Brand fields may be null, and a library with a name and no mark is a complete "
			+ "answer: a consumer must render its name rather than substituting the consortium's logo. "
			+ "defaultThemeName is a theme registry name and must be tolerated on read - an unrecognised "
			+ "value falls back to the consumer's default rather than failing. "
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
				entry.name(), entry.latitude(), entry.longitude(),
				entry.brandLogoUrl(), entry.brandLogoAlt(), entry.defaultThemeName()))
			.collectList();
	}

	/**
	 * One library, as the directory publishes it.
	 *
	 * The name is now narrower than the record — it carries brand as well as geography
	 * — and is kept because it is the wire type third-party discovery services already
	 * deserialise by shape. Renaming the Java type would change nothing on the wire and
	 * everything in their diffs.
	 *
	 * Every brand field is nullable and no consumer may treat that as an error: most
	 * libraries in a consortium will never upload a mark.
	 */
	@Serdeable
	public record LibraryGeo(
		String code,
		String hostLmsCode,
		String name,
		Double latitude,
		Double longitude,
		String brandLogoUrl,
		String brandLogoAlt,
		String defaultThemeName) {
	}
}
