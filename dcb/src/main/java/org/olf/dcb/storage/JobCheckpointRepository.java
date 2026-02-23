package org.olf.dcb.storage;

import java.util.UUID;

import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Vetoed;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.json.tree.JsonNode;

public interface JobCheckpointRepository {

	@Vetoed
	@SingleResult
	Publisher<JsonNode> findCheckpointByJobId( @NonNull UUID jobId );

	@Vetoed
	@SingleResult
	Publisher<JsonNode> saveCheckpointForJobId( @NonNull UUID jobId, @Nullable JsonNode data);

	@SingleResult
	Publisher<Long> deleteById(@NonNull UUID jobId);
}
