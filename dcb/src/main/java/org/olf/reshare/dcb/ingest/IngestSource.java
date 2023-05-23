package org.olf.reshare.dcb.ingest;

import java.time.Instant;
import java.util.function.Function;

import org.olf.reshare.dcb.configuration.ConfigurationRecord;
import org.olf.reshare.dcb.ingest.model.IngestRecord;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.Named;
import io.micronaut.core.util.Toggleable;

public interface IngestSource extends Function<Instant, Publisher<IngestRecord>>, Toggleable, Named {

	public static final int DEFAULT_PAGE_SIZE = 1000;

	/**
	 * Take in an Instant representing the point in time to use as the changed since
	 * and return a publisher of IngestRecords
	 *
	 * @param changedSince Instant representing the point in time for the delta
	 * @return A Publisher of IngestRecords
	 */
	@Override
	Publisher<IngestRecord> apply(@Nullable Instant changedSince);

	Publisher<ConfigurationRecord> getConfigStream();
}
