package org.olf.dcb.storage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.olf.dcb.core.model.PatronRequest.Status.CANCELLED;
import static org.olf.dcb.core.model.PatronRequest.Status.CONFIRMED;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.FINALISED;
import static org.olf.dcb.core.model.PatronRequest.Status.LOANED;
import static org.olf.dcb.core.model.PatronRequest.Status.NO_ITEMS_SELECTABLE_AT_ANY_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.REQUEST_PLACED_AT_SUPPLYING_AGENCY;
import static org.olf.dcb.core.model.PatronRequest.Status.RESOLVED;
import static org.olf.dcb.core.model.PatronRequest.Status.SUBMITTED_TO_DCB;
import static org.olf.dcb.core.model.WorkflowConstants.LOCAL_WORKFLOW;
import static org.olf.dcb.core.model.WorkflowConstants.STANDARD_WORKFLOW;
import static org.olf.dcb.test.PublisherUtils.manyValuesFrom;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.api.serde.CollectionBalanceStat;
import org.olf.dcb.core.api.serde.DemandHeatCell;
import org.olf.dcb.core.api.serde.FailureReasonStat;
import org.olf.dcb.core.api.serde.NetFlowStat;
import org.olf.dcb.core.api.serde.PeerBenchmarkStat;
import org.olf.dcb.core.api.serde.StatusDwellStat;
import org.olf.dcb.core.api.serde.SupplierReliabilityStat;
import org.olf.dcb.core.api.serde.SupplierResponseStat;
import org.olf.dcb.core.api.serde.TimeSeriesPoint;
import org.olf.dcb.core.api.serde.TradingPartnerStat;
import org.olf.dcb.core.api.serde.TurnaroundStat;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.PatronRequestAudit;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LibraryFixture;
import org.olf.dcb.test.PatronRequestsFixture;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;

@DcbTest
class StatsQueriesTests {
	@Inject
	private PatronRequestsFixture patronRequestsFixture;

	@Inject
	private PatronRequestRepository patronRequestRepository;

	@Inject
	private PatronRequestAuditRepository patronRequestAuditRepository;

	@Inject
	private R2dbcOperations r2dbcOperations;

	@Inject
	private LibraryFixture libraryFixture;

