package org.olf.dcb.core.api;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.api.serde.*;
import org.olf.dcb.core.svc.CollectionAnalysisService;
import org.olf.dcb.core.svc.TimeBucket;
import org.olf.dcb.security.StatsScopeGuard;
import org.olf.dcb.storage.ClusterRecordRepository;
import org.olf.dcb.storage.PatronRequestRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;
import static org.olf.dcb.security.RoleNames.CONSORTIUM_ADMIN;
import static org.olf.dcb.security.RoleNames.LIBRARY_ADMIN;

/**
 * Insights - reporting and analytics over patron requests and the catalogued collection.
 * Read only. Full documentation: {@code docs/insights.md}.
 *
 * <p><b>The class boundary is the security surface.</b> Every {@code @Get} in this file must
 * route its library filter through {@link StatsScopeGuard}; scoping is a property of the whole
 * surface, because one endpoint added without it reopens the cross-tenant read for all of them.
 * {@code StatsScopeArchitectureTests} reads this file and enforces exactly that.
 *
 * <p>{@code dcb.insights.enabled=false} removes the surface entirely - the bean is not created,
 * so the routes 404. Two older paths are still served by {@link LegacyStatsController}.
 */
@Controller("/insights")
@Requires(property = "dcb.insights.enabled", notEquals = StringUtils.FALSE)
@Validated
@Secured({CONSORTIUM_ADMIN, ADMINISTRATOR, LIBRARY_ADMIN})
@Tag(name = "Insights API")
@Slf4j
public class InsightsController {

	/** Shared with the Audit Explorer's incidence chart, so the two cannot disagree. */
	private static final Duration DEFAULT_STATS_WINDOW = Duration.ofDays(90);

	/** What "top partners" means when the caller names no sort. */
	private static final Sort TOTAL_DESCENDING = Sort.of(Sort.Order.desc("total_count"));

	private static final int DEFAULT_PARTNER_PAGE_SIZE = 10;

	private final PatronRequestRepository patronRequestRepository;
	private final ClusterRecordRepository clusterRecordRepository;

	/** A library-scoped caller must not be able to choose the library it reports on. */
	private final StatsScopeGuard statsScopeGuard;

	/** Owns the concurrency limit and cache for the catalogue-wide queries. */
	private final CollectionAnalysisService collectionAnalysisService;

