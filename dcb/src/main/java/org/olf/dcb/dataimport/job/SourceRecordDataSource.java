package org.olf.dcb.dataimport.job;

import java.util.Optional;

import io.micronaut.core.naming.Named;
import io.micronaut.json.tree.JsonNode;
import reactor.core.publisher.Mono;
import services.k_int.micronaut.concurrency.ConcurrencyGroupAware;

public interface SourceRecordDataSource extends Named, ConcurrencyGroupAware {
	
	boolean isSourceImportEnabled();
	
	Mono<SourceRecordImportChunk> getChunk( Optional<JsonNode> parameters );
}
