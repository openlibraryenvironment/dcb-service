package org.olf.dcb.storage.postgres;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.storage.ClusterRecordRepository;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Join.Type;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.QueryHint;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.jpa.reactive.ReactiveStreamsJpaSpecificationExecutor;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.function.TupleUtils;

@SuppressWarnings("unchecked")
@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresClusterRecordRepository extends 
                                                        ReactiveStreamsPageableRepository<ClusterRecord, UUID>, 
                                                        ReactiveStreamsJpaSpecificationExecutor<ClusterRecord>, 
                                                        ClusterRecordRepository {

	// Publisher<Page<ClusterRecord>> findAll(@Valid Pageable pageable);

	/*
	 * // Doing the join can cause recursion in the graph
	 * 
	 * @Join("bibs") Publisher<? extends ClusterRecord> findOneById(@NonNull UUID
	 * id);
	 * 
	 * @Join("bibs") Publisher<ClusterRecord> findById(@NotNull UUID id);
	 */

	@Query(value = "SELECT cr_.*, mp_.value mp_val FROM cluster_record cr_"
			+ "	INNER JOIN bib_record br_ ON br_.contributes_to = cr_.id"
			+ "	INNER JOIN match_point mp_ ON mp_.bib_id = br_.id"
			+ "	WHERE mp_.value IN (:points) ORDER BY date_created ASC;")
	Publisher<ClusterRecord> findAllByMatchPoints ( Collection<UUID> points );
	
	@Query(value = "SELECT cr_.*, mp_.value mp_val FROM cluster_record cr_"
			+ "	INNER JOIN bib_record br_ ON br_.contributes_to = cr_.id"
			+ "	INNER JOIN match_point mp_ ON mp_.bib_id = br_.id"
			+ "	WHERE br_.derived_type = :derivedType "
			+ "   AND mp_.value IN (:points)"
			+ " ORDER BY date_created ASC;")
	Publisher<ClusterRecord> findAllByDerivedTypeAndMatchPoints ( String derivedType, Collection<UUID> points );
	
	@NonNull
	@Override
	@Query(value = "SELECT cr_.* FROM cluster_record cr_"
			+ "	INNER JOIN bib_record br_ ON br_.contributes_to = cr_.id"
			+ "	WHERE br_.id NOT IN (:bibIds)"
			+ "   AND br_.derived_type = :derivedType"
			+ "   AND cr_.id NOT IN (:excludeClusters)"
			+ " ORDER BY date_created ASC;")
	Publisher<ClusterRecord> findAllByBibIdInAndDerivedTypeAndIdNotIn ( Collection<UUID> bibIds, String derivedType, Collection<UUID> excludeClusters );
	
//  @Query(value = "SELECT cr_.*, mp_.value mp_val FROM cluster_record cr_"
//      + " INNER JOIN bib_record br_ ON br_.contributes_to = cr_.id"
//      + " INNER JOIN match_point mp_ ON mp_.bib_id = br_.id"
//      + " WHERE br_.derived_type = :derivedType "
//      + "   AND mp_.value IN (:points)"
//			+ "   AND not exists ( Select bi_.id from bib_identifier bi_ where bi_.owner = br_.id AND bi_.namespace='ONLY-ISBN-13' AND bi_.value <> :isbnExclusion"
//      + " ORDER BY date_created ASC;")
//  Publisher<ClusterRecord> findAllByDerivedTypeAndMatchPointsWithISBNExclusion ( String derivedType, Collection<UUID> points, String isbnExclusion );

	@NonNull
	@Override
	@Query(
		"SELECT id FROM cluster_record cr "
		+ "WHERE EXISTS ("
		+ "	SELECT * FROM bib_record LEFT JOIN source_record ON source_record.id = bib_record.source_record_uuid"
		+ "	WHERE (bib_record.source_record_uuid IS NULL"
		+ "	  OR source_record.processing_state != 'PROCESSING_REQUIRED')"
		+ "	AND contributes_to = cr.id AND process_version < :version"
		+ ") "
		+ "LIMIT :max;")
	Publisher<UUID> getClusterIdsWithOutdatedUnprocessedBibs( int version, int max );

	@NonNull
	@Override
	@Query(
			"SELECT cr.id FROM cluster_record cr " +
			"WHERE cr.id IN (:ids)" +
				"AND EXISTS ( " +
					"SELECT * FROM bib_record " +
					"WHERE contributes_to = cr.id AND process_version < :version " +
				");")
	Publisher<UUID> getClusterIdsWithBibsPriorToVersionInList( int version, Collection<UUID> ids );

	@NonNull
	@SingleResult
	@Override
	default Publisher<Page<ClusterRecord>> findByDateUpdatedGreaterThanOrderByDateUpdated(Instant i, @Valid Pageable pageable) {
		
		return Mono.from( findIdByDateUpdatedGreaterThanOrderByDateUpdated(i, pageable) )
			.zipWhen( uuids -> Flux.from( findAllByIdInOrderByDateUpdated(uuids.getContent())).collectList() )
			.map(TupleUtils.function(( page, expandedData ) ->
				Page.of(expandedData, page.getPageable(), page.getTotalSize())))
		;
	}
	
	@NonNull
	@Join(value = "bibs", type = Type.LEFT_FETCH)
	Publisher<ClusterRecord> findAllByIdInOrderByDateUpdated(Collection<UUID> id);
	
	@NonNull
	@SingleResult
	Publisher<Page<UUID>> findIdByDateUpdatedGreaterThanOrderByDateUpdated(Instant i, @Valid Pageable pageable);
	
	@SingleResult
	Publisher<Long> updateById( @NonNull UUID id, Instant dateUpdated);
	
	@Override
	@SingleResult
	default Publisher<Long> touch( @NonNull UUID id ) {
		return this.updateById(id, Instant.now());
	}
	
	@Override
	@QueryHint(name="javax.persistence.FlushModeType", value="AUTO")
	@NonNull
	@SingleResult
	Publisher<? extends ClusterRecord> findOneById(@NonNull UUID id);

	@NonNull
	@Join(value = "bibs", type = Type.LEFT_FETCH)
	Publisher<ClusterRecord> getAllByIdInList(@NonNull Collection<UUID> id);

	@NonNull
	@Override
	default Publisher<ClusterRecord> findByIdInListWithBibs(@NonNull Collection<UUID> id) {
		return getAllByIdInList(id);
	}
	
	@NonNull
	@SingleResult
	Publisher<ClusterRecord> findById(@NotNull UUID id);
}

