package org.olf.reshare.dcb.item.availability;

import java.util.List;

import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.interaction.HostLmsClient;
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
	public Mono<List<Item>> getAvailableItems(String bibRecordId, String hostLmsCode) {
		log.debug("getAvailableItems({}, {})", bibRecordId, hostLmsCode);

		return hostLmsService.getClientFor(hostLmsCode)
			.flatMapMany(hostLmsClient -> getItems(bibRecordId, hostLmsClient, hostLmsCode))
			.map(this::mapToAvailabilityItem)
			.collectList();
	}

	private static Flux<org.olf.reshare.dcb.core.interaction.Item> getItems(
		String bibRecordId, HostLmsClient hostLmsClient, String hostLmsCode) {
		log.debug("getItems({}, {}, {})", bibRecordId, hostLmsClient, hostLmsCode);

		return hostLmsClient.getItemsByBibId(bibRecordId, hostLmsCode)
			.flatMapMany(Flux::fromIterable);
	}

	private Item mapToAvailabilityItem(org.olf.reshare.dcb.core.interaction.Item item) {
		return new Item(item.getId(), new Status(item.getStatus().getCode(),
			item.getStatus().getDisplayText(), item.getStatus().getDueDate()),
			new Location(item.getLocation().getCode(), item.getLocation().getName()),
			item.getBarcode(), item.getCallNumber(), item.getHostLmsCode());
	}
}
