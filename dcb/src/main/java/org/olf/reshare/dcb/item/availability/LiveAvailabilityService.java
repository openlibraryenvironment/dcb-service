package org.olf.reshare.dcb.item.availability;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Stream;

import static org.olf.reshare.dcb.item.availability.LiveAvailabilityConstants.AVAILABLE;

@Singleton
public class LiveAvailabilityService {
	private static final Logger log = LoggerFactory.getLogger(LiveAvailabilityService.class);

	public Mono<List<Item>> getAvailableItems(String bidRecordId, String systemCode) {
		log.debug("getAvailableItems({}, {})", bidRecordId, systemCode);
		return Mono.just(getList(bidRecordId, systemCode));
	}
	private List<Item> getList(String bidRecordId, String systemCode) {
		log.debug("getList({}, {})", bidRecordId, systemCode);
		return Stream.of(new Item("1000001", new Status("-", AVAILABLE)),
				new Item("1000002", new Status("-", AVAILABLE)),
				new Item("1000003", new Status("-", AVAILABLE)))
			.toList();
	}
}
