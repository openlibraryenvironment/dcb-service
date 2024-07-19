package org.olf.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.olf.dcb.core.model.DataChangeLog;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface DataChangeLogRepository {
	@NonNull
	@SingleResult
	Publisher<? extends DataChangeLog> save(@Valid @NotNull @NonNull DataChangeLog DataChangeLog);

	@NonNull
	@SingleResult
	Publisher<? extends DataChangeLog> persist(@Valid @NotNull @NonNull DataChangeLog DataChangeLog);

	@NonNull
	@SingleResult
	Publisher<? extends DataChangeLog> update(@Valid @NotNull @NonNull DataChangeLog DataChangeLog);

	@NonNull
	@SingleResult
	Publisher<? extends DataChangeLog> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<? extends DataChangeLog> findByEntityId(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<? extends DataChangeLog> findOneByEntityId(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Page<DataChangeLog>> queryAll(Pageable page);

	@NonNull
	Publisher<DataChangeLog> queryAll();

	Publisher<Void> delete(UUID id);

	@SingleResult
	@NonNull
	default Publisher<DataChangeLog> saveOrUpdate(@Valid @NotNull DataChangeLog DataChangeLog) {
		return Mono.from(this.existsById(DataChangeLog.getId()))
			.flatMap( update -> Mono.from( update ? this.update(DataChangeLog) : this.save(DataChangeLog)) )
			;
	}

}
