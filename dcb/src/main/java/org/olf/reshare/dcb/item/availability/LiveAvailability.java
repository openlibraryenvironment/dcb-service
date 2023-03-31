package org.olf.reshare.dcb.item.availability;

import reactor.core.publisher.Mono;

import java.util.List;

public interface LiveAvailability {
	Mono<List<Item>> getAvailableItems(String bibRecordId, String hostLmsCode);
}
