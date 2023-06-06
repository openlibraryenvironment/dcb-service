package org.olf.reshare.dcb.item.availability;

import org.olf.reshare.dcb.request.resolution.ClusteredBib;

import reactor.core.publisher.Mono;

public interface LiveAvailability {
	Mono<AvailabilityReport> getAvailableItems(ClusteredBib clusteredBib);
}
