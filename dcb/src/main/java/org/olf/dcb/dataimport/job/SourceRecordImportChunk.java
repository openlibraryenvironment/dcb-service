package org.olf.dcb.dataimport.job;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.dataimport.job.model.SourceRecord;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.json.tree.JsonNode;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import services.k_int.jobs.JobChunk;

@Data
@Builder(toBuilder = true)
@RequiredArgsConstructor
public class SourceRecordImportChunk implements JobChunk<SourceRecord> {
	

	@NonNull
	private final UUID jobId;
	
	private final boolean lastChunk;
	
	@NonNull
	private final JsonNode checkpoint;
	
	@NonNull
	@Singular("dataEntry")
	private final Collection<SourceRecord> data;
}
