package org.olf.reshare.dcb.request.resolution;

import static org.olf.reshare.dcb.item.availability.AvailabilityReport.emptyReport;
import static org.olf.reshare.dcb.request.fulfilment.SupplierRequestStatusCode.PENDING;
import static org.olf.reshare.dcb.utils.PublisherErrors.failWhenEmpty;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.olf.reshare.dcb.core.model.Item;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.item.availability.AvailabilityReport;
import org.olf.reshare.dcb.item.availability.LiveAvailability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
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

		return liveAvailabilityService.getAvailableItems(clusteredBib)
			.switchIfEmpty(Mono.just(emptyReport()))
			.map(AvailabilityReport::getItems);
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

		final var supplierRequestId = UUID.randomUUID();

		log.debug("create SR: {}, {}, {}", supplierRequestId, item, item.getHostLmsCode());

		final var updatedPatronRequest = patronRequest.resolve();

		return SupplierRequest.builder()
			.id(supplierRequestId)
			.patronRequest(updatedPatronRequest)
			.localItemId(item.getId())
			.localItemBarcode(item.getBarcode())
			.localItemLocationCode(item.getLocation().getCode())
			.hostLmsCode(item.getHostLmsCode())
			.statusCode(PENDING)
			.build();
	}

	private static Resolution resolveToNoItemsAvailable(PatronRequest patronRequest) {
		return new Resolution(patronRequest.resolveToNoItemsAvailable(), Optional.empty());
	}

	private static Resolution mapToResolution(SupplierRequest supplierRequest) {
		return new Resolution(supplierRequest.getPatronRequest(),
			Optional.of(supplierRequest));
	}
}
