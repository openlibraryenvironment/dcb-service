package org.olf.dcb.ingest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

import org.olf.dcb.configuration.ConfigurationRecord;
import org.olf.dcb.core.ProcessStateService;
import org.olf.dcb.core.interaction.shared.PublisherState;
import org.olf.dcb.ingest.model.IngestRecord;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.naming.Named;
import io.micronaut.core.util.Toggleable;
import io.micronaut.transaction.annotation.Transactional;
import services.k_int.micronaut.concurrency.ConcurrencyGroupAware;

public interface IngestSource extends BiFunction<Instant, Publisher<String>, Publisher<IngestRecord>>, Toggleable, Named, ConcurrencyGroupAware {

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
	Publisher<IngestRecord> apply(@Nullable Instant changedSince, Publisher<String> stopSignal);

	Publisher<ConfigurationRecord> getConfigStream();
	
	ProcessStateService getProcessStateService();
	
	PublisherState mapToPublisherState( Map<String, Object> mapData );
	
	@SingleResult
	Publisher<PublisherState> saveState(UUID context, String process, PublisherState state);
	
	/**
	 * Use the ProcessStateRepository to get the current state for
	 * <idOfLms>:"ingest" process - a list of name value pairs If we don't find one,
	 * just create a new empty map transform that data into the PublisherState class
	 * above ^^
	 */
	@SingleResult
	@Transactional
	default Publisher<PublisherState> getInitialState(UUID context, String process) {
		return getProcessStateService()
			.getStateMap(context, process)
			.defaultIfEmpty(new HashMap<>())
			.map(this::mapToPublisherState);
	}
}
