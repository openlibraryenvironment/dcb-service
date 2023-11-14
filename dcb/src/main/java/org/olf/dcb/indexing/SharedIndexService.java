package org.olf.dcb.indexing;

import java.util.UUID;

import reactor.core.publisher.Mono;

public interface SharedIndexService {
	
	Mono<Void> initialize();
	
	Mono<Void> deleteAll();
	
	void add( UUID clusterID );
	
	void update( UUID clusterID );

	void delete( UUID clusterID );
}
