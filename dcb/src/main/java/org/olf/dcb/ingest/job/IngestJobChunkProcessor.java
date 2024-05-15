package org.olf.dcb.ingest.job;

import org.reactivestreams.Publisher;

import services.k_int.jobs.JobChunk;
import services.k_int.jobs.JobChunkProcessor;
import services.k_int.jobs.JobChunkProcessor.ApplicableChunkTypes;

@ApplicableChunkTypes(IngestJobChunk.class)
public interface IngestJobChunkProcessor extends JobChunkProcessor {
	
	@Override
	default <T> Publisher<JobChunk<T>> processChunk( JobChunk<T> chunk ) {
		// TODO Auto-generated method stub
		return null;
	}
}
