package org.olf.reshare.dcb.request.resolution;

import java.util.List;
import java.util.Optional;
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
	private final ClusteredBibFinder clusteredBibFinder;
	private final LiveAvailability liveAvailabilityService;

	public PatronRequestResolutionService(
		@Named("SharedIndexService") ClusteredBibFinder clusteredBibFinder,
		@Named("LiveAvailabilityService") LiveAvailability liveAvailabilityService) {

		this.clusteredBibFinder = clusteredBibFinder;
		this.liveAvailabilityService = liveAvailabilityService;
	}

	public Mono<Resolution> resolvePatronRequest(PatronRequest patronRequest) {
		log.debug("resolvePatronRequest({})", patronRequest);

		return clusteredBibFinder.findClusteredBib(patronRequest.getBibClusterId())
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
			// pick first item from merged list
			.flatMap(items -> chooseFirstItem(items, patronRequest.getBibClusterId()))
			.map(item -> mapToSupplierRequest(item, patronRequest))
			.map(PatronRequestResolutionService::mapToResolution)
			.onErrorReturn(NoItemsAvailableAtAnyAgency.class,
				resolveToNoItemsAvailable(patronRequest));
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

		return liveAvailabilityService
			.getAvailableItems(bib.getBibRecordId(), bib.getHostLms());
	}

	private static Mono<Item> chooseFirstItem(
		List<org.olf.reshare.dcb.item.availability.Item> items, UUID bibClusterId) {

		log.debug("chooseFirstItem({})", items);

		if (items.isEmpty()) {
			final var message = "No items could be found for cluster record: " + bibClusterId;

			log.debug(message);

			return Mono.error(new NoItemsAvailableAtAnyAgency(message));
		}

		// choose first availability item and convert it into a resolution item
		return Mono.just(items.get(0))
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

	private static Resolution resolveToNoItemsAvailable(PatronRequest patronRequest) {
		return new Resolution(patronRequest.resolveToNoItemsAvailable(), Optional.empty());
	}

	private static Resolution mapToResolution(SupplierRequest supplierRequest) {
		return new Resolution(supplierRequest.getPatronRequest(),
			Optional.of(supplierRequest));
	}
}
