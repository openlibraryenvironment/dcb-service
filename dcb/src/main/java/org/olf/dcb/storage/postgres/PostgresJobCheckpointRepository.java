package org.olf.dcb.storage.postgres;

import java.util.UUID;

import org.olf.dcb.storage.JobCheckpointRepository;
import org.reactivestreams.Publisher;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.annotation.R2dbcRepository;
import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.transaction.TransactionDefinition.Propagation;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import services.k_int.jobs.JobCheckpoint;

@Singleton
@R2dbcRepository(dialect = Dialect.POSTGRES)
@Transactional(propagation = Propagation.MANDATORY)
public interface PostgresJobCheckpointRepository extends ReactiveStreamsCrudRepository<JobCheckpoint, UUID>, JobCheckpointRepository {
	
	@Override
	default Publisher<JsonNode> findCheckpointByJobId( UUID jobId ) {
		
		return Mono.from( findById(jobId) )
			.map( JobCheckpoint::getValue );
	}
	
	Publisher<Long> updateById( UUID id, JsonNode value );
	

	Publisher<Long> save( UUID id, JsonNode value );
	
	@Override
	default Publisher<JsonNode> saveCheckpointForJobId( UUID jobId, JsonNode data ) {
		
		return Mono.from( existsById(jobId) )
			.map( exists -> exists == true ? updateById( jobId, data ) : save( jobId, data) )
			.flatMap( Mono::from )
			.thenReturn( data );
	}
}
