package org.olf.dcb.storage;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.olf.dcb.core.api.serde.*;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static services.k_int.utils.StringUtils.truncate;


public interface PatronRequestRepository {
	@NonNull
	@SingleResult
	Publisher<? extends PatronRequest> save(@Valid @NotNull @NonNull PatronRequest patronRequest);

	@NonNull
	@SingleResult
	Publisher<? extends PatronRequest> update(@Valid @NotNull @NonNull PatronRequest patronRequest);

	@NonNull
	@SingleResult
	Publisher<PatronRequest> findById(@NotNull UUID id);

	@NonNull
	Publisher<PatronRequest> queryAll();

	@NonNull
	@SingleResult
	Publisher<Page<PatronRequest>> queryAll(Pageable page);

	@SingleResult
	Publisher<Void> delete(UUID id);
	
	@Vetoed
	Publisher<PatronRequest> findAllTrackableRequests(Iterable<Status> terminalStates, Iterable<String> supplierStatuses, Iterable<String> supplierItemStatuses);

	// local_request_id must be not null, it must currently be in a tracked state and the request itself must be trackable
	@Query(value = "SELECT p.* from patron_request p  where ( p.local_request_status is null OR p.local_request_status in ( select code from status_code where model = 'PatronRequest' and tracked = true ) ) and p.status_code in ( select code from status_code where model = 'DCBRequest' and tracked = true )", nativeQuery = true)
	Publisher<PatronRequest> findTrackedPatronHolds();



	@Query(value = "SELECT p.* from patron_request p  where p.status_code in ( select code from status_code where model = 'DCBRequest' and tracked = true )", nativeQuery = true)
	Publisher<PatronRequest> findProgressibleDCBRequests();



	// Find all the virtual items that we need to track - there must be an item id, the current item state must be null or a tracked state
  // and the request itself must be in a trackable state (Ie don't track item states for FINALISED requests)
	@Query(value = "SELECT p.* from patron_request p  where ( p.local_item_status is null or p.local_item_status in ( select code from status_code where model = 'VirtualItem' and tracked = true ) ) and p.status_code in ( select code from status_code where model = 'DCBRequest' and tracked = true )", nativeQuery = true)
	Publisher<PatronRequest> findTrackedVirtualItems();

	@Query(value = "SELECT pr.* from patron_request pr, patron_identity pi, host_lms h where pr.patron_id = pi.patron_id and pi.host_lms_id = h.id and h.code = :patronSystem and pi.local_id = :patronId and pi.home_identity=true order by pr.date_updated", countQuery = "SELECT count(pr.id) from patron_request pr, patron_identity pi, host_lms h where pr.patron_id = pi.patron_id and pi.host_lms_id = h.id and h.code = :patronSystem and pi.local_id = :patronId and pi.home_identity=true", nativeQuery = true)
	Publisher<Page<PatronRequest>> findRequestsForPatron(String patronSystem, String patronId, Pageable pageable);

	/**
	 * One request, ONLY when it belongs to the given patron — the ownership check for
	 * self-service actions like patron-initiated cancellation. Empty = not found OR
	 * not yours; deliberately indistinguishable so ids cannot be probed.
	 *
	 * THE ownership predicate for patron self-service. {@link #findSummariesForPatron}
	 * uses exactly the same join, so "which requests are mine" and "may I cancel this
	 * one" can never disagree.
	 *
	 * Keyed on pr.patron_id plus the patron's HOME identity at the asserted Host LMS.
	 * NOT on requesting_identity_id, which looks more precise and is wrong for this
	 * purpose: that column is @Nullable and only populated later in the workflow by
	 * ValidatePatronTransition, so a join through it silently hides a request the
	 * patron has just placed (SUBMITTED_TO_DCB, PATRON_VERIFIED) from their own list.
	 * patron_id is set at creation and never null.
	 *
	 * The Patron row is one human; its home identity is who they are. A caller that can
	 * assert (hostLmsCode, localId) matching that home identity IS that human, so the
	 * request is theirs. DISTINCT guards the data-integrity edge case of a Patron
	 * carrying two home_identity rows.
	 */
	@Query(value = """
		SELECT DISTINCT pr.*
		FROM patron_request pr
		JOIN patron_identity pi ON pr.patron_id = pi.patron_id
		JOIN host_lms h ON pi.host_lms_id = h.id
		WHERE pr.id = :patronRequestId
		  AND h.code = :patronSystem
		  AND pi.local_id = :patronId
		  AND pi.home_identity = true
		""", nativeQuery = true)
	Publisher<PatronRequest> findOwnedRequest(UUID patronRequestId, String patronSystem, String patronId);


	@Introspected
	public record ScheduledTrackingRecord(UUID id, @Nullable String status_code, @Nullable Instant next_scheduled_poll) {	};

	// If you change the where clause, make sure you keep the index aligned otherwise it will probably do a full table scan 
	@Query(value = "SELECT pr.id, pr.status_code, pr.next_scheduled_poll from patron_request pr where pr.next_scheduled_poll < now() and pr.is_too_long = false order by pr.next_scheduled_poll", nativeQuery = true)
	Publisher<ScheduledTrackingRecord> findScheduledChecks();

