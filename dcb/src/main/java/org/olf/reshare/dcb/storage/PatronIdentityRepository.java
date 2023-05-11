package org.olf.reshare.dcb.storage;

import java.util.UUID;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.core.model.Patron;
import org.olf.reshare.dcb.core.model.PatronIdentity;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;

public interface PatronIdentityRepository {
	@NonNull
	@SingleResult
	Publisher<? extends PatronIdentity> save(@Valid @NotNull @NonNull PatronIdentity patronIdentity);

	@NonNull
	@SingleResult
	Publisher<PatronIdentity> findOneByLocalIdAndHostLmsAndHomeIdentity(
		@NotNull String localId, @NotNull DataHostLms hostLmsId,
		@NotNull Boolean homeIdentity);

	@NonNull
	Publisher<PatronIdentity> findAllByPatron(Patron patron);

	@NonNull
	Publisher<PatronIdentity> findAll();

	@NonNull
	Publisher<Void> delete(UUID id);
}
