package org.olf.reshare.dcb.item.availability;

import java.util.List;

import org.olf.reshare.dcb.core.model.HostLms;

import reactor.core.publisher.Mono;

public interface LiveAvailability {
	Mono<List<Item>> getAvailableItems(String bibRecordId, HostLms hostLms);
}
