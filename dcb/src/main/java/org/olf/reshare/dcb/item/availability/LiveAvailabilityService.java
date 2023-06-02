package org.olf.reshare.dcb.item.availability;

import java.util.List;

import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.model.Item;
import org.olf.reshare.dcb.request.resolution.Bib;
import org.olf.reshare.dcb.request.resolution.ClusteredBib;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Prototype;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Prototype
public class LiveAvailabilityService implements LiveAvailability {
	private static final Logger log = LoggerFactory.getLogger(LiveAvailabilityService.class);

	private final HostLmsService hostLmsService;
	private final RequestableItemService requestableItemService;

	public LiveAvailabilityService(HostLmsService hostLmsService,
		RequestableItemService requestableItemService) {

		this.hostLmsService = hostLmsService;
		this.requestableItemService = requestableItemService;
	}

	@Override
	public Mono<List<Item>> getAvailableItems(ClusteredBib clusteredBib) {
		log.debug("getAvailableItems({})", clusteredBib);

		return getBibs(clusteredBib)
			.flatMap(this::getItems)
			.flatMap(this::flattenItemList)
			.map(this::determineReqestability)
			.collectList()
			.map(this::sortItems);
	}

	private Flux<Bib> getBibs(ClusteredBib clusteredBib) {
		log.debug("getBibs: {}", clusteredBib);

		final var bibs = clusteredBib.getBibs();

		if (bibs== null) {
			log.error("Bibs cannot be null when asking for available items");

			return Flux.error(new IllegalArgumentException("Bibs cannot be null"));
		}

		return Flux.fromIterable(bibs);
	}

	private Mono<List<Item>> getItems(Bib bib) {
		log.debug("getItems({})", bib);

		if (bib.getHostLms() == null) {
			log.error("hostLMS cannot be null when asking for available items");

			return Mono.error(new IllegalArgumentException("hostLMS cannot be null"));
		}

		return hostLmsService.getClientFor(bib.getHostLms())
			.flatMap(hostLmsClient -> getItems(bib.getBibRecordId(), hostLmsClient, bib.getHostLms().getCode()))
			.doOnError(error -> log.debug("Error occurred fetching items: ", error))
			.onErrorReturn(List.of());
	}

	private Mono<List<Item>> getItems(String bibRecordId,
		HostLmsClient hostLmsClient, String hostLmsCode) {

		log.debug("getItems({}, {}, {})", bibRecordId, hostLmsClient, hostLmsCode);

		return hostLmsClient.getItemsByBibId(bibRecordId, hostLmsCode);
	}

	private Item determineReqestability(Item item) {
		return item.markAsRequestable(requestableItemService.isRequestable(item));
	}

	private Flux<Item> flattenItemList(List<Item> list) {
		return Flux.fromIterable(list);
	}

	private List<Item> sortItems(List<Item> items) {
		return items.stream().sorted().toList();
	}
}
