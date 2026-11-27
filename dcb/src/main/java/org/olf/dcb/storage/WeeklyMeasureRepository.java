package org.olf.dcb.storage;

import java.time.Instant;
import java.time.LocalDate;

import org.olf.dcb.core.model.PatronRequest;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.GenericRepository;
import jakarta.transaction.Transactional;

/**
 * Stored weekly figures (library_weekly_measure) and the two measures they hold, worked out from DCB's own tables.
 * Every statement is for one library; the borrowing library is the patron's resolved agency.
 */
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface WeeklyMeasureRepository extends GenericRepository<PatronRequest, java.util.UUID> {
	@NonNull
	@SingleResult
	@Query(value = """
		INSERT INTO library_weekly_measure (agency_code, measure, week_start, value, samples, recorded_at)
		VALUES (:agencyCode, :measure, :weekStart, :value, :samples, :now)
		ON CONFLICT (agency_code, measure, week_start) DO UPDATE SET
			value = EXCLUDED.value, samples = EXCLUDED.samples, recorded_at = EXCLUDED.recorded_at
		""", nativeQuery = true)
	Publisher<Long> record(@NonNull String agencyCode, @NonNull String measure, @NonNull LocalDate weekStart,
		double value, int samples, @NonNull Instant now);

	@Introspected
	record Stored(LocalDate week_start, double value, int samples) {
	}

	@NonNull
	@Query(value = """
		SELECT week_start, value, samples FROM library_weekly_measure
		WHERE agency_code = :agencyCode AND measure = :measure AND week_start >= :from AND week_start < :before
		ORDER BY week_start
		""", nativeQuery = true)
	Publisher<Stored> trailing(@NonNull String agencyCode, @NonNull String measure, @NonNull LocalDate from,
		@NonNull LocalDate before);

	@NonNull
	@SingleResult
	@Query(value = "DELETE FROM library_weekly_measure WHERE week_start < :before", nativeQuery = true)
	Publisher<Long> deleteBefore(@NonNull LocalDate before);

	/** Median hours from placing to ready for pickup, for the library's patrons' requests ready in the window. */
	@NonNull
	@SingleResult
	@Query(value = """
		SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (a.audit_date - pr.date_created)) / 3600.0) AS value,
		       count(*) AS samples
		FROM patron_request_audit a
		  JOIN patron_request pr ON pr.id = a.patron_request_id
		  JOIN patron_identity pi ON pi.id = pr.requesting_identity_id
		  JOIN agency ag ON ag.id = pi.resolved_agency_id
		WHERE ag.code = :agencyCode AND a.to_status = 'READY_FOR_PICKUP'
		  AND a.audit_date >= :from AND a.audit_date < :to
		""", nativeQuery = true)
	Publisher<Figure> medianTurnaroundHours(@NonNull String agencyCode, @NonNull Instant from, @NonNull Instant to);

	/** The library's patrons' items waiting on a hold shelf now. */
	@NonNull
	@SingleResult
	@Query(value = """
		SELECT CAST(count(*) AS double precision) AS value, count(*) AS samples
		FROM patron_request pr
		  JOIN patron_identity pi ON pi.id = pr.requesting_identity_id
		  JOIN agency ag ON ag.id = pi.resolved_agency_id
		WHERE ag.code = :agencyCode AND pr.status_code = 'READY_FOR_PICKUP'
		""", nativeQuery = true)
	Publisher<Figure> awaitingPickup(@NonNull String agencyCode);

	@Introspected
	record Figure(@Nullable Double value, long samples) {
	}
}
