package org.olf.dcb.storage;

import java.util.UUID;

import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.json.tree.JsonNode;

public interface JobCheckpointRepository {
	
	@SingleResult
	@Vetoed
	Publisher<JsonNode> findCheckpointByJobId( @NonNull UUID jobId );

	@SingleResult
	@Vetoed
	Publisher<JsonNode> saveCheckpointForJobId( @NonNull UUID jobId, @Nullable JsonNode data);
}
