package org.olf.dcb.ingest;

import java.time.Instant;
import java.util.function.Function;

import org.olf.dcb.configuration.ConfigurationRecord;
import org.olf.dcb.ingest.model.IngestRecord;
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
	 * @param conversionsService the conversionService to use
	 * @return A Publisher of IngestRecords
	 */
	@Override
	Publisher<IngestRecord> apply(@Nullable Instant changedSince);

	Publisher<ConfigurationRecord> getConfigStream();
}
