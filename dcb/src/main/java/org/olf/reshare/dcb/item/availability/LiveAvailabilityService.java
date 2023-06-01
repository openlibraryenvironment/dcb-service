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

		return Mono.just(clusteredBib)
			.flatMapMany(this::getBibs)
			.flatMap(this::getBibItemsByHostLms)
			// merge lists
			.flatMap(Flux::fromIterable)
			.doOnNext(item -> log.debug("Item Found: {}", item))
			.collectList()
			.map(requestableItemService::determineRequestable)
			.map(items -> items.stream().sorted().toList());
	}

	private Flux<Bib> getBibs(ClusteredBib clusteredBib) {
		log.debug("getBibs: {}", clusteredBib);

		if (clusteredBib.getBibs() == null) {
			log.error("Bibs cannot be null when asking for available items");

			return Flux.error(new IllegalArgumentException("Bibs cannot be null"));
		}

		return Mono.just(clusteredBib)
			// get list of bibs
			.map(ClusteredBib::getBibs)
			.flatMapMany(Flux::fromIterable);
	}

	private Mono<List<Item>> getBibItemsByHostLms(Bib bib) {
		log.debug("getBibItemsByHostLms({}, {})", bib.getBibRecordId(), bib.getHostLms());

		if (bib.getHostLms() == null) {
			log.error("hostLMS cannot be null when asking for available items");

			return Mono.error(new IllegalArgumentException("hostLMS cannot be null"));
		}

		return hostLmsService.getClientFor(bib.getHostLms())
			.flatMapMany(hostLmsClient -> getItems(bib.getBibRecordId(), hostLmsClient, bib.getHostLms().getCode()))
			.collectList()
			.doOnError(error -> log.debug("Error occurred fetching items: ", error))
			.onErrorReturn(List.of());
	}

	private Flux<Item> getItems(String bibRecordId, HostLmsClient hostLmsClient,
		String hostLmsCode) {

		log.debug("getItems({}, {}, {})", bibRecordId, hostLmsClient, hostLmsCode);

		return hostLmsClient.getItemsByBibId(bibRecordId, hostLmsCode)
			.flatMapMany(Flux::fromIterable);
	}
}
