package org.olf.reshare.dcb.item.availability;

import java.util.List;

import org.olf.reshare.dcb.core.model.Item;

import org.olf.reshare.dcb.request.resolution.ClusteredBib;
import reactor.core.publisher.Mono;

public interface LiveAvailability {
	Mono<List<Item>> getAvailableItems(ClusteredBib ClusteredBib);
}
