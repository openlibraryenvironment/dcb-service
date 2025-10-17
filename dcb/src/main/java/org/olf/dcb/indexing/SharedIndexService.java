package org.olf.dcb.indexing;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.indexing.bulk.IndexOperation;
import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import reactor.core.publisher.Flux;

public interface SharedIndexService {
	
	@SingleResult
	@NonNull
	Publisher<Void> initialize();
	
	@SingleResult
	@NonNull
	Publisher<Void> deleteAll();
	
	void add( UUID clusterID );
	
	void update( UUID clusterID );

	void delete( UUID clusterID );

	Publisher<List<IndexOperation<UUID, ClusterRecord>>> expandAndProcess(Flux<List<UUID>> idFlux);
	
}
