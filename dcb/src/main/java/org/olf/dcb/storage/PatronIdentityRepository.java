package org.olf.dcb.storage;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import org.olf.dcb.core.model.*;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Join;
import reactor.core.publisher.Mono;

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
	Publisher<PatronIdentity> queryAll();

	@NonNull
	Publisher<Void> delete(UUID id);

        @Query(value = "SELECT pi.*, h.* from patron_identity, host_lms h pi where pi.patron_id = :patronId and pi.home_identity=true and pi.host_lms_id = h.id", nativeQuery = true)
        @Join(value = "hostLms", alias = "h")
	@NonNull
        @SingleResult
        Publisher<PatronIdentity> findHomePatronIdentityForPatron(@NotNull UUID patronId);

	@NonNull
	@SingleResult
	@Join("hostLms")
	Publisher<PatronIdentity> findOneByPatronIdAndHomeIdentity(@NotNull UUID patronId, @NotNull Boolean homeIdentity);

	@NonNull
	@SingleResult
	Publisher<? extends PatronIdentity> update(@Valid @NotNull @NonNull PatronIdentity patronIdentity);

	@NonNull
	@SingleResult
	Publisher<PatronIdentity> findById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<Boolean> existsById(@NonNull UUID id);

	@NonNull
	@SingleResult
	Publisher<PatronIdentity> findOneByPatronIdAndLocalId(@NotNull UUID patronId, @NotNull String localId);

	@SingleResult
	@NonNull
	default Publisher<PatronIdentity> saveOrUpdate(@Valid @NotNull PatronIdentity pi) {
		return Mono.from(this.existsById(pi.getId()))
			.flatMap( update -> Mono.from(update ? this.update(pi) : this.save(pi)) );
	}

	@NonNull
        @SingleResult
        Publisher<DataAgency> findResolvedAgencyById( @NonNull UUID id );
}
