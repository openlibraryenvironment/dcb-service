package org.olf.dcb.indexing;

import java.util.UUID;

import org.reactivestreams.Publisher;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;

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
	
	
}
