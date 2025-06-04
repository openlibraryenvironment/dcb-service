package org.olf.dcb.storage.postgres;

import java.util.UUID;

import org.olf.dcb.core.model.RecordCount;
import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.olf.dcb.storage.SourceRecordRepository;
import org.reactivestreams.Publisher;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsPageableRepository;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

@SuppressWarnings("unchecked")
@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional
public interface PostgresSourceRecordRepository extends ReactiveStreamsPageableRepository<SourceRecord, UUID>, SourceRecordRepository {
	
	@SingleResult
	@Query(value = "select processing_state as value, count(*) as count from source_record where host_lms_id = :hostLmsId group by processing_state order by processing_state", nativeQuery = true)
	public Publisher<RecordCount> getProcessStatusForHostLms(UUID hostLmsId);

	@SingleResult
	@Query(value = "select count(*) count from source_record where host_lms_id = :hostLmsId", nativeQuery = true)
	public Publisher<Long> getCountForHostLms(UUID hostLmsId);

//	@NonNull
//	@SingleResult
//	@Query("SELECT * FROM source_record WHERE host_lms_id = :hostLmsId AND remote_id LIKE :remoteId")
//	Publisher<SourceRecord> findByHostLmsIdAndRemoteIdLike(@NonNull UUID hostLmsId, @NonNull String remoteId);
}
