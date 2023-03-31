package org.olf.reshare.dcb.request.resolution;

import java.util.List;
import java.util.UUID;

import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.item.availability.LiveAvailability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
public class PatronRequestResolutionService {
	private static final Logger log = LoggerFactory.getLogger(PatronRequestResolutionService.class);
	private final SharedIndexService sharedIndexService;
	private final LiveAvailability liveAvailabilityService;

	public PatronRequestResolutionService(SharedIndexService sharedIndexService,
		@Named("fakeLiveAvailabilityService") LiveAvailability liveAvailabilityService) {
		this.sharedIndexService = sharedIndexService;
		this.liveAvailabilityService = liveAvailabilityService;
	}

	public Mono<SupplierRequest> resolvePatronRequest(PatronRequest patronRequest) {
		log.debug("resolvePatronRequest({})", patronRequest);
		return sharedIndexService.findClusteredBib(patronRequest.getBibClusterId())
			.filter(this::validateClusteredBib)
			// get list of bibs from clustered bib
			.map(ClusteredBib::getBibs)
			.flatMapMany(Flux::fromIterable)
			// from each bib get list of items
			.flatMap(this::getAvailableItems)
			// merge list of bibs of list of items into 1 list of items
			.flatMap(Flux::fromIterable)
			// get list of bibs from clustered bib
			.collectList()
			.filter(this::validateItemList)
			// pick first item from merged list
			.flatMap(PatronRequestResolutionService::chooseFirstItem)
			.map(item -> mapToSupplierRequest(item, patronRequest));
	}



	private boolean validateItemList(List<org.olf.reshare.dcb.item.availability.Item> itemList) {
		log.debug("validateItemList({})", itemList);

		if (itemList == null || itemList.isEmpty()) {
			throw new UnableToResolvePatronRequest("No items in bib");
		}

		return true;
	}

	private boolean validateClusteredBib(ClusteredBib clusteredBib) {
		log.debug("validateClusteredBib({})", clusteredBib);

		if (clusteredBib == null) {
			throw new UnableToResolvePatronRequest("Clustered bib was null");
		}

		final var bibs = clusteredBib.getBibs();

		if (bibs == null || bibs.isEmpty()) {
			throw new UnableToResolvePatronRequest("No bibs in clustered bib");
		}

		return true;
	}

	private Mono<List<org.olf.reshare.dcb.item.availability.Item>> getAvailableItems(Bib bib) {
		log.debug("getAvailableItems({})", bib);
		return liveAvailabilityService.getAvailableItems(bib.getBibRecordId(),
			bib.getHostLmsCode());
	}

	private static Mono<Item> chooseFirstItem(List<org.olf.reshare.dcb.item.availability.Item> itemList) {
		log.debug("chooseFirstItem({})", itemList.get(0));
		// choose first availability item and convert it into a resolution item
		return Mono.just(itemList.get(0))
			.map(item -> new Item(item.getId(), item.getHostLmsCode()));
	}

	private static SupplierRequest mapToSupplierRequest(Item item, PatronRequest patronRequest) {
		log.debug("mapToSupplierRequest({}}, {})", item, patronRequest);

		final var uuid = UUID.randomUUID();
		log.debug("create SR: {}, {}, {}", uuid, item, item.getHostLmsCode());

		log.debug("Resolve the patron request");
		final var updatedPatronRequest = patronRequest.resolve();

		return new SupplierRequest(uuid, updatedPatronRequest,
			item.getId(), item.getHostLmsCode());
	}
}
