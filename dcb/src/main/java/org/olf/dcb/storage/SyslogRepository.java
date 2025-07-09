package org.olf.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.olf.dcb.core.model.Syslog;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.UUID;
import java.time.Instant;

public interface SyslogRepository {

	@NonNull
	@SingleResult
	Publisher<? extends Syslog> save(@Valid @NotNull @NonNull Syslog syslog);

	@NonNull
	@SingleResult
	Publisher<? extends Syslog> persist(@Valid @NotNull @NonNull Syslog syslog);

	@NonNull
	@SingleResult
	Publisher<? extends Syslog> update(@Valid @NotNull @NonNull Syslog syslog);

	@NonNull
	@SingleResult
	Publisher<? extends Syslog> findById(@NonNull Long id);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull Long id);

	@NonNull
	@SingleResult
	Publisher<Page<Syslog>> queryAll(Pageable page);

	@NonNull
	Publisher<Syslog> queryAll();

	Publisher<Void> delete(Long id);
}
