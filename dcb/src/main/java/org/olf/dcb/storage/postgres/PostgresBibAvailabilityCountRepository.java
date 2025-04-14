package org.olf.dcb.storage.postgres;

import java.util.UUID;

import org.olf.dcb.availability.job.BibAvailabilityCount;
import org.olf.dcb.availability.job.BibAvailabilityCount.Status;
import org.olf.dcb.storage.BibAvailabilityCountRepository;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.jpa.reactive.ReactorJpaSpecificationExecutor;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;

@Singleton
@Transactional
@SuppressWarnings("unchecked")
@R2dbcRepository(dialect = Dialect.POSTGRES)
public interface PostgresBibAvailabilityCountRepository extends ReactiveStreamsPageableRepository<BibAvailabilityCount, UUID>,
									ReactorJpaSpecificationExecutor<BibAvailabilityCount>, BibAvailabilityCountRepository {
	
	
	@NonNull
	@Query(value = "SELECT * FROM bib_availability_count "
			+ " WHERE status = :status"
			+ " AND "
			+ " EXISTS ( SELECT id FROM bib_record WHERE bib_record.id = bib_id AND contributes_to = :contributesTo);",
			readOnly = true, nativeQuery = true)
	Publisher<BibAvailabilityCount> findAllKnownForClusterWithStatus(@NotNull UUID contributesTo, Status status);
	
	@Override
	default @NonNull Publisher<BibAvailabilityCount> findAllKnownForCluster(@NotNull @NonNull UUID clusterId) {
		return findAllKnownForClusterWithStatus(clusterId, Status.MAPPED);
	}

}
