package org.olf.dcb.storage.postgres;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.clustering.model.MatchPoint;
import org.olf.dcb.storage.MatchPointRepository;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresMatchPointRepository extends ReactiveStreamsPageableRepository<MatchPoint, UUID>, MatchPointRepository {

	@NonNull
	@Override
	@Query("""
		SELECT * FROM match_point
		  INNER JOIN bib_record ON bib_id = bib_record.id
		WHERE value IN (:points)
		  AND bib_record.derived_type = :derivedType
		ORDER BY bib_id;""")
	Publisher<MatchPoint> getMatchesByDerrivedType (String derivedType, Collection<UUID> points );
}
