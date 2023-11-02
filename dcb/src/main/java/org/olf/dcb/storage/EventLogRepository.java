package org.olf.dcb.storage;

import java.util.UUID;

import org.olf.dcb.core.model.Event;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface EventLogRepository {
	@NonNull
	@SingleResult
	Publisher<? extends Event> save(@Valid @NotNull @NonNull Event event);

	@NonNull
	Publisher<Event> queryAll();

	@NonNull
	Publisher<Void> delete(UUID id);
}
