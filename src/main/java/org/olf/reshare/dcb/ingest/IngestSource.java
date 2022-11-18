package org.olf.reshare.dcb.ingest;

import java.time.Instant;
import java.util.function.Function;

import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.Nullable;

public interface IngestSource extends Function<Instant, Publisher<IngestRecord>> {
	
	/**
	 * Take in an Instant representing the point in time to use as the changed since
	 * and return a publisher of IngestRecords
	 *
	 * @param changedSince Instant representing the point in time for the delta
	 * @return A Publisher of IngestRecords 
	 */
	@Override
	Publisher<IngestRecord> apply(@Nullable Instant changedSince);
}
