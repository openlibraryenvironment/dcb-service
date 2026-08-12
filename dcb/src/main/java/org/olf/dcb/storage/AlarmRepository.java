package org.olf.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.olf.dcb.core.model.Alarm;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;
import java.time.Instant;

public interface AlarmRepository {

	@NonNull
	@SingleResult
	Publisher<? extends Alarm> save(@Valid @NotNull @NonNull Alarm alarm);

	@NonNull
	@SingleResult
	Publisher<? extends Alarm> persist(@Valid @NotNull @NonNull Alarm alarm);

	@NonNull
	@SingleResult
	Publisher<? extends Alarm> update(@Valid @NotNull @NonNull Alarm alarm);

	@NonNull
	@SingleResult
	Publisher<? extends Alarm> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<? extends Alarm> findByCode(@NonNull String code);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<Alarm>> queryAll(Pageable page);

	@NonNull
	Publisher<Alarm> queryAll();

	Publisher<Void> delete(UUID id);

	@SingleResult
	@NonNull
	default Publisher<Alarm> saveOrUpdate(@Valid @NotNull Alarm alarm) {
		return Mono.from(this.existsById(alarm.getId()))
			.flatMap( update -> Mono.from( update ? this.update(alarm) : this.save(alarm)) )
			;
	}

	/**
	 * Upsert an alarm, adding one value to a set held under {@code detailKey} in its
	 * details, and report whether the alarm was created by this call.
	 * <p>
	 * A set of related occurrences - the unmapped location codes on one Host LMS -
	 * is reported one occurrence at a time from a concurrent per-item path. Reading
	 * the alarm, adding to the set in Java and writing it back loses most of the
	 * values under that concurrency: every writer computes from a stale read and the
	 * last write wins. The merge therefore happens in the database, in one statement.
	 *
	 * @return true when this call inserted the alarm, false when it updated an
	 * existing one - so the caller can notify only on first sighting
	 */
	@NonNull
	@SingleResult
	Publisher<Boolean> accumulateDetailValue(@NonNull UUID id, String code,
		@NonNull Instant now, Instant expires, @NonNull String detailKey,
		@NonNull String value, int maxValues);

	@NonNull
	Publisher<Alarm> findByExpiresBefore(@NonNull Instant now);

	Publisher<Void> deleteByCode(String code);

}
