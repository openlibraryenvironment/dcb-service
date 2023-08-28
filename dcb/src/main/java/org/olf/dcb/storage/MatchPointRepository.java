package org.olf.dcb.storage;

import java.util.Collection;
import java.util.UUID;

import org.olf.dcb.core.model.clustering.MatchPoint;
import org.reactivestreams.Publisher;

public interface MatchPointRepository {

	<M extends MatchPoint> Publisher<MatchPoint> saveAll(Collection<MatchPoint> matchPoints);
	
	Publisher<MatchPoint> queryAll();
	
	Publisher<Void> deleteAllByBibId(UUID bib);
	
	Publisher<Void> delete (UUID id);
}
