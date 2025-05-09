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

	@NonNull
	Publisher<Alarm> findByExpiresBefore(@NonNull Instant now);
}
