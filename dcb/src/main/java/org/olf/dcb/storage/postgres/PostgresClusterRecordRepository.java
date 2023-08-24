package org.olf.dcb.storage.postgres;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;

import org.olf.dcb.core.model.clustering.ClusterRecord;
import org.olf.dcb.storage.ClusterRecordRepository;
import org.reactivestreams.Publisher;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;


@SuppressWarnings("unchecked")
@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresClusterRecordRepository extends ReactiveStreamsPageableRepository<ClusterRecord, UUID>, ClusterRecordRepository {

	/* @Join("bibs") we don't need the bibs, just the record from the primary bib */
	Publisher<Page<ClusterRecord>> findAll(@Valid Pageable pageable);

	/*
	 * // Doing the join can cause recursion in the graph
	 * 
	 * @Join("bibs") Publisher<? extends ClusterRecord> findOneById(@NonNull UUID
	 * id);
	 * 
	 * @Join("bibs") Publisher<ClusterRecord> findById(@NotNull UUID id);
	 */

	/* @Join("bibs") */
	Publisher<Page<ClusterRecord>> findByDateUpdatedGreaterThanOrderByDateUpdated(Instant i, @Valid Pageable pageable);

	@Query(value = "SELECT cr_.*, mp_.value mp_val FROM cluster_record cr_"
			+ "	INNER JOIN bib_record br_ ON br_.contributes_to = cr_.id"
			+ "	INNER JOIN match_point mp_ ON mp_.bib_id = br_.id"
			+ "	WHERE mp_.value IN (:points) ORDER BY date_created ASC;")
	Publisher<ClusterRecord> findAllByMatchPoints ( Collection<UUID> points );
	
	@Query(value = "SELECT cr_.*, mp_.value mp_val FROM cluster_record cr_"
			+ "	INNER JOIN bib_record br_ ON br_.contributes_to = cr_.id"
			+ "	INNER JOIN match_point mp_ ON mp_.bib_id = br_.id"
			+ "	WHERE (br_.derived_type IS NULL OR br_.derived_type = :derivedType )"
			+ "   AND mp_.value IN (:points)"
			+ " ORDER BY date_created ASC;")
	Publisher<ClusterRecord> findAllByDerivedTypeAndMatchPoints ( String derivedType, Collection<UUID> points );
}

