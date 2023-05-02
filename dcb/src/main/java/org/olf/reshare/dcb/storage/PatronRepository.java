package org.olf.reshare.dcb.storage;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import org.olf.reshare.dcb.core.model.Patron;
import org.reactivestreams.Publisher;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public interface PatronRepository {
	@NonNull
	@SingleResult
	Publisher<? extends Patron> save(@Valid @NotNull @NonNull Patron patron);

	@NonNull
	@SingleResult
	Publisher<Patron> findById(@NotNull UUID id);
}
