package org.olf.dcb.storage.postgres;

import static jakarta.transaction.Transactional.TxType.NOT_SUPPORTED;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.clustering.RecordClusteringService.MissingAvailabilityInfo;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.storage.BibRepository;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.jpa.reactive.ReactiveStreamsJpaSpecificationExecutor;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import reactor.core.publisher.Mono;


@SuppressWarnings("unchecked")
@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresBibRepository extends ReactiveStreamsPageableRepository<BibRecord, UUID>, ReactiveStreamsJpaSpecificationExecutor<BibRecord>, BibRepository {
	
	@Override
	@Query(value = """
		SELECT bib_record.id as bib_id, contributes_to as cluster_id, source_system_id, bib_record.date_updated
		FROM bib_record
		WHERE NOT EXISTS (
			SELECT bib_availability_count.id
			FROM bib_availability_count
			WHERE bib_availability_count.bib_id = bib_record.id
			AND (
					bib_availability_count.status != 'RECHECK_REQUIRED'
					AND (
						bib_availability_count.grace_period_end > NOW()
						OR (bib_availability_count.grace_period_end IS NULL
					AND bib_availability_count.last_updated < :graceCutoff)))
		)
		ORDER BY contributes_to, bib_record.date_updated ASC NULLS FIRST
		LIMIT :limit;""", nativeQuery = true)
	public Publisher<MissingAvailabilityInfo> findMissingAvailability (int limit, Instant graceCutoff);
	
	
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
