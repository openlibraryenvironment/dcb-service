package org.olf.reshare.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import org.olf.reshare.dcb.core.model.DataHostLms;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.UUID;
public interface HostLmsRepository {

	@NonNull
	@SingleResult
	Publisher<? extends DataHostLms> save(@Valid @NotNull @NonNull DataHostLms hostLms);

        @NonNull
        @SingleResult
        Publisher<? extends DataHostLms> persist(@Valid @NotNull @NonNull DataHostLms hostLms);

	@NonNull
	@SingleResult
	Publisher<? extends DataHostLms> update(@Valid @NotNull @NonNull DataHostLms hostLms);

	@NonNull
	@SingleResult
	Publisher<DataHostLms> findById(@NonNull UUID id);

	@NonNull
	Publisher<DataHostLms> findAll();

        Publisher<Void> delete(UUID id);

}
