package org.olf.reshare.dcb.request.resolution;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.olf.reshare.dcb.core.model.Item;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.item.availability.LiveAvailability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.olf.reshare.dcb.utils.PublisherErrors.failWhenEmpty;

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

		final var clusterRecordId = patronRequest.getBibClusterId();

		return findClusterRecord(clusterRecordId)
			.map(this::validateClusteredBib)
			.flatMap(this::getItems)
			.map(items -> chooseFirstRequestableItem(items, clusterRecordId))
			.map(item -> mapToSupplierRequest(item, patronRequest))
			.map(PatronRequestResolutionService::mapToResolution)
			.onErrorReturn(NoItemsRequestableAtAnyAgency.class,
				resolveToNoItemsAvailable(patronRequest));
	}

	private Mono<ClusteredBib> findClusterRecord(UUID clusterRecordId) {
		return clusteredBibFinder.findClusteredBib(clusterRecordId)
			.map(Optional::ofNullable)
			.defaultIfEmpty(Optional.empty())
			.map(optionalClusterRecord ->
				failWhenClusterRecordIsEmpty(optionalClusterRecord, clusterRecordId));
	}

	private static ClusteredBib failWhenClusterRecordIsEmpty(
		Optional<ClusteredBib> optionalClusterRecord, UUID clusterRecordId) {

		return failWhenEmpty(optionalClusterRecord,
			() -> new UnableToResolvePatronRequest(
				"Unable to find clustered record: " + clusterRecordId));
	}

	private ClusteredBib validateClusteredBib(ClusteredBib clusteredBib) {
		log.debug("validateClusteredBib({})", clusteredBib);

		final var bibs = clusteredBib.getBibs();

		if (bibs == null || bibs.isEmpty()) {
			throw new UnableToResolvePatronRequest("No bibs in clustered bib");
		}

		return clusteredBib;
	}

	private Mono<List<Item>> getItems(ClusteredBib clusteredBib) {
		log.debug("getAvailableItems({})", clusteredBib);

		return liveAvailabilityService.getAvailableItems(clusteredBib);
	}

	private Item chooseFirstRequestableItem(List<Item> items, UUID clusterRecordId) {
		log.debug("chooseFirstRequestableItem({})", items);

		return items.stream()
			.filter(Item::getIsRequestable)
			.filter(item -> ( ( item.getHoldCount() == null ) || ( item.getHoldCount() == 0 ) ) )
			.findFirst()
			.orElseThrow(() -> new NoItemsRequestableAtAnyAgency(clusterRecordId));
	}

	private static SupplierRequest mapToSupplierRequest(Item item,
		PatronRequest patronRequest) {

		log.debug("mapToSupplierRequest({}}, {})", item, patronRequest);

		final var uuid = UUID.randomUUID();
		log.debug("create SR: {}, {}, {}", uuid, item, item.getHostLmsCode());

		log.debug("Resolve the patron request");
		final var updatedPatronRequest = patronRequest.resolve();

		return new SupplierRequest(uuid, updatedPatronRequest,
			item.getId(), item.getBarcode(), item.getLocation().getCode(),
			item.getHostLmsCode(), null, null,
			null);
	}

	private static Resolution resolveToNoItemsAvailable(PatronRequest patronRequest) {
		return new Resolution(patronRequest.resolveToNoItemsAvailable(), Optional.empty());
	}

	private static Resolution mapToResolution(SupplierRequest supplierRequest) {
		return new Resolution(supplierRequest.getPatronRequest(),
			Optional.of(supplierRequest));
	}
}
