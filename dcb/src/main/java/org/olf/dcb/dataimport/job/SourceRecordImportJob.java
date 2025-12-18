package org.olf.dcb.dataimport.job;

import java.util.Optional;

import org.olf.dcb.dataimport.job.model.SourceRecord;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.json.tree.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import services.k_int.jobs.Job;
import services.k_int.jobs.JobChunk;

@Slf4j
@RequiredArgsConstructor
public class SourceRecordImportJob implements Job<SourceRecord> {

	final SourceRecordDataSource datasource;

	@Override
	public String getName() {
		return "%s Source Record Import".formatted( datasource.getName() );
	}
	
	private Mono<JobChunk<SourceRecord>> getChunkForCheckpoint( Optional<JsonNode> checkpointOpt ) {
		return Mono.just(checkpointOpt)
			.flatMap( datasource::getChunk )
			.map( chunk -> chunk.toBuilder().jobId(getId()).build() );
	}

	@Override
	@SingleResult
	public Mono<JobChunk<SourceRecord>> start() {
		log.info("Start job [{}] from the beginning", getName());
		return getChunkForCheckpoint(Optional.empty());
	}

	@Override
	@SingleResult
	public Mono<JobChunk<SourceRecord>> resume(@NonNull JsonNode lastCheckpoint) {
		log.info("Resume job [{}] using checkpoint: [{}]", getName(), lastCheckpoint.getValue());
		return getChunkForCheckpoint(Optional.of(lastCheckpoint));
	}
}
