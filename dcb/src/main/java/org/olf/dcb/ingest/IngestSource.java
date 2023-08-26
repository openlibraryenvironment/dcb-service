package org.olf.dcb.ingest;

import java.time.Instant;
import java.util.function.BiFunction;

import org.olf.dcb.configuration.ConfigurationRecord;
import org.olf.dcb.ingest.model.IngestRecord;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.Named;
import io.micronaut.core.util.Toggleable;

import io.micronaut.core.convert.ConversionService;

public interface IngestSource extends BiFunction<Instant, ConversionService, Publisher<IngestRecord>>, Toggleable, Named {

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
	Publisher<IngestRecord> apply(@Nullable Instant changedSince, ConversionService conversionService);

	Publisher<ConfigurationRecord> getConfigStream();
}
