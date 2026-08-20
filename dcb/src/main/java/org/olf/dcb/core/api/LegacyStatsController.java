package org.olf.dcb.core.api;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.api.serde.RequestedTitleStat;
import org.olf.dcb.core.api.serde.TopRequestorStat;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;
import static org.olf.dcb.security.RoleNames.CONSORTIUM_ADMIN;
import static org.olf.dcb.security.RoleNames.LIBRARY_ADMIN;

/**
 * The two Insights paths that were live before the surface moved to {@code /insights}.
 * dcb-admin-for-libraries calls them from its <b>main</b> branch today.
 *
 * <p><b>Delete this class once a dcb-admin-for-libraries release using {@code /insights} is
 * out.</b> The WARN below says whether anything still calls them. Background:
 * {@code docs/insights.md} part 1.
 *
 * <p>It delegates to {@link InsightsController} rather than repeating the query, so the scoping
 * guard cannot be bypassed by coming in through the old door. A copy here would be a second
 * path to the same data that {@code StatsScopeArchitectureTests} would never look at.
 */
@Controller("/patrons/requests/stats")
@Requires(property = "dcb.insights.enabled", notEquals = StringUtils.FALSE)
@Validated
@Secured({CONSORTIUM_ADMIN, ADMINISTRATOR, LIBRARY_ADMIN})
@Tag(name = "Insights API (deprecated paths)")
@Slf4j
public class LegacyStatsController {

	private final InsightsController insights;

	public LegacyStatsController(InsightsController insights) {
		this.insights = insights;
	}

	@Deprecated(forRemoval = true)
	@Operation(deprecated = true, summary = "Moved to /insights/top-requestors")
	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/top-requestors")
	public Mono<Page<TopRequestorStat>> getTopRequestors(
		@Nullable @QueryValue String requestedLibraryCode,
		Pageable pageable,
		Authentication authentication) {

		warn("top-requestors");

		return insights.getTopRequestors(requestedLibraryCode, pageable, authentication);
	}

	@Deprecated(forRemoval = true)
	@Operation(deprecated = true, summary = "Moved to /insights/top-requested-titles")
	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/top-requested-titles")
	public Mono<Page<RequestedTitleStat>> getMostRequestedTitles(
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		@Nullable @QueryValue String requestedLibraryCode,
		Pageable pageable,
		Authentication authentication) {

		warn("top-requested-titles");

		return insights.getMostRequestedTitles(startDate, endDate, requestedLibraryCode,
			pageable, authentication);
	}

	/** WARN, not DEBUG: a silent alias is one nobody ever has the evidence to remove. */
	private void warn(String endpoint) {
		log.warn("Deprecated path /patrons/requests/stats/{} called - use /insights/{}",
			endpoint, endpoint);
	}
}