	@SingleResult
	@NonNull
	default Publisher<PatronRequest> saveOrUpdate(@Valid @NotNull @NonNull PatronRequest pc) {
		return Mono.from(this.existsById(pc.getId()))
				.flatMap(update -> Mono.from(update ? this.update(pc) : this.save(pc)));
	}
	

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);
	
	@NonNull
	@SingleResult
	Publisher<Void> updateStatusAndErrorMessage(@Id UUID id, PatronRequest.Status status, String errorMessage);
	
	@NonNull
	@SingleResult
	Publisher<Void> updateIsTooLongAndNeedsAttention(@Id UUID id, Boolean isTooLong, Boolean needsAttention);
	
	@NonNull
	@SingleResult
	default Publisher<Void> updateStatusWithError(@Id UUID id, String errorMessage) {
		// Truncate message to length shorter than database column
		return updateStatusAndErrorMessage(id, ERROR, truncate(errorMessage, 255));
	}

	@NonNull
	@SingleResult
	Publisher<PatronIdentity> findRequestingIdentityById(UUID id);

	@NotNull
	@SingleResult
	@Query(value = "SELECT pr.* from patron_request pr, supplier_request sr where sr.patron_request_id = pr.id and sr.id = :srid", nativeQuery = true)
	Publisher<PatronRequest> getPRForSRID(UUID srid);

	@NotNull
	@SingleResult
	@Query(value = "SELECT pr.* from patron_request pr, inactive_supplier_request sr where sr.patron_request_id = pr.id and sr.id = :inactiveSupplierRequestId", nativeQuery = true)
	Publisher<PatronRequest> getPatronRequestByInactiveSupplierRequestId(UUID inactiveSupplierRequestId);

	// Borrowing system tracking updates
	Publisher<Long> updateLocalRequestTracking(@Id @NotNull UUID id, @Nullable String localRequestStatus, @Nullable String rawLocalRequestStatus, Instant localRequestLastCheckTimestamp, Long localRequestStatusRepeat);
	Publisher<Long> updateLocalItemTracking(@Id @NotNull UUID id, @Nullable String localItemStatus, @Nullable String rawLocalItemStatus, Instant localItemLastCheckTimestamp, Long localItemStatusRepeat);

	// Pickup system tracking updates
	Publisher<Long> updatePickupRequestTracking(@Id @NotNull UUID id, @Nullable String pickupRequestStatus, @Nullable String rawPickupRequestStatus, Instant pickupRequestLastCheckTimestamp, Long pickupRequestStatusRepeat);
	Publisher<Long> updatePickupItemTracking(@Id @NotNull UUID id, @Nullable String pickupItemStatus, @Nullable String rawPickupItemStatus, Instant pickupItemLastCheckTimestamp, Long pickupItemStatusRepeat);

	@Join("requestingIdentity")
	Publisher<PatronRequest> findAllByPatronHostlmsCodeAndBibClusterIdOrderByDateCreatedDesc(
		@NotNull @NonNull String patronHostlmsCode, @NotNull @NonNull UUID bibClusterId);

	Publisher<PatronRequest> findAllByPickupLocationCode(String pickupLocationCode);

  default Publisher<Long> getActiveRequestCountForPatron(String hostLmsCode, String patronId) {
    return getActiveRequestCountForPatron(hostLmsCode, patronId, Status.ACTIVE_STATE_CODES);
  }

  @SingleResult
  @Query(value = """
    select count(pr.*)
    from patron_request pr,
         patron_identity pi,
         host_lms hl
    where pr.requesting_identity_id = pi.id
      and pi.host_lms_id = hl.id
      and pi.local_id=:patronId
      and hl.code = :hostLmsCode
      and pr.status_code in (:activeStates)
    """, nativeQuery = true)
  Publisher<Long> getActiveRequestCountForPatron(String hostLmsCode, String patronId, Collection<String> activeStates);

	// The following methods provide a limited patron request summary for a patron's active and all-time requests.
	// This is intended for discovery services who cannot make use of the GraphQL APIs available
	// And who need to provide a summary for patrons
	@Query(value = """
        SELECT
            pr.id,
            CAST(pr.status_code AS VARCHAR) as status,
            CAST(pr.outcome_code AS VARCHAR) as outcome,
            CAST(pr.next_expected_status AS VARCHAR) as next_expected_status,
            pr.elapsed_time_in_current_status as time_in_state,
            pr.error_message,
            cr.title as title,
            pr.pickup_location_code,
            (SELECT l.name FROM location l WHERE l.code = pr.pickup_location_code LIMIT 1) as pickup_location_name,
            pr.active_workflow,
            pr.bib_cluster_id,
            pr.date_created,
            pr.date_updated
        FROM patron_request pr
        JOIN patron_identity pi ON pr.requesting_identity_id = pi.id
        LEFT JOIN cluster_record cr ON pr.bib_cluster_id = cr.id
        WHERE pr.patron_hostlms_code = :hostLmsCode
          -- local_barcode holds either a bare barcode or a list literal
          -- "[b1, b2, b3]" (see PatronIdentity.getParsedBarcodes). Match an ELEMENT
          -- EXACTLY. This was LIKE '%barcode%', which matched substrings: a barcode
          -- of '1' returned every patron at this Host LMS whose barcode contained a
          -- '1', along with their titles and pickup locations.
          AND :patronBarcode = ANY (
                string_to_array(translate(pi.local_barcode, '[] ', ''), ',')
              )
          AND pr.status_code IN (:activeStates)
        ORDER BY pr.date_updated DESC
    """, nativeQuery = true)
	Flux<PatronRequestSummaryProjection> findActiveRequestsForPatronByBarcode(String hostLmsCode, String patronBarcode, Collection<String> activeStates);

	default Flux<PatronRequestSummaryProjection> findActiveRequestsForPatronByBarcode(String hostLmsCode, String patronBarcode) {
		return findActiveRequestsForPatronByBarcode(hostLmsCode, patronBarcode, Status.ACTIVE_STATE_CODES);
	}

	// While the active requests are most useful for the discovery services, all time data also has its use for consortial admins
	@Query(value = """
        SELECT
            pr.id,
            CAST(pr.status_code AS VARCHAR) as status,
            CAST(pr.outcome_code AS VARCHAR) as outcome,
            CAST(pr.next_expected_status AS VARCHAR) as next_expected_status,
            pr.elapsed_time_in_current_status as time_in_state,
            pr.error_message,
            cr.title as title,
            pr.pickup_location_code,
            (SELECT l.name FROM location l WHERE l.code = pr.pickup_location_code LIMIT 1) as pickup_location_name,
            pr.active_workflow,
            pr.bib_cluster_id,
            pr.date_created,
            pr.date_updated
        FROM patron_request pr
        JOIN patron_identity pi ON pr.requesting_identity_id = pi.id
        LEFT JOIN cluster_record cr ON pr.bib_cluster_id = cr.id
        WHERE pr.patron_hostlms_code = :hostLmsCode
          -- local_barcode holds either a bare barcode or a list literal
          -- "[b1, b2, b3]" (see PatronIdentity.getParsedBarcodes). Match an ELEMENT
          -- EXACTLY. This was LIKE '%barcode%', which matched substrings: a barcode
          -- of '1' returned every patron at this Host LMS whose barcode contained a
          -- '1', along with their titles and pickup locations.
          AND :patronBarcode = ANY (
                string_to_array(translate(pi.local_barcode, '[] ', ''), ',')
              )
        ORDER BY pr.date_updated DESC
    """, nativeQuery = true)
	Flux<PatronRequestSummaryProjection> findAllRequestsForPatronByBarcode(String hostLmsCode, String patronBarcode);

	// Paged summary of a patron's own requests, keyed by the identity DCB verified from
	// a discovery service's patron assertion. Uses THE ownership predicate — the same
	// join as findOwnedRequest above, so "which requests are mine" and "may I cancel
	// this one" can never disagree. See that method for why it is patron_id and not
	// requesting_identity_id.
	//
	// Projected to PatronRequestSummaryProjection because the raw PatronRequest entity
	// has no serializable introspection (returning it 500s at render time).
	@Query(value = """
        SELECT DISTINCT
            pr.id,
            CAST(pr.status_code AS VARCHAR) as status,
            CAST(pr.outcome_code AS VARCHAR) as outcome,
            CAST(pr.next_expected_status AS VARCHAR) as next_expected_status,
            pr.elapsed_time_in_current_status as time_in_state,
            pr.error_message,
            cr.title as title,
            pr.pickup_location_code,
            (SELECT l.name FROM location l WHERE l.code = pr.pickup_location_code LIMIT 1) as pickup_location_name,
            pr.active_workflow,
            pr.bib_cluster_id,
            pr.date_created,
            pr.date_updated
        FROM patron_request pr
        JOIN patron_identity pi ON pr.patron_id = pi.patron_id
        JOIN host_lms h ON pi.host_lms_id = h.id
        LEFT JOIN cluster_record cr ON pr.bib_cluster_id = cr.id
        WHERE h.code = :patronSystem
          AND pi.local_id = :patronId
          AND pi.home_identity = true
        ORDER BY pr.date_updated DESC
    """, countQuery = """
        SELECT count(DISTINCT pr.id)
        FROM patron_request pr
        JOIN patron_identity pi ON pr.patron_id = pi.patron_id
        JOIN host_lms h ON pi.host_lms_id = h.id
        WHERE h.code = :patronSystem
          AND pi.local_id = :patronId
          AND pi.home_identity = true
    """, nativeQuery = true)
	Publisher<Page<PatronRequestSummaryProjection>> findSummariesForPatron(String patronSystem, String patronId, Pageable pageable);

	@Query(
		value = """
			SELECT cr.title, COUNT(pr.id) AS request_count 
			FROM patron_request pr
			JOIN cluster_record cr ON pr.bib_cluster_id = cr.id
			WHERE (:startDate IS NULL OR pr.date_created >= :startDate)
			AND (:endDate IS NULL OR pr.date_created <= :endDate)
			AND (:libraryCode IS NULL OR pr.patron_hostlms_code = ANY(string_to_array(:libraryCode, ',')))
			AND cr.is_deleted IS NULL
			GROUP BY cr.id, cr.title
		""",
		countQuery = """
			SELECT count(*) FROM (
			    SELECT 1
			    FROM patron_request pr
			    JOIN cluster_record cr ON pr.bib_cluster_id = cr.id
			    WHERE (:startDate IS NULL OR pr.date_created >= :startDate)
			    AND (:endDate IS NULL OR pr.date_created <= :endDate)
			    AND (:libraryCode IS NULL OR pr.patron_hostlms_code = ANY(string_to_array(:libraryCode, ',')))
			    AND cr.is_deleted IS NULL
			    GROUP BY cr.id, cr.title
			) AS count_query
		""",
		nativeQuery = true
	)
	Publisher<Page<RequestedTitleStat>> findMostRequestedTitles(@Nullable Instant startDate, @Nullable Instant endDate, @Nullable String libraryCode, Pageable pageable);

	@Query(
		value = """
			SELECT 
			    pi.local_barcode AS patron_barcode, 
			    pr.patron_hostlms_code AS library_code, 
			    COUNT(pr.id) AS active_request_count
			FROM patron_request pr
			JOIN patron_identity pi ON pr.patron_id = pi.patron_id
			WHERE pi.home_identity = TRUE
			AND pr.status_code IN (:activeStates)
			AND (:libraryCode IS NULL OR pr.patron_hostlms_code = ANY(string_to_array(:libraryCode, ',')))
			GROUP BY pi.local_barcode, pr.patron_hostlms_code
		""",
		countQuery = """
			SELECT count(*) FROM (
			    SELECT 1
			    FROM patron_request pr
			    JOIN patron_identity pi ON pr.patron_id = pi.patron_id
			    WHERE pi.home_identity = TRUE
					AND pr.status_code IN (:activeStates)
					AND (:libraryCode IS NULL OR pr.patron_hostlms_code = ANY(string_to_array(:libraryCode, ',')))
			    GROUP BY pi.local_barcode, pr.patron_hostlms_code
			) AS count_query
		""",
		nativeQuery = true
	)
	Publisher<Page<TopRequestorStat>> findTopRequestors(@Nullable String libraryCode, Collection<String> activeStates, Pageable pageable);

	default Publisher<Page<TopRequestorStat>> findTopRequestors(@Nullable String libraryCode, Pageable pageable) {
		return findTopRequestors(libraryCode, Status.ACTIVE_STATE_CODES, pageable);
	}


	// Percentile turnaround (seconds) from creation to first reaching :targetStatus.
	// p50/p95, NOT AVG: turnaround is heavily skewed and one stuck request poisons the mean.
	// COALESCE so an empty window yields (0,0) rather than a single (null,null) row.
	// :libraryCodes is comma-separated, or NULL for the whole consortium.
	@Query(
		value = """
			SELECT
				COALESCE(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (pra.audit_date - pr.date_created))), 0) AS p50_seconds,
				COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (pra.audit_date - pr.date_created))), 0) AS p95_seconds
			FROM patron_request pr
			JOIN patron_request_audit pra ON pr.id = pra.patron_request_id
			WHERE pra.to_status = :targetStatus
			AND pr.status_code != 'ERROR'
			AND active_workflow != 'RET-LOCAL'
			AND (:libraryCodes IS NULL OR pr.patron_hostlms_code = ANY(string_to_array(:libraryCodes, ',')))
			AND (:startDate IS NULL OR pr.date_created >= :startDate)
			AND (:endDate IS NULL OR pr.date_created <= :endDate)
		""",
		nativeQuery = true
	)
	Publisher<TurnaroundStat> findTurnaroundToStatus(
		@Nullable String libraryCodes,
		String targetStatus,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	// Flow time-series: transitions INTO each status per bucket, from the audit trail. Obeys the
	// window contract on TimeBucket - see that enum.
	//
	// Bounds are LocalDateTime, NOT Instant: audit_date is timestamp WITHOUT time zone holding
	// UTC, so binding UTC local date times keeps date_trunc and generate_series in the same
	// frame as the column. :bucket is enum-owned, never caller text.
	//
	// The bucket comes back AT TIME ZONE 'UTC' for the same reason in reverse. TimeSeriesPoint
	// holds an Instant, and reading one from a timestamp WITHOUT time zone takes its offset
	// from the JVM default - the deployment's zone rather than the column's - so every bucket
	// shifted on any host that is not UTC.
	@Query(
		value = """
			WITH bucketed AS (
				SELECT date_trunc(:bucket, pra.audit_date) AS bucket,
				       pra.to_status AS series,
				       COUNT(*) AS count
				FROM patron_request_audit pra
				JOIN patron_request pr ON pr.id = pra.patron_request_id
				WHERE pra.to_status IS NOT NULL
				  AND (:libraryCode IS NULL OR pr.patron_hostlms_code = ANY(string_to_array(:libraryCode, ',')))
				  AND pra.audit_date >= :seriesStart
				  AND pra.audit_date < :seriesEnd
				GROUP BY 1, 2
			),
			grid AS (
				SELECT g.bucket
				FROM generate_series(
					date_trunc(:bucket, :seriesStart),
					date_trunc(:bucket, :seriesEnd - interval '1 microsecond'),
					CAST('1 ' || :bucket AS interval)) AS g(bucket)
				LIMIT :maxBuckets
			)
			SELECT grid.bucket AT TIME ZONE 'UTC' AS bucket,
			       present.series AS series,
			       COALESCE(bucketed.count, 0) AS count
			FROM grid
			CROSS JOIN (SELECT DISTINCT series FROM bucketed) present
			LEFT JOIN bucketed
			       ON bucketed.bucket = grid.bucket AND bucketed.series = present.series
			ORDER BY 1, 2
		""",
		nativeQuery = true
	)
	Flux<TimeSeriesPoint> findStatusFlowTimeSeries(
		String bucket,
		@Nullable String libraryCode,
		LocalDateTime seriesStart,
		LocalDateTime seriesEnd,
		int maxBuckets
	);

	// Failure taxonomy: WHY requests failed, not just how many. Derived reason bucket.
	@Query(
		value = """
			SELECT
				CASE
					WHEN pr.previous_status_code = 'NO_ITEMS_SELECTABLE_AT_ANY_AGENCY' THEN 'NO_ITEMS_SELECTABLE'
					WHEN pr.status_code = 'CANCELLED' THEN 'CANCELLED'
					WHEN pr.status_code = 'ERROR' THEN COALESCE('ERROR_AT_' || pr.previous_status_code, 'ERROR')
					ELSE 'OTHER'
				END AS reason,
				COUNT(*) AS count
			FROM patron_request pr
			WHERE (pr.status_code IN ('ERROR', 'CANCELLED')
			       OR pr.previous_status_code = 'NO_ITEMS_SELECTABLE_AT_ANY_AGENCY')
			  AND (:libraryCode IS NULL OR pr.patron_hostlms_code = ANY(string_to_array(:libraryCode, ',')))
			  AND (:startDate IS NULL OR pr.date_created >= :startDate)
			  AND (:endDate IS NULL OR pr.date_created <= :endDate)
			GROUP BY reason
			ORDER BY count DESC
		""",
		nativeQuery = true
	)
	Flux<FailureReasonStat> findFailureTaxonomy(
		@Nullable String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	// Per-supplier reliability: fulfilled vs failed for each supplying library.
	@Query(
		value = """
			SELECT
				pr.local_item_hostlms_code AS supplier_code,
				COALESCE(SUM(CASE WHEN pr.status_code IN ('LOANED', 'COMPLETED', 'FINALISED')
				              AND (pr.previous_status_code != 'NO_ITEMS_SELECTABLE_AT_ANY_AGENCY' OR pr.previous_status_code IS NULL)
				             THEN 1 ELSE 0 END), 0) AS fulfilled_count,
				COALESCE(SUM(CASE WHEN pr.status_code IN ('ERROR', 'CANCELLED')
				              OR pr.previous_status_code = 'NO_ITEMS_SELECTABLE_AT_ANY_AGENCY'
				             THEN 1 ELSE 0 END), 0) AS failed_count
			FROM patron_request pr
			WHERE pr.local_item_hostlms_code IS NOT NULL
			  AND (pr.status_code IN ('LOANED', 'COMPLETED', 'FINALISED', 'ERROR', 'CANCELLED')
			       OR pr.previous_status_code = 'NO_ITEMS_SELECTABLE_AT_ANY_AGENCY')
			  AND (:libraryCode IS NULL OR pr.local_item_hostlms_code = ANY(string_to_array(:libraryCode, ',')))
			  AND (:startDate IS NULL OR pr.date_created >= :startDate)
			  AND (:endDate IS NULL OR pr.date_created <= :endDate)
			GROUP BY pr.local_item_hostlms_code
			ORDER BY fulfilled_count DESC
			LIMIT 50
		""",
		nativeQuery = true
	)
	Flux<SupplierReliabilityStat> findSupplierReliability(
		@Nullable String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	// Reciprocity / net flow: borrowed vs supplied volume per library across the network.
	@Query(
		value = """
			SELECT
				lib AS library_code,
				SUM(borrowed) AS borrowed_count,
				SUM(supplied) AS supplied_count
			FROM (
				SELECT patron_hostlms_code AS lib, COUNT(*) AS borrowed, 0 AS supplied
				FROM patron_request
				WHERE patron_hostlms_code IS NOT NULL AND status_code != 'ERROR'
				  AND (:startDate IS NULL OR date_created >= :startDate)
				  AND (:endDate IS NULL OR date_created <= :endDate)
				GROUP BY patron_hostlms_code
				UNION ALL
				SELECT local_item_hostlms_code AS lib, 0 AS borrowed, COUNT(*) AS supplied
				FROM patron_request
				WHERE local_item_hostlms_code IS NOT NULL AND status_code != 'ERROR'
				  AND (:startDate IS NULL OR date_created >= :startDate)
				  AND (:endDate IS NULL OR date_created <= :endDate)
				GROUP BY local_item_hostlms_code
			) t
			WHERE (:libraryCode IS NULL OR lib = ANY(string_to_array(:libraryCode, ',')))
			GROUP BY lib
			ORDER BY (SUM(supplied) - SUM(borrowed)) DESC
		""",
		nativeQuery = true
	)
	Flux<NetFlowStat> findNetFlow(
		@Nullable String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	// Time-in-status / bottleneck: median dwell (seconds) in each status. Dwell in a
	// from_status = this transition's time minus the previous audit's time (when the
	// request entered that status), via LAG over the per-request audit trail.
	@Query(
		value = """
			WITH dwell AS (
				SELECT
					pra.from_status AS status,
					EXTRACT(EPOCH FROM (pra.audit_date - LAG(pra.audit_date)
						OVER (PARTITION BY pra.patron_request_id ORDER BY pra.audit_date))) AS dwell_seconds
				FROM patron_request_audit pra
				JOIN patron_request pr ON pr.id = pra.patron_request_id
				WHERE (:libraryCode IS NULL OR pr.patron_hostlms_code = ANY(string_to_array(:libraryCode, ',')))
				  AND (:startDate IS NULL OR pra.audit_date >= :startDate)
				  AND (:endDate IS NULL OR pra.audit_date <= :endDate)
			)
			SELECT
				status,
				PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY dwell_seconds) AS median_dwell_seconds,
				COUNT(*) AS sample_count
			FROM dwell
			WHERE dwell_seconds IS NOT NULL AND status IS NOT NULL
			GROUP BY status
			ORDER BY median_dwell_seconds DESC
		""",
		nativeQuery = true
	)
	Flux<StatusDwellStat> findTimeInStatus(
		@Nullable String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	// Supplier-response SLA: median seconds from "request placed at supplying agency"
	// to "confirmed", per supplier. Uses the EARLIEST audit per status per request so
	// tracking-repeat audits do not double-count.
	@Query(
		value = """
			SELECT
				pr.local_item_hostlms_code AS supplier_code,
				PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (conf.first_date - placed.first_date))) AS median_response_seconds,
				COUNT(*) AS sample_count
			FROM patron_request pr
			JOIN (
				SELECT patron_request_id, MIN(audit_date) AS first_date
				FROM patron_request_audit
				WHERE to_status = 'REQUEST_PLACED_AT_SUPPLYING_AGENCY'
				GROUP BY patron_request_id
			) placed ON placed.patron_request_id = pr.id
			JOIN (
				SELECT patron_request_id, MIN(audit_date) AS first_date
				FROM patron_request_audit
				WHERE to_status = 'CONFIRMED'
				GROUP BY patron_request_id
			) conf ON conf.patron_request_id = pr.id
			WHERE pr.local_item_hostlms_code IS NOT NULL
			  AND conf.first_date >= placed.first_date
			  AND (:libraryCode IS NULL OR pr.local_item_hostlms_code = ANY(string_to_array(:libraryCode, ',')))
			  AND (:startDate IS NULL OR pr.date_created >= :startDate)
			  AND (:endDate IS NULL OR pr.date_created <= :endDate)
			GROUP BY pr.local_item_hostlms_code
			ORDER BY median_response_seconds DESC
			LIMIT 50
		""",
		nativeQuery = true
	)
	Flux<SupplierResponseStat> findSupplierResponseSla(
		@Nullable String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	// Demand heatmap: request counts bucketed by day-of-week x hour-of-day of creation.
	// DOW/hour are evaluated in the database session timezone.
	@Query(
		value = """
			SELECT
				EXTRACT(DOW FROM pr.date_created)::int AS day_of_week,
				EXTRACT(HOUR FROM pr.date_created)::int AS hour_of_day,
				COUNT(*) AS request_count
			FROM patron_request pr
			WHERE pr.status_code != 'ERROR'
			  AND (:libraryCode IS NULL OR pr.patron_hostlms_code = ANY(string_to_array(:libraryCode, ',')))
			  AND (:startDate IS NULL OR pr.date_created >= :startDate)
			  AND (:endDate IS NULL OR pr.date_created <= :endDate)
			GROUP BY 1, 2
			ORDER BY 1, 2
		""",
		nativeQuery = true
	)
	Flux<DemandHeatCell> findDemandHeatmap(
		@Nullable String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	// Checkout rate: how many placed requests ever reached LOANED (the shelf). Uses the
	// audit trail so requests that have since been returned still count as "reached".
	@Query(
		value = """
			SELECT
				COUNT(*) FILTER (WHERE loaned.patron_request_id IS NOT NULL) AS reached_count,
				COUNT(*) AS total_count
			FROM patron_request pr
			LEFT JOIN (
				SELECT DISTINCT patron_request_id FROM patron_request_audit WHERE to_status = 'LOANED'
			) loaned ON loaned.patron_request_id = pr.id
			WHERE (:libraryCode IS NULL OR pr.patron_hostlms_code = ANY(string_to_array(:libraryCode, ',')))
			  AND (:startDate IS NULL OR pr.date_created >= :startDate)
			  AND (:endDate IS NULL OR pr.date_created <= :endDate)
		""",
		nativeQuery = true
	)
	Publisher<CheckoutRateStat> findCheckoutRate(
		@Nullable String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	// Consortial collection analysis - demand by canonical format (derivedType).
	@Query(
		value = """
			SELECT
				COALESCE(br.canonical_metadata->>'derivedType', 'Unknown') AS format,
				COUNT(pr.id) AS request_count
			FROM patron_request pr
			JOIN cluster_record cr ON pr.bib_cluster_id = cr.id
			LEFT JOIN bib_record br ON cr.selected_bib = br.id
			WHERE pr.status_code != 'ERROR'
			  AND (:libraryCode IS NULL OR pr.patron_hostlms_code = ANY(string_to_array(:libraryCode, ',')))
			  AND (:startDate IS NULL OR pr.date_created >= :startDate)
			  AND (:endDate IS NULL OR pr.date_created <= :endDate)
			GROUP BY COALESCE(br.canonical_metadata->>'derivedType', 'Unknown')
			ORDER BY request_count DESC
		""",
		nativeQuery = true
	)
	Flux<FormatDemandStat> findDemandByFormat(
		@Nullable String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	// Headline collection figures: distinct titles (clusters) requested vs total volume.
	@Query(
		value = """
			SELECT
				COUNT(DISTINCT pr.bib_cluster_id) AS unique_titles_requested,
				COUNT(pr.id) AS total_requests
			FROM patron_request pr
			WHERE pr.status_code != 'ERROR'
			  AND pr.bib_cluster_id IS NOT NULL
			  AND (:libraryCode IS NULL OR pr.patron_hostlms_code = ANY(string_to_array(:libraryCode, ',')))
			  AND (:startDate IS NULL OR pr.date_created >= :startDate)
			  AND (:endDate IS NULL OR pr.date_created <= :endDate)
		""",
		nativeQuery = true
	)
	Publisher<CollectionSummaryStat> findCollectionSummary(
		@Nullable String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	// Peer benchmarking: each library's own borrower-side figures. The UI compares a
	// library against the consortium median computed from the full set of rows.
	@Query(
		value = """
			SELECT
				pr.patron_hostlms_code AS library_code,
				-- The name a librarian recognises. Correlated on the grouping column, so
				-- it runs once per library rather than once per request, over three small
				-- configuration tables. string_agg rather than picking one, because a
				-- shared Host LMS genuinely serves several libraries and naming only the
				-- alphabetically-first would be a quiet lie.
				(SELECT string_agg(DISTINCT l.full_name, ', ')
				   FROM library l
				   JOIN agency a ON a.code = l.agency_code
				   JOIN host_lms h ON h.id = a.host_lms_id
				  WHERE h.code = pr.patron_hostlms_code) AS library_name,
				COUNT(*) AS total_requests,
				COUNT(*) FILTER (WHERE loaned.patron_request_id IS NOT NULL) AS checkout_count,
				COUNT(*) FILTER (WHERE pr.status_code IN ('LOANED', 'COMPLETED', 'FINALISED')
					AND (pr.previous_status_code != 'NO_ITEMS_SELECTABLE_AT_ANY_AGENCY' OR pr.previous_status_code IS NULL)) AS success_count,
				COUNT(*) FILTER (WHERE pr.status_code IN ('ERROR', 'CANCELLED')
					OR pr.previous_status_code = 'NO_ITEMS_SELECTABLE_AT_ANY_AGENCY') AS failed_count
			FROM patron_request pr
			LEFT JOIN (
				SELECT DISTINCT patron_request_id FROM patron_request_audit WHERE to_status = 'LOANED'
			) loaned ON loaned.patron_request_id = pr.id
			WHERE pr.patron_hostlms_code IS NOT NULL
			  AND (:startDate IS NULL OR pr.date_created >= :startDate)
			  AND (:endDate IS NULL OR pr.date_created <= :endDate)
			GROUP BY pr.patron_hostlms_code
			ORDER BY total_requests DESC
		""",
		nativeQuery = true
	)
	Flux<PeerBenchmarkStat> findPeerBenchmarks(
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	// Collection analysis - demand by language. language is a JSON array (a record can be
	// multilingual), so each language is unnested; a bilingual title counts in both.
	@Query(
		value = """
			SELECT lang AS category, COUNT(pr.id) AS request_count
			FROM patron_request pr
			JOIN cluster_record cr ON pr.bib_cluster_id = cr.id
			LEFT JOIN bib_record br ON cr.selected_bib = br.id
			CROSS JOIN LATERAL jsonb_array_elements_text(
				COALESCE(br.canonical_metadata->'language', '[]'::jsonb)) AS lang
			WHERE pr.status_code != 'ERROR'
			  AND (:libraryCode IS NULL OR pr.patron_hostlms_code = ANY(string_to_array(:libraryCode, ',')))
			  AND (:startDate IS NULL OR pr.date_created >= :startDate)
			  AND (:endDate IS NULL OR pr.date_created <= :endDate)
			GROUP BY lang
			ORDER BY request_count DESC
			LIMIT 30
		""",
		nativeQuery = true
	)
	Flux<DimensionDemandStat> findDemandByLanguage(
		@Nullable String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	// Collection analysis - demand by subject (subjects is a JSON array of strings).
	@Query(
		value = """
			SELECT subj AS category, COUNT(pr.id) AS request_count
			FROM patron_request pr
			JOIN cluster_record cr ON pr.bib_cluster_id = cr.id
			LEFT JOIN bib_record br ON cr.selected_bib = br.id
			CROSS JOIN LATERAL jsonb_array_elements_text(
				COALESCE(br.canonical_metadata->'subjects', '[]'::jsonb)) AS subj
			WHERE pr.status_code != 'ERROR'
			  AND (:libraryCode IS NULL OR pr.patron_hostlms_code = ANY(string_to_array(:libraryCode, ',')))
			  AND (:startDate IS NULL OR pr.date_created >= :startDate)
			  AND (:endDate IS NULL OR pr.date_created <= :endDate)
			GROUP BY subj
			ORDER BY request_count DESC
			LIMIT 30
		""",
		nativeQuery = true
	)
	Flux<DimensionDemandStat> findDemandBySubject(
		@Nullable String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	// Collection analysis - demand by publication decade (year parsed from the free-text
	// dateOfPublication, bucketed to the decade).
	@Query(
		value = """
			SELECT (FLOOR(yr / 10) * 10)::int || 's' AS category, COUNT(*) AS request_count
			FROM (
				SELECT NULLIF(substring(br.canonical_metadata->>'dateOfPublication' FROM '[0-9]{4}'), '')::int AS yr
				FROM patron_request pr
				JOIN cluster_record cr ON pr.bib_cluster_id = cr.id
				LEFT JOIN bib_record br ON cr.selected_bib = br.id
				WHERE pr.status_code != 'ERROR'
				  AND (:libraryCode IS NULL OR pr.patron_hostlms_code = ANY(string_to_array(:libraryCode, ',')))
				  AND (:startDate IS NULL OR pr.date_created >= :startDate)
				  AND (:endDate IS NULL OR pr.date_created <= :endDate)
			) t
			WHERE yr IS NOT NULL AND yr BETWEEN 1000 AND 2100
			GROUP BY FLOOR(yr / 10) * 10
			ORDER BY FLOOR(yr / 10) * 10
		""",
		nativeQuery = true
	)
	Flux<DimensionDemandStat> findDemandByPublicationDecade(
		@Nullable String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	// Scope-aware lend/borrow totals in one pass: borrowed = requests where the scope is
	// the patron side, supplied = requests where the scope is the item side. Powers the
	// combined dashboard's totals without returning per-library net-flow rows.
	@Query(
		value = """
			SELECT
				COUNT(*) FILTER (WHERE :libraryCode IS NULL
					OR patron_hostlms_code = ANY(string_to_array(:libraryCode, ','))) AS borrowed_count,
				COUNT(*) FILTER (WHERE :libraryCode IS NULL
					OR local_item_hostlms_code = ANY(string_to_array(:libraryCode, ','))) AS supplied_count
			FROM patron_request
			WHERE status_code != 'ERROR'
			  AND (:startDate IS NULL OR date_created >= :startDate)
			  AND (:endDate IS NULL OR date_created <= :endDate)
		""",
		nativeQuery = true
	)
	Publisher<CollectionBalanceStat> findLendBorrowTotals(
		@Nullable String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	// Trading partners. Three queries: who we borrow from, who borrows from us, and both
	// together ranked on the total. Semantics and the gaps that remain: docs/insights.md 3.2a.
	//
	// All three share the same rules, and the two directional ones must stay mirror images:
	//
	//   - RET-LOCAL is excluded, because a local fulfilment is not a partnership, and so is
	//     ERROR, because a failed request is not traffic;
	//   - :libraryCode is a comma-separated SET, matched with = ANY(string_to_array(...)) like
	//     every other scoped query. Scalar equality here silently returned nothing for a
	//     caller who administers several libraries, while turnaround in the same response
	//     worked - an empty panel that looked like no activity;
	//   - a partner is by definition NOT one of the caller's own codes. Without that, traffic
	//     wholly inside a multi-library caller's own group is counted once under each end;
	//   - partner_name is resolved here, not joined in the client: the figures group by Host
	//     LMS and only the service knows which libraries sit on one. string_agg rather than
	//     picking the first, since a shared Host LMS genuinely serves several. Correlated on
	//     the grouping column, so it runs once per partner over three small config tables.

	@Query(
		value = """
			SELECT pr.local_item_hostlms_code as partner_code,
			       (SELECT string_agg(DISTINCT l.full_name, ', ')
			          FROM library l
			          JOIN agency a ON a.code = l.agency_code
			          JOIN host_lms h ON h.id = a.host_lms_id
			         WHERE h.code = pr.local_item_hostlms_code) AS partner_name,
			       COUNT(pr.id) as request_count
			FROM patron_request pr
			WHERE pr.patron_hostlms_code = ANY(string_to_array(:libraryCode, ','))
			  AND pr.local_item_hostlms_code IS NOT NULL
			  AND pr.local_item_hostlms_code <> ALL(string_to_array(:libraryCode, ','))
			  AND pr.status_code != 'ERROR'
			  AND pr.active_workflow != 'RET-LOCAL'
			  AND (:startDate IS NULL OR pr.date_created >= :startDate)
			  AND (:endDate IS NULL OR pr.date_created <= :endDate)
			GROUP BY pr.local_item_hostlms_code
			ORDER BY request_count DESC
			LIMIT 10
		""",
		nativeQuery = true
	)
	Flux<PartnerStat> findTopSuppliersForLibrary(
		String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	@Query(
		value = """
			SELECT pr.patron_hostlms_code as partner_code,
			       (SELECT string_agg(DISTINCT l.full_name, ', ')
			          FROM library l
			          JOIN agency a ON a.code = l.agency_code
			          JOIN host_lms h ON h.id = a.host_lms_id
			         WHERE h.code = pr.patron_hostlms_code) AS partner_name,
			       COUNT(pr.id) as request_count
			FROM patron_request pr
			WHERE pr.local_item_hostlms_code = ANY(string_to_array(:libraryCode, ','))
			  AND pr.patron_hostlms_code IS NOT NULL
			  AND pr.patron_hostlms_code <> ALL(string_to_array(:libraryCode, ','))
			  AND pr.status_code != 'ERROR'
			  AND pr.active_workflow != 'RET-LOCAL'
			  AND (:startDate IS NULL OR pr.date_created >= :startDate)
			  AND (:endDate IS NULL OR pr.date_created <= :endDate)
			GROUP BY pr.patron_hostlms_code
			ORDER BY request_count DESC
			LIMIT 10
		""",
		nativeQuery = true
	)
	Flux<PartnerStat> findTopBorrowersFromLibrary(
		String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	// Both directions in one ranking, which neither list above can be derived into: a partner
	// sixth in each can out-total one ranked third in one, and appears in neither. The
	// breakdown travels with the total because a partner we borrow from constantly and never
	// supply is a different relationship from an even one, and the total alone cannot say.
	//
	// UNION ALL, not two aggregates joined: the borrow and supply arms are disjoint by
	// construction (a request has one borrower and one supplier), so there is nothing to
	// deduplicate and a FULL OUTER JOIN would only add a way to lose a partner present on one
	// side. Each arm is served by its own composite index - pr_stats_borrower_idx and
	// pr_stats_supplier_idx.
	// Paged rather than a fixed top N. Neither ORDER BY nor LIMIT appears here: Micronaut Data
	// appends both from the Pageable, and a literal one would collide with what it appends.
	// InsightsController supplies total_count DESC when the caller names no sort, so the
	// default is still "top partners" rather than whatever order the aggregate happens to emit.
	@Query(
		value = """
			WITH traffic AS (
			    SELECT pr.local_item_hostlms_code AS partner_code, 1 AS borrowed, 0 AS supplied
			    FROM patron_request pr
			    WHERE pr.patron_hostlms_code = ANY(string_to_array(:libraryCode, ','))
			      AND pr.local_item_hostlms_code IS NOT NULL
			      AND pr.local_item_hostlms_code <> ALL(string_to_array(:libraryCode, ','))
			      AND pr.status_code != 'ERROR'
			      AND pr.active_workflow != 'RET-LOCAL'
			      AND (:startDate IS NULL OR pr.date_created >= :startDate)
			      AND (:endDate IS NULL OR pr.date_created <= :endDate)
			    UNION ALL
			    SELECT pr.patron_hostlms_code AS partner_code, 0 AS borrowed, 1 AS supplied
			    FROM patron_request pr
			    WHERE pr.local_item_hostlms_code = ANY(string_to_array(:libraryCode, ','))
			      AND pr.patron_hostlms_code IS NOT NULL
			      AND pr.patron_hostlms_code <> ALL(string_to_array(:libraryCode, ','))
			      AND pr.status_code != 'ERROR'
			      AND pr.active_workflow != 'RET-LOCAL'
			      AND (:startDate IS NULL OR pr.date_created >= :startDate)
			      AND (:endDate IS NULL OR pr.date_created <= :endDate)
			)
			SELECT t.partner_code,
			       (SELECT string_agg(DISTINCT l.full_name, ', ')
			          FROM library l
			          JOIN agency a ON a.code = l.agency_code
			          JOIN host_lms h ON h.id = a.host_lms_id
			         WHERE h.code = t.partner_code) AS partner_name,
			       SUM(t.borrowed) AS borrowed_from_count,
			       SUM(t.supplied) AS supplied_to_count,
			       COUNT(*) AS total_count
			FROM traffic t
			GROUP BY t.partner_code
		""",
		countQuery = """
			SELECT count(*) FROM (
			    SELECT t.partner_code
			    FROM (
			        SELECT pr.local_item_hostlms_code AS partner_code
			        FROM patron_request pr
			        WHERE pr.patron_hostlms_code = ANY(string_to_array(:libraryCode, ','))
			          AND pr.local_item_hostlms_code IS NOT NULL
			          AND pr.local_item_hostlms_code <> ALL(string_to_array(:libraryCode, ','))
			          AND pr.status_code != 'ERROR'
			          AND pr.active_workflow != 'RET-LOCAL'
			          AND (:startDate IS NULL OR pr.date_created >= :startDate)
			          AND (:endDate IS NULL OR pr.date_created <= :endDate)
			        UNION ALL
			        SELECT pr.patron_hostlms_code AS partner_code
			        FROM patron_request pr
			        WHERE pr.local_item_hostlms_code = ANY(string_to_array(:libraryCode, ','))
			          AND pr.patron_hostlms_code IS NOT NULL
			          AND pr.patron_hostlms_code <> ALL(string_to_array(:libraryCode, ','))
			          AND pr.status_code != 'ERROR'
			          AND pr.active_workflow != 'RET-LOCAL'
			          AND (:startDate IS NULL OR pr.date_created >= :startDate)
			          AND (:endDate IS NULL OR pr.date_created <= :endDate)
			    ) t
			    GROUP BY t.partner_code
			) AS count_query
		""",
		nativeQuery = true
	)
	Publisher<Page<TradingPartnerStat>> findTopTradingPartners(
		String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate,
		Pageable pageable
	);

	// "Unmet local demand": clusters THIS library's own patrons requested, but to which
	// the library contributes no bib record (i.e. it does not hold the title). Self-demand.
	// TopClusterStat only carries (clusterId, title, requestCount), so we select nothing more -
	// the previous author/ISBN projection was computed per row and silently discarded.
	@Query(
		value = """
            SELECT
                cr.id AS cluster_id,
                cr.title AS title,
                COUNT(pr.id) AS request_count
            FROM patron_request pr
            JOIN cluster_record cr ON pr.bib_cluster_id = cr.id
            WHERE pr.patron_hostlms_code = :libraryCode
              AND pr.status_code != 'ERROR'
              AND (:startDate IS NULL OR pr.date_created >= :startDate)
              AND (:endDate IS NULL OR pr.date_created <= :endDate)
              AND NOT EXISTS (
                  SELECT 1
                  FROM bib_record my_br
                  JOIN host_lms hl ON my_br.source_system_id = hl.id
                  WHERE my_br.contributes_to = cr.id
                    AND hl.code = :libraryCode
              )
            GROUP BY cr.id
            ORDER BY request_count DESC
            LIMIT 50
        """,
		nativeQuery = true
	)
	Flux<TopClusterStat> findUnmetLocalDemandForLibrary(
		String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	@Query(
		value = """
            SELECT 
                cr.id AS cluster_id, 
                cr.title AS title, 
                CAST(br.canonical_metadata->>'author' AS VARCHAR) AS author,
                (SELECT bi.value FROM bib_identifier bi WHERE bi.owner_id = br.id AND bi.namespace = 'ISBN' LIMIT 1) AS isbn,
                br.source_record_id AS local_bib_id,
                COUNT(pr.id) AS supply_count
            FROM patron_request pr
            JOIN cluster_record cr ON pr.bib_cluster_id = cr.id
            JOIN host_lms hl ON hl.code = :libraryCode
            JOIN bib_record br ON br.contributes_to = cr.id AND br.source_system_id = hl.id
            WHERE pr.local_item_hostlms_code = :libraryCode
              AND pr.status_code != 'ERROR'
              AND (:startDate IS NULL OR pr.date_created >= :startDate)
              AND (:endDate IS NULL OR pr.date_created <= :endDate)
            GROUP BY cr.id, br.id
            ORDER BY supply_count DESC
            LIMIT 50
        """,
		nativeQuery = true
	)
	Flux<ConsortialLifelineStat> findConsortialLifelineForLibrary(String libraryCode, Instant startDate, Instant endDate);

	@Query(
		value = """
            SELECT 
                pi.canonical_ptype AS patron_group, 
                COUNT(pr.id) AS request_count
            FROM patron_request pr
            JOIN patron_identity pi ON pr.requesting_identity_id = pi.id
            WHERE pr.status_code != 'ERROR'
              AND pi.canonical_ptype IS NOT NULL
              AND (:libraryCode IS NULL OR pr.patron_hostlms_code = ANY(string_to_array(:libraryCode, ',')))
              AND (:startDate IS NULL OR pr.date_created >= :startDate)
              AND (:endDate IS NULL OR pr.date_created <= :endDate)
            GROUP BY pi.canonical_ptype
            ORDER BY request_count DESC
        """,
		nativeQuery = true
	)
	Flux<PatronGroupDemandStat> findDemandByPatronGroup(
		@Nullable String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	@Query(
		value = """
            SELECT
                pr.pickup_location_code AS pickup_location_code,
                l.name AS pickup_location_name,
                COUNT(pr.id) AS request_count
            FROM patron_request pr
            LEFT JOIN location l ON pr.pickup_location_code = l.code
            WHERE pr.status_code != 'ERROR'
              AND pr.pickup_location_code IS NOT NULL
              AND (:libraryCode IS NULL OR pr.patron_hostlms_code = ANY(string_to_array(:libraryCode, ',')))
              AND (:startDate IS NULL OR pr.date_created >= :startDate)
              AND (:endDate IS NULL OR pr.date_created <= :endDate)
            GROUP BY pr.pickup_location_code, l.name
            ORDER BY request_count DESC
            LIMIT 20
        """,
		nativeQuery = true
	)
	Flux<PickupLocationDemandStat> findDemandByPickupLocation(
		@Nullable String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	@Query(
		value = """
            SELECT COUNT(pr.id)
            FROM patron_request pr
            WHERE pr.status_code != 'ERROR'
              AND pr.pickup_location_code = :pickupLocationCode
              AND (:libraryCode IS NULL OR pr.patron_hostlms_code = ANY(string_to_array(:libraryCode, ',')))
              AND (:startDate IS NULL OR pr.date_created >= :startDate)
              AND (:endDate IS NULL OR pr.date_created <= :endDate)
        """,
		nativeQuery = true
	)
	Publisher<Long> countDemandForPickupLocation(
		@Nullable String libraryCode,
		String pickupLocationCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	@Query(
		value = """
            SELECT 
                cr.id AS cluster_id, 
                cr.title AS title, 
                CAST(br.canonical_metadata->>'author' AS VARCHAR) AS author,
                br.source_record_id AS local_bib_id,
                br.date_created AS date_added,
                COUNT(pr.id) AS supply_count
            FROM bib_record br
            JOIN host_lms hl ON br.source_system_id = hl.id
            JOIN cluster_record cr ON br.contributes_to = cr.id
            JOIN patron_request pr ON pr.bib_cluster_id = cr.id AND pr.local_item_hostlms_code = hl.code
            WHERE hl.code = :libraryCode
              AND br.date_created >= :acquiredSince
              AND pr.status_code != 'ERROR'
              AND (:startDate IS NULL OR pr.date_created >= :startDate)
              AND (:endDate IS NULL OR pr.date_created <= :endDate)
            GROUP BY cr.id, br.id
            ORDER BY supply_count DESC
            LIMIT 50
        """,
		nativeQuery = true
	)
	Flux<NewAcquisitionPerformanceStat> findNewAcquisitionsPerformance(
		String libraryCode,
		Instant acquiredSince,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);


	@Query(
		value = """
            SELECT COUNT(id) 
            FROM patron_request 
            WHERE patron_hostlms_code = :libraryCode 
              AND status_code != 'ERROR'
              AND (:startDate IS NULL OR date_created >= :startDate)
              AND (:endDate IS NULL OR date_created <= :endDate)
        """,
		nativeQuery = true
	)
	Publisher<Long> countBorrowedForLibrary(String libraryCode, @Nullable Instant startDate, @Nullable Instant endDate);

	@Query(
		value = """
            SELECT COUNT(id) 
            FROM patron_request 
            WHERE local_item_hostlms_code = :libraryCode 
              AND status_code != 'ERROR'
              AND (:startDate IS NULL OR date_created >= :startDate)
              AND (:endDate IS NULL OR date_created <= :endDate)
        """,
		nativeQuery = true
	)
	Publisher<Long> countSuppliedForLibrary(String libraryCode, @Nullable Instant startDate, @Nullable Instant endDate);

	@Query(
		value = """
            SELECT 
                cr.id AS cluster_id, 
                COALESCE(cr.title, CAST(br.canonical_metadata->>'title' AS VARCHAR), 'Unknown Title') AS title,
                CAST(br.canonical_metadata->>'author' AS VARCHAR) AS author,
                COUNT(pr.id) AS request_count
            FROM patron_request pr
            JOIN cluster_record cr ON pr.bib_cluster_id = cr.id
            LEFT JOIN bib_record br ON cr.selected_bib = br.id
            WHERE pr.previous_status_code = 'NO_ITEMS_SELECTABLE_AT_ANY_AGENCY'
              AND (pr.is_manually_selected_item IS NULL OR pr.is_manually_selected_item = false)
              AND (:libraryCode IS NULL OR pr.patron_hostlms_code = ANY(string_to_array(:libraryCode, ',')))
              AND (:startDate IS NULL OR pr.date_created >= :startDate)
              AND (:endDate IS NULL OR pr.date_created <= :endDate)
            GROUP BY cr.id, br.id
            ORDER BY request_count DESC
            LIMIT 50
        """,
		nativeQuery = true
	)
	Flux<UnfillableDemandStat> findUnfillableDemand(
		@Nullable String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	@Query(
		value = """
            SELECT COUNT(id)
            FROM patron_request
            WHERE resolution_count > 1
              AND error_message IS NULL
              AND status_code IN ('LOANED', 'FINALISED')
              AND (:libraryCode IS NULL OR patron_hostlms_code = ANY(string_to_array(:libraryCode, ',')))
              AND (:startDate IS NULL OR date_created >= :startDate)
              AND (:endDate IS NULL OR date_created <= :endDate)
        """,
		nativeQuery = true
	)
	Publisher<Long> countSavedByReresolution(
		@Nullable String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);
	@Query(
		value = """
            SELECT 
                COALESCE(SUM(CASE WHEN pr.status_code IN ('LOANED', 'COMPLETED', 'FINALISED') 
                              AND (pr.previous_status_code != 'NO_ITEMS_SELECTABLE_AT_ANY_AGENCY' OR pr.previous_status_code IS NULL) 
                             THEN 1 ELSE 0 END), 0) AS successful_count,
                COALESCE(SUM(CASE WHEN pr.status_code IN ('ERROR', 'CANCELLED') 
                              OR pr.previous_status_code = 'NO_ITEMS_SELECTABLE_AT_ANY_AGENCY' 
                             THEN 1 ELSE 0 END), 0) AS failed_count
            FROM patron_request pr
            WHERE (pr.status_code IN ('LOANED', 'COMPLETED', 'FINALISED', 'ERROR', 'CANCELLED') 
                   OR pr.previous_status_code = 'NO_ITEMS_SELECTABLE_AT_ANY_AGENCY')
              AND (:libraryCode IS NULL OR pr.patron_hostlms_code = ANY(string_to_array(:libraryCode, ',')))
              AND (:startDate IS NULL OR pr.date_created >= :startDate)
              AND (:endDate IS NULL OR pr.date_created <= :endDate)
        """,
		nativeQuery = true
	)
	Publisher<FulfillmentStat> getBorrowerFulfillmentStats(
		@Nullable String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	@Query(
		value = """
            SELECT 
                COALESCE(SUM(CASE WHEN pr.status_code IN ('LOANED', 'COMPLETED', 'FINALISED') 
                              AND (pr.previous_status_code != 'NO_ITEMS_SELECTABLE_AT_ANY_AGENCY' OR pr.previous_status_code IS NULL) 
                             THEN 1 ELSE 0 END), 0) AS successful_count,
                COALESCE(SUM(CASE WHEN pr.status_code IN ('ERROR', 'CANCELLED') 
                              OR pr.previous_status_code = 'NO_ITEMS_SELECTABLE_AT_ANY_AGENCY' 
                             THEN 1 ELSE 0 END), 0) AS failed_count
            FROM patron_request pr
            WHERE (pr.status_code IN ('LOANED', 'COMPLETED', 'FINALISED', 'ERROR', 'CANCELLED') 
                   OR pr.previous_status_code = 'NO_ITEMS_SELECTABLE_AT_ANY_AGENCY')
              AND (:libraryCode IS NULL OR pr.local_item_hostlms_code = ANY(string_to_array(:libraryCode, ',')))
              AND (:startDate IS NULL OR pr.date_created >= :startDate)
              AND (:endDate IS NULL OR pr.date_created <= :endDate)
        """,
		nativeQuery = true
	)
	Publisher<FulfillmentStat> getSupplierFulfillmentStats(
		@Nullable String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);

	@Query(
		value = """
            SELECT 
                cr.id AS cluster_id, 
                cr.title AS title, 
                CAST(br.canonical_metadata->>'author' AS VARCHAR) AS author,
                br.source_record_id AS local_bib_id,
                COUNT(pr.id) AS supply_count
            FROM patron_request pr
            JOIN cluster_record cr ON pr.bib_cluster_id = cr.id
            JOIN host_lms hl ON hl.code = :libraryCode
            JOIN bib_record br ON br.contributes_to = cr.id AND br.source_system_id = hl.id
            WHERE pr.local_item_hostlms_code = :libraryCode
              AND pr.status_code != 'ERROR'
              AND (:startDate IS NULL OR pr.date_created >= :startDate)
              AND (:endDate IS NULL OR pr.date_created <= :endDate)
              AND (
                  SELECT COUNT(DISTINCT inner_br.source_system_id)
                  FROM bib_record inner_br
                  WHERE inner_br.contributes_to = cr.id
              ) = 1
            GROUP BY cr.id, br.id
            ORDER BY supply_count DESC
            LIMIT 50
        """,
		nativeQuery = true
	)
	Flux<RareGem> findUniqueContributions(
		String libraryCode,
		@Nullable Instant startDate,
		@Nullable Instant endDate
	);
}
