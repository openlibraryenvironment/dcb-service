package org.olf.dcb.dataimport.job;

import java.util.Optional;

import org.olf.dcb.dataimport.job.model.SourceRecord;
import org.reactivestreams.Publisher;

import io.micronaut.core.naming.Named;
import io.micronaut.json.tree.JsonNode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import services.k_int.micronaut.concurrency.ConcurrencyGroupAware;

public interface SourceRecordDataSource extends Named, ConcurrencyGroupAware {

	boolean isSourceImportEnabled();

	Mono<SourceRecordImportChunk> getChunk( Optional<JsonNode> parameters );

	/**
	 * Re-fetch records this source holds but DCB does not, without a full re-harvest.
	 *
	 * A harvest that resumes correctly still never recovers records it previously skipped, because
	 * delta harvesting only surfaces records changed after the watermark. Sources that can enumerate
	 * their own id space override this to close that gap.
	 *
	 * @param knownRemoteIds streamed remote ids DCB already holds for this source. Streamed rather
	 *        than collected so implementations fold them into a bounded structure.
	 * @return the records DCB was missing. Empty when the source cannot enumerate itself.
	 */
	default Flux<SourceRecord> findMissingRecords( Publisher<String> knownRemoteIds ) {
		return Flux.empty();
	}
}
