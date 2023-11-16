package org.olf.dcb.indexing;

import java.util.UUID;

public interface SharedIndexService {
	
	void initialize();
	
	void deleteAll();
	
	void add( UUID clusterID );
	
	void update( UUID clusterID );

	void delete( UUID clusterID );
	
	
}