	public InsightsController(PatronRequestRepository patronRequestRepository,
			ClusterRecordRepository clusterRecordRepository,
			StatsScopeGuard statsScopeGuard,
			CollectionAnalysisService collectionAnalysisService) {

		this.patronRequestRepository = patronRequestRepository;
		this.clusterRecordRepository = clusterRecordRepository;
		this.statsScopeGuard = statsScopeGuard;
		this.collectionAnalysisService = collectionAnalysisService;
	}

	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/top-requestors")
	public Mono<Page<TopRequestorStat>> getTopRequestors(
		@Nullable @QueryValue String requestedLibraryCode,
		Pageable pageable,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					return Mono.from(patronRequestRepository.findTopRequestors(libraryCode, pageable));
			});
	}

	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/top-requested-titles")
	public Mono<Page<RequestedTitleStat>> getMostRequestedTitles(
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		@Nullable @QueryValue String requestedLibraryCode,
		Pageable pageable,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					return Mono.from(patronRequestRepository.findMostRequestedTitles(startDate, endDate, libraryCode, pageable));
			});
	}

	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/dashboard-metrics")
	public Mono<DashboardMetrics> getDashboardMetrics(
		@Nullable @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					// Percentile turnaround (p50/p95); fall back to zeroed stat if the window is empty.
					Mono<TurnaroundStat> turnaroundToLoaned = Mono.from(patronRequestRepository
							.findTurnaroundToStatus(libraryCode, "LOANED", startDate, endDate))
						.defaultIfEmpty(new TurnaroundStat(0.0, 0.0));

					Mono<TurnaroundStat> turnaroundToFinalised = Mono.from(patronRequestRepository
							.findTurnaroundToStatus(libraryCode, "FINALISED", startDate, endDate))
						.defaultIfEmpty(new TurnaroundStat(0.0, 0.0));

					Mono<List<PartnerStat>> topSuppliers = patronRequestRepository
						.findTopSuppliersForLibrary(libraryCode, startDate, endDate).collectList();

					Mono<List<PartnerStat>> topBorrowers = patronRequestRepository
						.findTopBorrowersFromLibrary(libraryCode, startDate, endDate).collectList();

					// Combine all queries asynchronously and map them to the return record
					return Mono.zip(turnaroundToLoaned, turnaroundToFinalised, topSuppliers, topBorrowers)
						.map(tuple -> new DashboardMetrics(
							tuple.getT1(),
							tuple.getT2(),
							tuple.getT3(),
							tuple.getT4()
						));
			});
	}

	/**
	 * Flow time-series: how many requests transitioned into each status per bucket, from the
	 * audit trail. Powers the trend charts and the interactive plot-builder. This is a flow
	 * (events-per-period) metric; true concurrent "stock" counts are a separate, later concern.
	 */
	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/timeseries")
	public Mono<List<TimeSeriesPoint>> getStatusFlowTimeSeries(
		@Nullable @QueryValue String interval,
		@Nullable @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					// TimeBucket owns the unit and the window contract. An unknown name is
					// REJECTED, never collapsed to 'day'.
					final var bucket = TimeBucket.fromName(interval);

					// Gap filling needs bounds to fill between, so the window is not optional here.
					final var end = endDate == null ? Instant.now() : endDate;
					final var start = startDate == null ? end.minus(DEFAULT_STATS_WINDOW) : startDate;

					return Flux.from(patronRequestRepository.findStatusFlowTimeSeries(
							bucket.getDateTruncUnit(), libraryCode,
							utc(start), utc(end), TimeBucket.MAX_BUCKETS + 1))
						.collectList()
						.flatMap(points -> distinctBuckets(points) > TimeBucket.MAX_BUCKETS
							? Mono.error(bucket.tooManyBuckets())
							: Mono.just(points));
			});
	}

	/**
	 * The generated series is capped in SQL at MAX_BUCKETS + 1, so seeing more than MAX_BUCKETS
	 * distinct buckets means the window was too long for the chosen width. Counting distinct
	 * buckets rather than rows because this series has a second dimension - one row per bucket
	 * per status - so a row count would reject far too early.
	 */
	private static long distinctBuckets(List<TimeSeriesPoint> points) {
		return points.stream().map(TimeSeriesPoint::bucket).distinct().count();
	}

	/** audit_date is timestamp WITHOUT time zone holding UTC, so bind UTC local date times. */
	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/failure-taxonomy")
	public Mono<List<FailureReasonStat>> getFailureTaxonomy(
		@Nullable @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					return Flux.from(patronRequestRepository.findFailureTaxonomy(libraryCode, startDate, endDate))
						.collectList();
			});
	}

	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/supplier-reliability")
	public Mono<List<SupplierReliabilityStat>> getSupplierReliability(
		@Nullable @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					return Flux.from(patronRequestRepository.findSupplierReliability(libraryCode, startDate, endDate))
						.collectList();
			});
	}

	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/net-flow")
	public Mono<List<NetFlowStat>> getNetFlow(
		@Nullable @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					return Flux.from(patronRequestRepository.findNetFlow(libraryCode, startDate, endDate))
						.collectList();
			});
	}

	// Time-in-status funnel: median dwell per status, biggest bottleneck first.
	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/time-in-status")
	public Mono<List<StatusDwellStat>> getTimeInStatus(
		@Nullable @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					return Flux.from(patronRequestRepository.findTimeInStatus(libraryCode, startDate, endDate))
						.collectList();
			});
	}

	// Supplier-response SLA: median time from request-placed to confirmed, per supplier.
	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/supplier-response-sla")
	public Mono<List<SupplierResponseStat>> getSupplierResponseSla(
		@Nullable @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					return Flux.from(patronRequestRepository.findSupplierResponseSla(libraryCode, startDate, endDate))
						.collectList();
			});
	}

	// Demand heatmap: request volume by day-of-week x hour-of-day.
	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/demand-heatmap")
	public Mono<List<DemandHeatCell>> getDemandHeatmap(
		@Nullable @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					return Flux.from(patronRequestRepository.findDemandHeatmap(libraryCode, startDate, endDate))
						.collectList();
			});
	}

	// Checkout rate: how many placed requests actually reached the shelf (LOANED).
	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/checkout-rate")
	public Mono<CheckoutRateStat> getCheckoutRate(
		@Nullable @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					return Mono.from(patronRequestRepository.findCheckoutRate(libraryCode, startDate, endDate))
						.defaultIfEmpty(new CheckoutRateStat(0L, 0L));
			});
	}

	// Consortial collection analysis: demand by canonical format.
	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/demand-by-format")
	public Mono<List<FormatDemandStat>> getDemandByFormat(
		@Nullable @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					return Flux.from(patronRequestRepository.findDemandByFormat(libraryCode, startDate, endDate))
						.collectList();
			});
	}

	// Collection summary: distinct titles requested vs total volume.
	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/collection-summary")
	public Mono<CollectionSummaryStat> getCollectionSummary(
		@Nullable @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					return Mono.from(patronRequestRepository.findCollectionSummary(libraryCode, startDate, endDate))
						.defaultIfEmpty(new CollectionSummaryStat(0L, 0L));
			});
	}

	/**
	 * Combined KPI header for the insights dashboard: every above-the-fold figure in ONE
	 * round-trip (fanned out in parallel), instead of ~7 separate requests per load. The
	 * heavier below-the-fold panels stay on their own endpoints and are lazy-loaded.
	 */
	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/dashboard")
	public Mono<DashboardSummary> getDashboard(
		@Nullable @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					// Prior window = the equal-length window immediately before this one (for deltas).
					final Mono<FulfillmentStat> priorMono;
					if (startDate != null && endDate != null) {
						final long windowMs = endDate.toEpochMilli() - startDate.toEpochMilli();
						final Instant priorStart = startDate.minusMillis(windowMs);
						priorMono = Mono.from(patronRequestRepository.getBorrowerFulfillmentStats(libraryCode, priorStart, startDate))
							.defaultIfEmpty(new FulfillmentStat(0L, 0L));
					} else {
						priorMono = Mono.just(new FulfillmentStat(0L, 0L));
					}

					final Mono<FulfillmentStat> current = Mono.from(patronRequestRepository.getBorrowerFulfillmentStats(libraryCode, startDate, endDate))
						.defaultIfEmpty(new FulfillmentStat(0L, 0L));
					final Mono<TurnaroundStat> turnaround = Mono.from(patronRequestRepository.findTurnaroundToStatus(libraryCode, "LOANED", startDate, endDate))
						.defaultIfEmpty(new TurnaroundStat(0.0, 0.0));
					final Mono<CheckoutRateStat> checkout = Mono.from(patronRequestRepository.findCheckoutRate(libraryCode, startDate, endDate))
						.defaultIfEmpty(new CheckoutRateStat(0L, 0L));
					final Mono<CollectionBalanceStat> totals = Mono.from(patronRequestRepository.findLendBorrowTotals(libraryCode, startDate, endDate))
						.defaultIfEmpty(new CollectionBalanceStat(0L, 0L));
					final Mono<Long> saved = Mono.from(patronRequestRepository.countSavedByReresolution(libraryCode, startDate, endDate))
						.defaultIfEmpty(0L);
					final Mono<CollectionSummaryStat> summary = Mono.from(patronRequestRepository.findCollectionSummary(libraryCode, startDate, endDate))
						.defaultIfEmpty(new CollectionSummaryStat(0L, 0L));

					return Mono.zip(current, priorMono, turnaround, checkout, totals, saved, summary)
						.map(t -> new DashboardSummary(
							t.getT1(), t.getT2(), t.getT3(), t.getT4(), t.getT5(), t.getT6(), t.getT7()));
			});
	}

	/**
	 * Median (p50) and p95 turnaround to a target status for ONE library, the whole
	 * CONSORTIUM, or an arbitrary COMBINATION of libraries. Pass libraryCodes as a
	 * comma-separated list of Host LMS codes, or omit it for the whole consortium.
	 */
	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/turnaround")
	public Mono<TurnaroundStat> getTurnaround(
		@Nullable @QueryValue String libraryCodes,
		@QueryValue(defaultValue = "LOANED") String targetStatus,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		// The only endpoint taking a COMBINATION of libraries. A library-scoped caller is
		// collapsed to their own code however many they listed - passing the requested list
		// through would make this the one endpoint that still answers for anyone.
		return statsScopeGuard.resolve(authentication, null)
			.flatMap(scope -> {
				final var codes = scope.isUnscoped() ? libraryCodes : scope.libraryCode();

				return Mono.from(patronRequestRepository
						.findTurnaroundToStatus(codes, targetStatus, startDate, endDate))
					.defaultIfEmpty(new TurnaroundStat(0.0, 0.0));
			});
	}

	/**
	 * Peer benchmarking. The one endpoint that is cross-tenant BY DESIGN - ranking a library
	 * against the network cannot be done from its own row - so peers are NAMED, for every
	 * caller. Why naming beat pseudonymising: docs/insights.md part 3.
	 *
	 * If a consortium ever wants peer identity withheld, hide the panel. Do not publish a
	 * pseudonym derived from the Host LMS code; codes are short, few and known to every member.
	 */
	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/peer-benchmarks")
	public Mono<List<PeerBenchmarkStat>> getPeerBenchmarks(
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		// Resolved but not used as a filter - the rows are the same for everyone. It still
		// runs so a library role with no agency claim is refused here too, rather than
		// reaching the consortium-wide table by the one route that does not narrow.
		return statsScopeGuard.resolve(authentication, null)
			.flatMap(scope -> Flux.from(patronRequestRepository.findPeerBenchmarks(startDate, endDate))
				.collectList());
	}

	// Consortial collection analysis by a chosen dimension (format | language | subject | decade).
	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/demand-by-dimension")
	public Mono<List<DimensionDemandStat>> getDemandByDimension(
		@QueryValue(defaultValue = "format") String dimension,
		@Nullable @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					final Flux<DimensionDemandStat> result = switch (dimension.toLowerCase()) {
						case "language" -> Flux.from(patronRequestRepository.findDemandByLanguage(libraryCode, startDate, endDate));
						case "subject" -> Flux.from(patronRequestRepository.findDemandBySubject(libraryCode, startDate, endDate));
						case "decade" -> Flux.from(patronRequestRepository.findDemandByPublicationDecade(libraryCode, startDate, endDate));
						// Reuse the existing, already-verified format query, mapped onto the generic shape.
						default -> Flux.from(patronRequestRepository.findDemandByFormat(libraryCode, startDate, endDate))
							.map(f -> new DimensionDemandStat(f.format(), f.requestCount()));
					};

					return result.collectList();
			});
	}

	// Unmet local demand: titles this library's own patrons requested but the library does not hold.
	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/unmet-local-demand")
	public Mono<List<TopClusterStat>> getUnmetLocalDemand(
		@NotNull @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					return Flux.from(patronRequestRepository.findUnmetLocalDemandForLibrary(libraryCode, startDate, endDate))
						.collectList();
			});
	}

	// Acquisition opportunities: consortium-wide highly-requested titles the library does not hold.
	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/acquisition-opportunities")
	public Mono<List<TopClusterStat>> getAcquisitionOpportunities(
		@NotNull @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					return Flux.from(clusterRecordRepository.findAcquisitionOpportunitiesForLibrary(libraryCode, startDate, endDate))
						.collectList();
			});
	}

	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/consortial-lifeline")
	public Mono<List<ConsortialLifelineStat>> getConsortialLifeline(
		@NotNull @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					return Flux.from(patronRequestRepository.findConsortialLifelineForLibrary(libraryCode, startDate, endDate)).collectList();
			});
	}

	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/demand-by-patron-group")
	public Mono<List<PatronGroupDemandStat>> getDemandByPatronGroup(
		@Nullable @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					return Flux.from(patronRequestRepository.findDemandByPatronGroup(libraryCode, startDate, endDate))
						.collectList();
			});
	}

	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/demand-by-pickup-location/{pickupLocationCode}")
	public Mono<Long> getDemandForSinglePickupLocation(
		@PathVariable String pickupLocationCode,
		@Nullable @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					return Mono.from(patronRequestRepository.countDemandForPickupLocation(libraryCode, pickupLocationCode, startDate, endDate))
						.defaultIfEmpty(0L);
			});
	}

	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/demand-by-pickup-location")
	public Mono<List<PickupLocationDemandStat>> getDemandByPickupLocation(
		@Nullable @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					return Flux.from(patronRequestRepository.findDemandByPickupLocation(libraryCode, startDate, endDate))
						.collectList();
			});
	}

	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/new-acquisitions-performance")
	public Mono<List<NewAcquisitionPerformanceStat>> getNewAcquisitionsPerformance(
		@NotNull @QueryValue String requestedLibraryCode,
		@NotNull @QueryValue Instant acquiredSince,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					return Flux.from(patronRequestRepository.findNewAcquisitionsPerformance(libraryCode, acquiredSince, startDate, endDate))
						.collectList();
			});
	}

	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/collection-balance")
	public Mono<CollectionBalanceStat> getCollectionBalance(
		@NotNull @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					// Default to 0L to ensure Mono.zip doesn't drop empty results
					Mono<Long> borrowed = Mono.from(patronRequestRepository.countBorrowedForLibrary(libraryCode, startDate, endDate)).defaultIfEmpty(0L);
					Mono<Long> supplied = Mono.from(patronRequestRepository.countSuppliedForLibrary(libraryCode, startDate, endDate)).defaultIfEmpty(0L);

					return Mono.zip(borrowed, supplied)
						.map(tuple -> new CollectionBalanceStat(tuple.getT1(), tuple.getT2()));
			});
	}


	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/unfillable-demand")
	public Mono<List<UnfillableDemandStat>> getUnfillableDemand(
		@Nullable @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					return Flux.from(patronRequestRepository.findUnfillableDemand(libraryCode, startDate, endDate))
						.collectList();
			});
	}

	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/saved-by-re-resolution")
	public Mono<Long> getSavedByReResolution(
		@Nullable @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					return Mono.from(patronRequestRepository.countSavedByReresolution(libraryCode, startDate, endDate))
						.defaultIfEmpty(0L);
			});
	}

	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/fulfillment/borrower")
	public Mono<FulfillmentStat> getBorrowerFulfillmentRate(
		@Nullable @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					return Mono.from(patronRequestRepository.getBorrowerFulfillmentStats(libraryCode, startDate, endDate))
						.defaultIfEmpty(new FulfillmentStat(0L, 0L));
			});
	}

	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/fulfillment/supplier")
	public Mono<FulfillmentStat> getSupplierFulfillmentRate(
		@Nullable @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					return Mono.from(patronRequestRepository.getSupplierFulfillmentStats(libraryCode, startDate, endDate))
						.defaultIfEmpty(new FulfillmentStat(0L, 0L));
			});
	}

	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/unique-contributions")
	public Mono<List<RareGem>> getUniqueContributions(
		@NotNull @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> {
				final var libraryCode = scope.libraryCode();

					return Flux.from(patronRequestRepository.findUniqueContributions(libraryCode, startDate, endDate))
						.collectList();
			});
	}

	/**
	 * Who this library trades with most, both directions in one ranking, with the borrow and
	 * supply split kept alongside the total.
	 *
	 * requestedLibraryCode is mandatory even for a consortium administrator: "who do WE trade
	 * with" needs a "we", and without one the query has nothing to rank against.
	 */
	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/top-partners")
	public Mono<Page<TradingPartnerStat>> getTopTradingPartners(
		@NotNull @QueryValue String requestedLibraryCode,
		@Nullable @QueryValue Instant startDate,
		@Nullable @QueryValue Instant endDate,
		@Nullable Pageable pageable,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> Mono.from(patronRequestRepository.findTopTradingPartners(
				scope.libraryCode(), startDate, endDate, rankedByTotal(pageable))));
	}

	/**
	 * The query carries no ORDER BY of its own, so an unsorted request would page through an
	 * arbitrary order while still being called "top partners". Default the sort rather than
	 * hard-coding it, so a caller can still rank by either direction.
	 */
	private static Pageable rankedByTotal(@Nullable Pageable pageable) {
		if (pageable == null) {
			return Pageable.from(0, DEFAULT_PARTNER_PAGE_SIZE, TOTAL_DESCENDING);
		}

		return pageable.getSort().isSorted()
			? pageable
			: Pageable.from(pageable.getNumber(), pageable.getSize(), TOTAL_DESCENDING);
	}

	// === Collection analysis over the catalogue ==============================================
	//
	// These aggregate bib_record rather than patron_request. The first four are
	// consortium-wide, so they resolve the caller and then deliberately DISCARD the scope: a
	// library administrator sees every library's profile. That is a decision - docs/insights.md
	// part 5. resolve() still earns its keep, because a library role with no agency claim is
	// refused rather than silently served.

	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/collection-totals")
	public Mono<CollectionTotalsStat> getCollectionTotals(Authentication authentication) {

		return statsScopeGuard.resolve(authentication, null)
			.then(collectionAnalysisService.totals());
	}

	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/collection-profile")
	public Mono<List<CollectionProfileStat>> getCollectionProfile(Authentication authentication) {

		return statsScopeGuard.resolve(authentication, null)
			.then(collectionAnalysisService.profile());
	}

	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/cluster-size-distribution")
	public Mono<List<ClusterSizeStat>> getClusterSizeDistribution(Authentication authentication) {

		return statsScopeGuard.resolve(authentication, null)
			.then(collectionAnalysisService.clusterSizeDistribution());
	}

	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/format-profile")
	public Mono<List<SourceFormatStat>> getFormatProfile(Authentication authentication) {

		return statsScopeGuard.resolve(authentication, null)
			.then(collectionAnalysisService.formatProfile());
	}

	/**
	 * Who duplicates this library. requestedLibraryCode is mandatory even for a consortium
	 * administrator: anchoring to one library is what keeps this linear, and making it optional
	 * would invite the quadratic full matrix back in by the side door.
	 */
	@Secured({CONSORTIUM_ADMIN, LIBRARY_ADMIN, ADMINISTRATOR})
	@Get("/collection-overlap")
	public Mono<List<CollectionOverlapStat>> getCollectionOverlap(
		@NotNull @QueryValue String requestedLibraryCode,
		Authentication authentication) {

		return statsScopeGuard.resolve(authentication, requestedLibraryCode)
			.flatMap(scope -> collectionAnalysisService.overlapFor(scope.libraryCode()));
	}
}
