package org.olf.dcb.storage.postgres;

import static jakarta.transaction.Transactional.TxType.NOT_SUPPORTED;

import java.util.Collection;
import java.util.UUID;

import jakarta.transaction.Transactional;

import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.storage.BibRepository;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

@SuppressWarnings("unchecked")
@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresBibRepository extends ReactiveStreamsPageableRepository<BibRecord, UUID>, BibRepository {
	
	@Override
	@NonNull
	@SingleResult
	@Query("UPDATE bib_record SET contributes_to=:contributesTo WHERE contributes_to IN (:contributesToList)")
	Publisher<Void> updateByContributesToInList(@NonNull Collection<ClusterRecord> contributesToList, @NonNull ClusterRecord contributesTo);
	
	@Override
	@Transactional(NOT_SUPPORTED)
	@NonNull
	@SingleResult
	default Publisher<Void> cleanUp() {
		return Mono.empty();
	}
	
	@Override
	@Transactional(NOT_SUPPORTED)
	@NonNull
	@SingleResult
	default Publisher<Void> commit() {
		return Mono.empty();
	}

}
