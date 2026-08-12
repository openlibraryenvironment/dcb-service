package org.olf.dcb.storage.postgres;

import java.time.Instant;
import java.util.UUID;

import jakarta.transaction.Transactional;

import org.olf.dcb.core.model.Alarm;
import org.olf.dcb.storage.AlarmRepository;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;
import io.micronaut.data.repository.jpa.reactive.ReactorJpaSpecificationExecutor;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import org.reactivestreams.Publisher;
import jakarta.validation.constraints.NotNull;

@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresAlarmRepository extends ReactiveStreamsPageableRepository<Alarm, UUID>, ReactorJpaSpecificationExecutor<Alarm>, AlarmRepository {

	@NonNull
	@SingleResult
	Publisher<Alarm> findById(@NotNull UUID id);

	/**
	 * {@inheritDoc}
	 * <p>
	 * One statement, so concurrent reporters cannot lose each other's values the way
	 * a read-modify-write in Java does.
	 * <p>
	 * The set is capped at {@code maxValues} taken in sort order: this is a digest
	 * telling an operator what to go and fix, not a log, and an uncapped jsonb array
	 * fed from a per-item path is an unbounded collection in the database.
	 * <p>
	 * {@code xmax = 0} is true only for a row this statement inserted, which is how
	 * the caller distinguishes first sighting from a repeat without a second query.
	 * <p>
	 * CAST(... AS jsonb) rather than the ::jsonb shorthand throughout, because the
	 * named parameter parser reads "::jsonb" as a parameter reference.
	 */
	@Override
	@NonNull
	@SingleResult
	@Query(value = """
		INSERT INTO alarm (id, code, created, last_seen, repeat_count, expires, alarm_details)
		VALUES (:id, :code, :now, :now, 0, :expires,
			jsonb_build_object(CAST(:detailKey AS text), jsonb_build_array(CAST(:value AS text))))
		ON CONFLICT (id) DO UPDATE SET
			last_seen = :now,
			expires = :expires,
			repeat_count = COALESCE(alarm.repeat_count, 0) + 1,
			alarm_details = jsonb_set(
				COALESCE(alarm.alarm_details, CAST('{}' AS jsonb)),
				ARRAY[CAST(:detailKey AS text)],
				COALESCE((
					SELECT jsonb_agg(capped.v ORDER BY capped.v)
					FROM (
						SELECT merged.v FROM (
							SELECT jsonb_array_elements_text(
								COALESCE(alarm.alarm_details -> CAST(:detailKey AS text), CAST('[]' AS jsonb))) AS v
							UNION
							SELECT CAST(:value AS text)
						) merged
						ORDER BY merged.v
						LIMIT :maxValues
					) capped
				), CAST('[]' AS jsonb))
			)
		RETURNING (xmax = 0)
		""", nativeQuery = true)
	Publisher<Boolean> accumulateDetailValue(@NonNull UUID id, String code,
		@NonNull Instant now, Instant expires, @NonNull String detailKey,
		@NonNull String value, int maxValues);

}