	@Inject
	private AgencyFixture agencyFixture;

	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAll();
		libraryFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
	}

	/** What InsightsController applies when the caller names no sort. */
	private static final Sort RANKED_BY_TOTAL = Sort.of(Sort.Order.desc("total_count"));

	private List<TradingPartnerStat> tradingPartners(String libraryCode, Sort sort) {
		return singleValueFrom(patronRequestRepository.findTopTradingPartners(
			libraryCode, null, null, Pageable.from(0, 25, sort))).getContent();
	}

	/**
	 * A Host LMS onboarded as a named library, which is what the name resolution walks:
	 * host_lms.code -> agency.host_lms_id -> library.agency_code -> library.full_name.
	 */
	private void onboard(String hostLmsCode, String agencyCode, String fullName) {
		final var hostLms = hostLmsFixture.createDummyHostLms(hostLmsCode);

		agencyFixture.defineAgency(agencyCode, agencyCode, hostLms);

		libraryFixture.saveLibrary(Library.builder()
			.id(UUID.randomUUID())
			.agencyCode(agencyCode)
			.fullName(fullName)
			.shortName(fullName)
			.abbreviatedName(fullName)
			.build());
	}

	// --- helpers -------------------------------------------------------------

	private PatronRequest saveRequest(Status status, String borrower, String supplier,
			Status previousStatus, String workflow) {
		return patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(UUID.randomUUID())
			.status(status)
			.patronHostlmsCode(borrower)
			.localItemHostlmsCode(supplier)
			.previousStatus(previousStatus)
			.activeWorkflow(workflow)
			.build());
	}

	/**
	 * audit_date is {@code @DateCreated}, so the builder value is overwritten with "now" on
	 * insert - this helper used to accept an auditDate and silently ignore it, which is why the
	 * flow time-series could only ever be asserted over an unbounded window. Setting it back
	 * with an UPDATE is the only way to place an audit at a chosen instant.
	 */
	/** Companion to {@link #saveAudit}: date_created is @DateCreated and equally unwritable. */
	private void setDateCreated(PatronRequest pr, Instant dateCreated) {
		singleValueFrom(r2dbcOperations.withConnection(connection ->
			Flux.from(connection.createStatement(
					"UPDATE patron_request SET date_created = $1 WHERE id = $2")
				.bind("$1", LocalDateTime.ofInstant(dateCreated, ZoneOffset.UTC))
				.bind("$2", pr.getId())
				.execute())
			.flatMap(result -> result.getRowsUpdated())));
	}

	private void saveAudit(PatronRequest pr, Status toStatus, Instant auditDate) {
		final var id = UUID.randomUUID();

		singleValueFrom(patronRequestAuditRepository.save(PatronRequestAudit.builder()
			.id(id)
			.patronRequest(pr)
			.toStatus(toStatus)
			.build()));

		singleValueFrom(r2dbcOperations.withConnection(connection ->
			Flux.from(connection.createStatement(
					"UPDATE patron_request_audit SET audit_date = $1 WHERE id = $2")
				.bind("$1", LocalDateTime.ofInstant(auditDate, ZoneOffset.UTC))
				.bind("$2", id)
				.execute())
			.flatMap(result -> result.getRowsUpdated())));
	}

	private PatronRequest saveRequestForCluster(Status status, String borrower, UUID clusterId) {
		return patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(UUID.randomUUID())
			.status(status)
			.patronHostlmsCode(borrower)
			.bibClusterId(clusterId)
			.activeWorkflow(STANDARD_WORKFLOW)
			.build());
	}

	// auditDate is @DateCreated (auto-now); ordering follows insertion order.
	private void saveTransition(PatronRequest pr, Status fromStatus, Status toStatus) {
		singleValueFrom(patronRequestAuditRepository.save(PatronRequestAudit.builder()
			.id(UUID.randomUUID())
			.patronRequest(pr)
			.fromStatus(fromStatus)
			.toStatus(toStatus)
			.build()));
	}

	// --- the in-flight set is a domain constant, not a DB flag ----------------

	@Test
	void activeStateSetIsTheExpectedElevenInFlightStatuses() {
		assertThat(PatronRequest.Status.ACTIVE_STATE_CODES, containsInAnyOrder(
			"SUBMITTED_TO_DCB", "PATRON_VERIFIED", "RESOLVED",
			"REQUEST_PLACED_AT_SUPPLYING_AGENCY", "REQUEST_PLACED_AT_BORROWING_AGENCY",
			"REQUEST_PLACED_AT_PICKUP_AGENCY", "CONFIRMED", "PICKUP_TRANSIT",
			"RECEIVED_AT_PICKUP", "READY_FOR_PICKUP", "LOANED"));
	}

	// --- flow time-series (audit-derived) ------------------------------------

	@Test
	void flowTimeSeriesCountsTransitionsIntoEachStatusPerBucket() {
		final var pr = saveRequest(LOANED, "LIB_A", "SUP_A", null, STANDARD_WORKFLOW);
		final var sameDay = Instant.parse("2025-01-15T10:00:00Z");

		saveAudit(pr, LOANED, sameDay);
		saveAudit(pr, LOANED, sameDay.plusSeconds(3600));
		saveAudit(pr, ERROR, sameDay.plusSeconds(7200));

		final var points = manyValuesFrom(patronRequestRepository.findStatusFlowTimeSeries(
			"day", null, utc("2025-01-15T00:00:00Z"), utc("2025-01-16T00:00:00Z"), 1001));

		// All three audits fall in one day bucket: LOANED x2, ERROR x1.
		final var bySeries = points.stream()
			.collect(Collectors.toMap(TimeSeriesPoint::series, TimeSeriesPoint::count));

		assertThat(bySeries.get("LOANED"), equalTo(2L));
		assertThat(bySeries.get("ERROR"), equalTo(1L));
	}

	@Test
	void flowTimeSeriesFillsQuietBucketsWithZeroRatherThanOmittingThem() {
		// The contract on TimeBucket: an omitted bucket compresses the axis and makes a quiet
		// day look like it did not happen. It must come back as a zero.
		final var pr = saveRequest(LOANED, "LIB_A", "SUP_A", null, STANDARD_WORKFLOW);

		saveAudit(pr, LOANED, Instant.parse("2025-03-01T10:00:00Z"));
		saveAudit(pr, LOANED, Instant.parse("2025-03-03T10:00:00Z"));

		final var counts = manyValuesFrom(patronRequestRepository.findStatusFlowTimeSeries(
				"day", null, utc("2025-03-01T00:00:00Z"), utc("2025-03-04T00:00:00Z"), 1001))
			.stream()
			.filter(point -> "LOANED".equals(point.series()))
			.collect(Collectors.toMap(TimeSeriesPoint::bucket, TimeSeriesPoint::count));

		assertThat(counts.get(Instant.parse("2025-03-01T00:00:00Z")), equalTo(1L));
		assertThat(counts.get(Instant.parse("2025-03-02T00:00:00Z")), equalTo(0L));
		assertThat(counts.get(Instant.parse("2025-03-03T00:00:00Z")), equalTo(1L));
	}

	@Test
	void flowTimeSeriesTreatsTheEndOfTheWindowAsExclusive() {
		// Half-open windows tile without double counting. An audit landing exactly on the upper
		// bound belongs to the NEXT window, not this one.
		final var pr = saveRequest(LOANED, "LIB_A", "SUP_A", null, STANDARD_WORKFLOW);

		saveAudit(pr, LOANED, Instant.parse("2025-04-02T00:00:00Z"));

		final var points = manyValuesFrom(patronRequestRepository.findStatusFlowTimeSeries(
			"day", null, utc("2025-04-01T00:00:00Z"), utc("2025-04-02T00:00:00Z"), 1001));

		assertThat(points.stream().map(TimeSeriesPoint::series).toList(), not(hasItem("LOANED")));
	}

	@Test
	void flowTimeSeriesCapsTheGeneratedSeriesSoAnOverLongWindowCanBeRejected() {
		// The SQL caps the generated grid; the controller compares the distinct bucket count
		// against MAX_BUCKETS and rejects rather than returning a silently clipped chart.
		final var pr = saveRequest(LOANED, "LIB_A", "SUP_A", null, STANDARD_WORKFLOW);

		saveAudit(pr, LOANED, Instant.parse("2025-05-01T10:00:00Z"));

		final var buckets = manyValuesFrom(patronRequestRepository.findStatusFlowTimeSeries(
				"day", null, utc("2025-05-01T00:00:00Z"), utc("2025-05-10T00:00:00Z"), 3))
			.stream()
			.map(TimeSeriesPoint::bucket)
			.distinct()
			.toList();

		assertThat(buckets, hasSize(3));
	}

	@Test
	void flowTimeSeriesBucketsAreTheSameInstantsWhateverTheJvmZone() {
		// date_trunc returns `timestamp WITHOUT time zone`, so turning a bucket into an
		// Instant needs a zone from somewhere. Left implicit it comes from the JVM default -
		// the DEPLOYMENT's zone, not the column's - and every bucket shifts by that offset.
		// A service running anywhere but UTC labels its Insights charts a day out, and this
		// suite passes on a UTC CI runner while failing on a developer's machine in Berlin.
		//
		// A fixed non-UTC zone rather than the ambient one, so this fails everywhere without
		// the fix instead of only where somebody happens to be sitting.
		final var original = TimeZone.getDefault();
		TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));

		try {
			final var pr = saveRequest(LOANED, "LIB_A", "SUP_A", null, STANDARD_WORKFLOW);

			saveAudit(pr, LOANED, Instant.parse("2025-07-01T10:00:00Z"));

			final var buckets = manyValuesFrom(patronRequestRepository.findStatusFlowTimeSeries(
					"day", null, utc("2025-07-01T00:00:00Z"), utc("2025-07-02T00:00:00Z"), 1001))
				.stream()
				.map(TimeSeriesPoint::bucket)
				.distinct()
				.toList();

			assertThat(buckets, contains(Instant.parse("2025-07-01T00:00:00Z")));
		}
		finally {
			TimeZone.setDefault(original);
		}
	}

	private static LocalDateTime utc(String isoInstant) {
		return LocalDateTime.ofInstant(Instant.parse(isoInstant), ZoneOffset.UTC);
	}

	// --- percentile turnaround (not AVG) -------------------------------------

	@Test
	void turnaroundReportsOrderedNonNegativePercentiles() {
		// saveAudit now really does place the audit at the instant it is given, so this can
		// assert an actual turnaround rather than merely that the percentile query executes.
		//
		// Both ends are pinned explicitly rather than deriving the audit from
		// pr.getDateCreated(). date_created is written by the framework in the JVM's default
		// zone into a timestamp WITHOUT time zone column, so mixing it with a UTC-written
		// audit_date makes the measured gap depend on the build machine's offset - under BST
		// that showed up as a turnaround of exactly -3600 seconds.
		final var placed = Instant.parse("2025-06-01T09:00:00Z");

		for (var minutes : List.of(10L, 20L, 30L)) {
			final var pr = saveRequest(LOANED, "LIB_A", "SUP_A", null, STANDARD_WORKFLOW);
			setDateCreated(pr, placed);
			saveAudit(pr, LOANED, placed.plusSeconds(minutes * 60));
		}

		final var turnaround = singleValueFrom(
			patronRequestRepository.findTurnaroundToStatus(null, "LOANED", null, null));

		assertThat(turnaround.p50Seconds(), closeTo(1200.0, 1.0));
		assertThat(turnaround.p95Seconds() >= turnaround.p50Seconds(), equalTo(true));
	}

	@Test
	void turnaroundExcludesLocalWorkflowRequests() {
		final var local = saveRequest(LOANED, "LIB_A", "SUP_A", null, LOCAL_WORKFLOW);
		saveAudit(local, LOANED, local.getDateCreated().plusSeconds(100));

		final var turnaround = singleValueFrom(
			patronRequestRepository.findTurnaroundToStatus(null, "LOANED", null, null));

		// Only RET-LOCAL rows exist, so the window is effectively empty -> COALESCE to 0.
		assertThat(turnaround.p50Seconds(), equalTo(0.0));
		assertThat(turnaround.p95Seconds(), equalTo(0.0));
	}

	// --- failure taxonomy ----------------------------------------------------

	@Test
	void failureTaxonomyBucketsByDerivedReason() {
		saveRequest(CANCELLED, "LIB_A", "SUP_A", null, STANDARD_WORKFLOW);
		saveRequest(ERROR, "LIB_A", "SUP_A", REQUEST_PLACED_AT_SUPPLYING_AGENCY, STANDARD_WORKFLOW);
		saveRequest(CONFIRMED, "LIB_A", "SUP_A", NO_ITEMS_SELECTABLE_AT_ANY_AGENCY, STANDARD_WORKFLOW);

		final var taxonomy = manyValuesFrom(
			patronRequestRepository.findFailureTaxonomy(null, null, null));

		final var byReason = taxonomy.stream()
			.collect(Collectors.toMap(FailureReasonStat::reason, FailureReasonStat::count));

		assertThat(byReason.get("CANCELLED"), equalTo(1L));
		assertThat(byReason.get("ERROR_AT_REQUEST_PLACED_AT_SUPPLYING_AGENCY"), equalTo(1L));
		assertThat(byReason.get("NO_ITEMS_SELECTABLE"), equalTo(1L));
	}

	// --- supplier reliability ------------------------------------------------

	@Test
	void supplierReliabilitySplitsFulfilledFromFailed() {
		saveRequest(LOANED, "LIB_A", "SUP_A", null, STANDARD_WORKFLOW);
		saveRequest(FINALISED, "LIB_B", "SUP_A", null, STANDARD_WORKFLOW);
		saveRequest(ERROR, "LIB_A", "SUP_A", REQUEST_PLACED_AT_SUPPLYING_AGENCY, STANDARD_WORKFLOW);

		final var reliability = manyValuesFrom(
			patronRequestRepository.findSupplierReliability("SUP_A", null, null));

		assertThat(reliability, hasSize(1));
		final var supA = reliability.get(0);
		assertThat(supA.supplierCode(), equalTo("SUP_A"));
		assertThat(supA.fulfilledCount(), equalTo(2L));
		assertThat(supA.failedCount(), equalTo(1L));
	}

	// --- net flow / reciprocity ----------------------------------------------

	@Test
	void netFlowReportsBorrowedVersusSuppliedPerLibrary() {
		// LIB_A borrows twice (as patron) and supplies once (as item owner).
		saveRequest(LOANED, "LIB_A", "SUP_X", null, STANDARD_WORKFLOW);
		saveRequest(LOANED, "LIB_A", "SUP_Y", null, STANDARD_WORKFLOW);
		saveRequest(LOANED, "LIB_B", "LIB_A", null, STANDARD_WORKFLOW);

		final var netFlow = manyValuesFrom(
			patronRequestRepository.findNetFlow("LIB_A", null, null));

		assertThat(netFlow, hasSize(1));
		final var libA = netFlow.get(0);
		assertThat(libA.libraryCode(), equalTo("LIB_A"));
		assertThat(libA.borrowedCount(), equalTo(2L));
		assertThat(libA.suppliedCount(), equalTo(1L));
	}

	// --- time-in-status / bottleneck -----------------------------------------

	@Test
	void timeInStatusReportsMedianDwellPerFromStatus() {
		final var pr = saveRequest(LOANED, "LIB_A", "SUP_A", null, STANDARD_WORKFLOW);
		// A chain of transitions; dwell is attributed to each transition's from_status.
		// The entry row (from = null) is excluded, as is the first row (no LAG predecessor).
		saveTransition(pr, null, SUBMITTED_TO_DCB);
		saveTransition(pr, SUBMITTED_TO_DCB, RESOLVED);
		saveTransition(pr, RESOLVED, LOANED);

		final var dwell = manyValuesFrom(
			patronRequestRepository.findTimeInStatus(null, null, null));
		final var byStatus = dwell.stream()
			.collect(Collectors.toMap(StatusDwellStat::status, s -> s));

		assertThat(
			byStatus.keySet(), containsInAnyOrder("SUBMITTED_TO_DCB", "RESOLVED"));
		assertThat(byStatus.get("SUBMITTED_TO_DCB").sampleCount(), equalTo(1L));
		assertThat(
			byStatus.get("SUBMITTED_TO_DCB").medianDwellSeconds(), notNullValue());
	}

	// --- supplier-response SLA -----------------------------------------------

	@Test
	void supplierResponseSlaMeasuresPlacedToConfirmed() {
		final var pr = saveRequest(CONFIRMED, "LIB_A", "SUP_A", null, STANDARD_WORKFLOW);
		saveTransition(pr, RESOLVED, REQUEST_PLACED_AT_SUPPLYING_AGENCY);
		saveTransition(pr, REQUEST_PLACED_AT_SUPPLYING_AGENCY, CONFIRMED);

		final var sla = manyValuesFrom(
			patronRequestRepository.findSupplierResponseSla("SUP_A", null, null));

		assertThat(sla, hasSize(1));
		assertThat(sla.get(0).supplierCode(), equalTo("SUP_A"));
		assertThat(sla.get(0).sampleCount(), equalTo(1L));
		// Confirmed was recorded after placed, so the SLA is non-negative.
		assertThat(sla.get(0).medianResponseSeconds() >= 0.0, equalTo(true));
	}

	// --- demand heatmap ------------------------------------------------------

	@Test
	void demandHeatmapBucketsByDayOfWeekAndHour() {
		saveRequest(LOANED, "LIB_A", "SUP_A", null, STANDARD_WORKFLOW);
		saveRequest(LOANED, "LIB_A", "SUP_A", null, STANDARD_WORKFLOW);
		saveRequest(CONFIRMED, "LIB_A", "SUP_A", null, STANDARD_WORKFLOW);

		final var cells = manyValuesFrom(
			patronRequestRepository.findDemandHeatmap(null, null, null));

		final long total = cells.stream()
			.mapToLong(DemandHeatCell::requestCount)
			.sum();
		assertThat(total, equalTo(3L));
		for (final var cell : cells) {
			assertThat(cell.dayOfWeek() >= 0 && cell.dayOfWeek() <= 6, equalTo(true));
			assertThat(cell.hourOfDay() >= 0 && cell.hourOfDay() <= 23, equalTo(true));
		}
	}

	// --- checkout rate -------------------------------------------------------

	@Test
	void checkoutRateCountsRequestsThatEverReachedLoaned() {
		final var reached = saveRequest(LOANED, "LIB_A", "SUP_A", null, STANDARD_WORKFLOW);
		saveTransition(reached, CONFIRMED, LOANED); // audit shows it hit the shelf
		saveRequest(CONFIRMED, "LIB_A", "SUP_A", null, STANDARD_WORKFLOW); // never loaned

		final var rate = singleValueFrom(
			patronRequestRepository.findCheckoutRate(null, null, null));

		assertThat(rate.totalCount(), equalTo(2L));
		assertThat(rate.reachedCount(), equalTo(1L));
	}

	// --- collection summary (unique titles) ----------------------------------

	@Test
	void collectionSummaryCountsDistinctRequestedTitles() {
		final var titleX = UUID.randomUUID();
		final var titleY = UUID.randomUUID();
		saveRequestForCluster(LOANED, "LIB_A", titleX);
		saveRequestForCluster(LOANED, "LIB_A", titleX); // same title requested again
		saveRequestForCluster(LOANED, "LIB_A", titleY);

		final var summary = singleValueFrom(
			patronRequestRepository.findCollectionSummary(null, null, null));

		assertThat(summary.totalRequests(), equalTo(3L));
		assertThat(summary.uniqueTitlesRequested(), equalTo(2L));
	}

	// --- turnaround for library / consortium / combinations ------------------

	@Test
	void turnaroundAcceptsSingleConsortiumAndCombinationScopes() {
		final var a = saveRequest(LOANED, "LIB_A", "SUP_A", null, STANDARD_WORKFLOW);
		saveTransition(a, CONFIRMED, LOANED);
		final var b = saveRequest(LOANED, "LIB_B", "SUP_A", null, STANDARD_WORKFLOW);
		saveTransition(b, CONFIRMED, LOANED);

		// Single library, consortium (null) and a combination all execute and return a stat.
		assertThat(
			singleValueFrom(patronRequestRepository.findTurnaroundToStatus("LIB_A", "LOANED", null, null)).p50Seconds(),
			notNullValue());
		assertThat(
			singleValueFrom(patronRequestRepository.findTurnaroundToStatus(null, "LOANED", null, null)).p50Seconds(),
			notNullValue());
		assertThat(
			singleValueFrom(patronRequestRepository.findTurnaroundToStatus("LIB_A,LIB_B", "LOANED", null, null)).p50Seconds(),
			notNullValue());
		// A code that matches nothing yields the COALESCE(0) fallback - proves the CSV
		// membership filter actually excludes.
		final var none = singleValueFrom(
			patronRequestRepository.findTurnaroundToStatus("NO_SUCH_LIB", "LOANED", null, null));
		assertThat(none.p50Seconds(), equalTo(0.0));
		assertThat(none.p95Seconds(), equalTo(0.0));
	}

	// --- peer benchmarking ---------------------------------------------------

	@Test
	void peerBenchmarksReportPerLibraryCountsForComparison() {
		final var loaned = saveRequest(LOANED, "LIB_A", "SUP_A", null, STANDARD_WORKFLOW);
		saveTransition(loaned, CONFIRMED, LOANED); // reached the shelf + success
		saveRequest(ERROR, "LIB_A", "SUP_A", REQUEST_PLACED_AT_SUPPLYING_AGENCY, STANDARD_WORKFLOW); // failed
		saveRequest(LOANED, "LIB_B", "SUP_A", null, STANDARD_WORKFLOW); // different library

		final var benchmarks = manyValuesFrom(
			patronRequestRepository.findPeerBenchmarks(null, null));
		final var byLib = benchmarks.stream()
			.collect(Collectors.toMap(PeerBenchmarkStat::libraryCode, b -> b));

		assertThat(byLib.get("LIB_A").totalRequests(), equalTo(2L));
		assertThat(byLib.get("LIB_A").checkoutCount(), equalTo(1L));
		assertThat(byLib.get("LIB_A").successCount(), equalTo(1L));
		assertThat(byLib.get("LIB_A").failedCount(), equalTo(1L));
		assertThat(byLib.get("LIB_B").totalRequests(), equalTo(1L));
	}

	// --- trading partners ----------------------------------------------------

	@Test
	void topPartnersAreDirectionalAndNamed() {
		onboard("SUP_A", "AG_SUP_A", "Northern University Library");
		onboard("LIB_B", "AG_LIB_B", "Riverside Public Library");

		// LIB_A borrows twice from SUP_A, and supplies LIB_B once.
		saveRequest(LOANED, "LIB_A", "SUP_A", null, STANDARD_WORKFLOW);
		saveRequest(LOANED, "LIB_A", "SUP_A", null, STANDARD_WORKFLOW);
		saveRequest(LOANED, "LIB_B", "LIB_A", null, STANDARD_WORKFLOW);

		final var suppliers = manyValuesFrom(
			patronRequestRepository.findTopSuppliersForLibrary("LIB_A", null, null));

		assertThat(suppliers, hasSize(1));
		assertThat(suppliers.get(0).partnerCode(), equalTo("SUP_A"));
		assertThat(suppliers.get(0).partnerName(), equalTo("Northern University Library"));
		assertThat(suppliers.get(0).requestCount(), equalTo(2L));

		// The mirror. Who borrowed FROM us is a different question, and the two lists must not
		// be derivable from one another - LIB_B appears here and nowhere above.
		final var borrowers = manyValuesFrom(
			patronRequestRepository.findTopBorrowersFromLibrary("LIB_A", null, null));

		assertThat(borrowers, hasSize(1));
		assertThat(borrowers.get(0).partnerCode(), equalTo("LIB_B"));
		assertThat(borrowers.get(0).partnerName(), equalTo("Riverside Public Library"));
		assertThat(borrowers.get(0).requestCount(), equalTo(1L));
	}

	@Test
	void topPartnersReportANullNameForAHostLmsThatIsNotAnOnboardedLibrary() {
		// A Host LMS can ingest requests without a library row - the case that made
		// PeerBenchmarkStat.libraryName nullable, and it reaches this constructor identically.
		// Without @Nullable the query fails to deserialise rather than returning a bare code.
		saveRequest(LOANED, "LIB_A", "SUP_UNKNOWN", null, STANDARD_WORKFLOW);

		final var suppliers = manyValuesFrom(
			patronRequestRepository.findTopSuppliersForLibrary("LIB_A", null, null));

		assertThat(suppliers, hasSize(1));
		assertThat(suppliers.get(0).partnerCode(), equalTo("SUP_UNKNOWN"));
		assertThat(suppliers.get(0).partnerName(), nullValue());
	}

	@Test
	void topPartnersIgnoreLocalFulfilmentAndErrors() {
		// A local return is not a partnership, and an errored request is not traffic either.
		saveRequest(LOANED, "LIB_A", "SUP_A", null, LOCAL_WORKFLOW);
		saveRequest(ERROR, "LIB_A", "SUP_A", REQUEST_PLACED_AT_SUPPLYING_AGENCY,
			STANDARD_WORKFLOW);

		assertThat(manyValuesFrom(
			patronRequestRepository.findTopSuppliersForLibrary("LIB_A", null, null)), hasSize(0));
	}

	@Test
	void topPartnersFindTheLibraryOfAMultiLibraryCaller() {
		// StatsScopeGuard hands these queries a COMMA-SEPARATED set for somebody who
		// administers several libraries. Scalar equality matched none of it and returned an
		// empty panel that read as "no activity", while turnaround in the same response worked.
		saveRequest(LOANED, "LIB_A", "SUP_A", null, STANDARD_WORKFLOW);
		saveRequest(LOANED, "LIB_B", "SUP_A", null, STANDARD_WORKFLOW);

		final var suppliers = manyValuesFrom(
			patronRequestRepository.findTopSuppliersForLibrary("LIB_A,LIB_B", null, null));

		assertThat(suppliers, hasSize(1));
		assertThat(suppliers.get(0).requestCount(), equalTo(2L));
	}

	@Test
	void tradingPartnersRankOnBothDirectionsAndKeepTheSplit() {
		onboard("PEER_X", "AG_X", "Northern University Library");

		// PEER_X: one borrow, two supplies - three in total.
		saveRequest(LOANED, "LIB_A", "PEER_X", null, STANDARD_WORKFLOW);
		saveRequest(LOANED, "PEER_X", "LIB_A", null, STANDARD_WORKFLOW);
		saveRequest(LOANED, "PEER_X", "LIB_A", null, STANDARD_WORKFLOW);

		// PEER_Y: two borrows and nothing else - fewer in total, but more borrowing.
		saveRequest(LOANED, "LIB_A", "PEER_Y", null, STANDARD_WORKFLOW);
		saveRequest(LOANED, "LIB_A", "PEER_Y", null, STANDARD_WORKFLOW);

		final var partners = tradingPartners("LIB_A", RANKED_BY_TOTAL);

		assertThat(partners, hasSize(2));

		// Ranked on the TOTAL, which is the whole point - PEER_Y leads the borrow-only list
		// but trades less overall.
		final var top = partners.get(0);
		assertThat(top.partnerCode(), equalTo("PEER_X"));
		assertThat(top.partnerName(), equalTo("Northern University Library"));
		assertThat(top.borrowedFromCount(), equalTo(1L));
		assertThat(top.suppliedToCount(), equalTo(2L));
		assertThat(top.totalCount(), equalTo(3L));

		// The split survives, so an uneven relationship is still visible in the ranking.
		final var second = partners.get(1);
		assertThat(second.partnerCode(), equalTo("PEER_Y"));
		assertThat(second.borrowedFromCount(), equalTo(2L));
		assertThat(second.suppliedToCount(), equalTo(0L));
		assertThat(second.partnerName(), nullValue());
	}

	@Test
	void tradingPartnersExcludeTrafficInsideTheCallersOwnGroup() {
		// LIB_A and LIB_B are both the caller's. Traffic between them is internal, and counting
		// it once under each end would invent a partner out of the caller itself.
		saveRequest(LOANED, "LIB_A", "LIB_B", null, STANDARD_WORKFLOW);
		saveRequest(LOANED, "LIB_B", "LIB_A", null, STANDARD_WORKFLOW);
		saveRequest(LOANED, "LIB_A", "PEER_X", null, STANDARD_WORKFLOW);

		final var partners = tradingPartners("LIB_A,LIB_B", RANKED_BY_TOTAL);

		assertThat(partners, hasSize(1));
		assertThat(partners.get(0).partnerCode(), equalTo("PEER_X"));
		assertThat(partners.get(0).totalCount(), equalTo(1L));
	}

	@Test
	void tradingPartnersPageThroughTheWholeRankingRatherThanACappedTop() {
		// Three partners with distinct totals, so paging is observable rather than incidental.
		saveRequest(LOANED, "LIB_A", "PEER_X", null, STANDARD_WORKFLOW);
		saveRequest(LOANED, "LIB_A", "PEER_X", null, STANDARD_WORKFLOW);
		saveRequest(LOANED, "LIB_A", "PEER_X", null, STANDARD_WORKFLOW);
		saveRequest(LOANED, "LIB_A", "PEER_Y", null, STANDARD_WORKFLOW);
		saveRequest(LOANED, "LIB_A", "PEER_Y", null, STANDARD_WORKFLOW);
		saveRequest(LOANED, "LIB_A", "PEER_Z", null, STANDARD_WORKFLOW);

		final var firstPage = singleValueFrom(patronRequestRepository.findTopTradingPartners(
			"LIB_A", null, null, Pageable.from(0, 2, RANKED_BY_TOTAL)));

		// totalSize counts PARTNERS, not requests - the count query groups the union the same
		// way the page does. Six requests, three partners.
		assertThat(firstPage.getTotalSize(), equalTo(3L));
		assertThat(firstPage.getContent(), hasSize(2));
		assertThat(firstPage.getContent().get(0).partnerCode(), equalTo("PEER_X"));
		assertThat(firstPage.getContent().get(1).partnerCode(), equalTo("PEER_Y"));

		// The tail a fixed top-N would have hidden.
		final var secondPage = singleValueFrom(patronRequestRepository.findTopTradingPartners(
			"LIB_A", null, null, Pageable.from(1, 2, RANKED_BY_TOTAL)));

		assertThat(secondPage.getContent(), hasSize(1));
		assertThat(secondPage.getContent().get(0).partnerCode(), equalTo("PEER_Z"));
		assertThat(secondPage.getContent().get(0).totalCount(), equalTo(1L));
	}

	// --- combined-dashboard lend/borrow totals -------------------------------

	@Test
	void lendBorrowTotalsCountBothSidesForAScope() {
		saveRequest(LOANED, "LIB_A", "SUP_A", null, STANDARD_WORKFLOW); // LIB_A borrows
		saveRequest(LOANED, "LIB_B", "LIB_A", null, STANDARD_WORKFLOW); // LIB_A supplies

		final CollectionBalanceStat scoped = singleValueFrom(
			patronRequestRepository.findLendBorrowTotals("LIB_A", null, null));
		assertThat(scoped.borrowedCount(), equalTo(1L));
		assertThat(scoped.suppliedCount(), equalTo(1L));

		final CollectionBalanceStat consortium = singleValueFrom(
			patronRequestRepository.findLendBorrowTotals(null, null, null));
		assertThat(consortium.borrowedCount(), equalTo(2L));
		assertThat(consortium.suppliedCount(), equalTo(2L));
	}
}
