package org.olf.reshare.dcb.item.availability;

import java.util.List;

import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.model.HostLms;
import org.olf.reshare.dcb.core.model.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
public class LiveAvailabilityService implements LiveAvailability {
	private static final Logger log = LoggerFactory.getLogger(LiveAvailabilityService.class);

	private final HostLmsService hostLmsService;

	public LiveAvailabilityService(HostLmsService hostLmsService) {
		this.hostLmsService = hostLmsService;
	}

	@Override
	public Mono<List<Item>> getAvailableItems(String bibRecordId, HostLms hostLms) {
		log.debug("getAvailableItems({}, {})", bibRecordId, hostLms);

		if (hostLms == null) {
			log.error("hostLMS cannot be null when asking for available items");

			return Mono.error(new IllegalArgumentException("hostLMS cannot be null"));
		}

		return hostLmsService.getClientFor(hostLms)
			.flatMapMany(hostLmsClient -> getItems(bibRecordId, hostLmsClient, hostLms.getCode()))
			.collectList();
	}

	private static Flux<Item> getItems(
		String bibRecordId, HostLmsClient hostLmsClient, String hostLmsCode) {

		log.debug("getItems({}, {}, {})", bibRecordId, hostLmsClient, hostLmsCode);

		return hostLmsClient.getItemsByBibId(bibRecordId, hostLmsCode)
			.flatMapMany(Flux::fromIterable);
	}
}
