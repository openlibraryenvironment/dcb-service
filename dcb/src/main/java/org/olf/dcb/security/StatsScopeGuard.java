package org.olf.dcb.security;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.olf.dcb.core.svc.AgencyService;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.security.authentication.Authentication;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Decides which library the /insights/** endpoints may report on.
 *
 * Every endpoint takes a libraryCode QUERY PARAMETER, and without this guard a
 * LIBRARY_ADMIN at library A reads library B's figures by editing it. Identity comes
 * from the token's agency claim (see {@link AgencyClaims}); any parameter the caller
 * sent is checked against it, never trusted.
 *
 * Both failure modes REFUSE rather than narrowing - no agency in the token, and an
 * agency that is not the one requested. Why refusal rather than a rollout mode:
 * docs/insights.md part 2.
 */
@Slf4j
@Singleton
public class StatsScopeGuard {

	private final AgencyService agencyService;

	/**
	 * Agency code -> Host LMS code. An Insights page load fans out to ~20 endpoints;
	 * without this, that is 20 extra round trips per view. Keys are agency codes - a
	 * fixed vocabulary, so a caller cannot drive the cardinality.
	 */
	private final Cache<String, String> hostLmsCodeByAgency = Caffeine.newBuilder()
		.maximumSize(2_000)
		.expireAfterWrite(Duration.ofMinutes(10))
		.build();

	public StatsScopeGuard(AgencyService agencyService) {
		this.agencyService = agencyService;
	}

	/**
	 * The library filter to apply, given who is asking and what they asked for.
	 *
	 * Consortium-level callers keep whatever they requested, including nothing.
	 * Library-level callers always get their OWN library, whatever they requested.
	 */
	public Mono<StatsScope> resolve(@Nullable Authentication authentication,
		@Nullable String requestedLibraryCode) {

		final var scope = scopeOf(authentication);

		if (!scope.requiresNarrowing()) {
			return Mono.just(requestedLibraryCode == null
				? StatsScope.unscoped()
				: StatsScope.of(requestedLibraryCode));
		}

		if (scope.isIncoherent()) {
			// Nothing to narrow to. The alternatives would be the caller's own requested
			// code - the hole this class exists to close - or no filter at all.
			return refuse("caller holds a library role but the token carries no "
				+ AgencyClaims.CODE + " claim");
		}

		return ownHostLmsCodes(scope.agencyCodes())
			.flatMap(ownCodes -> {
				if (ownCodes.isEmpty()) {
					return refuse("no agency in " + scope.agencyCodes()
						+ " resolves to a Host LMS");
				}

				final var own = StatsScope.of(ownCodes);

				if (requestedLibraryCode != null && !ownCodes.contains(requestedLibraryCode)) {
					return refuse("caller scoped to " + ownCodes + " asked for "
						+ requestedLibraryCode);
				}

				// Asked for one of their own: honour it, so a multi-library caller can
				// still look at one of their libraries at a time.
				return Mono.just(requestedLibraryCode == null
					? own
					: StatsScope.of(requestedLibraryCode));
			});
	}

	private CallerScope scopeOf(@Nullable Authentication authentication) {
		if (authentication == null) {
			// No authentication on a @Secured route should be impossible, but if the
			// annotation is ever loosened this must not become "unscoped".
			return new CallerScope(false, null, true);
		}
		return CallerScope.from(authentication.getRoles(), authentication.getAttributes());
	}

	/**
	 * The reason is logged, never returned: which library the caller IS associated
	 * with, and what they asked for, are not theirs to have confirmed.
	 */
	private Mono<StatsScope> refuse(String reason) {
		log.warn("Refusing statistics request: {}", reason);

		return Mono.error(new HttpStatusException(HttpStatus.FORBIDDEN,
			"Access denied: your account is not associated with a library."));
	}

	/**
	 * The Host LMS codes behind the caller's agencies, deduplicated - two agencies can
	 * sit on one Host LMS.
	 *
	 * concatMap, not flatMap: each miss is a database read, bounded by the number of
	 * agencies one person administers.
	 */
	private Mono<List<String>> ownHostLmsCodes(Collection<String> agencyCodes) {
		return Flux.fromIterable(agencyCodes)
			.concatMap(this::ownHostLmsCode)
			.distinct()
			.collectList();
	}

	private Mono<String> ownHostLmsCode(String agencyCode) {
		final var cached = hostLmsCodeByAgency.getIfPresent(agencyCode);

		if (cached != null) {
			return Mono.just(cached);
		}

		return agencyService.findByCode(agencyCode)
			.mapNotNull(agency -> {
				final var hostLms = agency.getHostLms();
				return hostLms == null ? null : hostLms.getCode();
			})
			.filter(Objects::nonNull)
			.doOnNext(code -> hostLmsCodeByAgency.put(agencyCode, code));
	}
}
