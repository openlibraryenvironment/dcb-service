package org.olf.dcb.ingest.job;

import java.util.Collection;
import java.util.UUID;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.json.tree.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import services.k_int.jobs.JobChunk;

@Data
@Builder(toBuilder = true)
@RequiredArgsConstructor
public class IngestJobChunk implements JobChunk<IngestOperation> {
	
	@NonNull
	@NotNull
	private final UUID jobId;
	
	private final boolean lastChunk;
	
	@NonNull
	@NotNull
	private final JsonNode checkpoint;
	
	@NonNull
	@Singular("dataEntry")
	private final Collection<IngestOperation> data;
}
