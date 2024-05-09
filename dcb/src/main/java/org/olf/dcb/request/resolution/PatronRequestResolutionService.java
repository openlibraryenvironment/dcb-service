package org.olf.dcb.request.resolution;

import static org.olf.dcb.request.fulfilment.SupplierRequestStatusCode.PENDING;
import static org.olf.dcb.request.resolution.Resolution.resolveToNoItemsSelectable;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.item.availability.AvailabilityReport;
import org.olf.dcb.item.availability.LiveAvailabilityService;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;


@Slf4j
@Singleton
public class PatronRequestResolutionService {
	private final LiveAvailabilityService liveAvailabilityService;
	private final List<ResolutionStrategy> allResolutionStrategies;
	private final String itemResolver;

	public PatronRequestResolutionService(LiveAvailabilityService liveAvailabilityService,
		@Value("${dcb.itemresolver.code}") String itemResolver,
		List<ResolutionStrategy> allResolutionStrategies) {

		this.liveAvailabilityService = liveAvailabilityService;
		this.itemResolver = itemResolver;
		this.allResolutionStrategies = allResolutionStrategies;

		log.debug("Available item resolver strategies (selected={})", this.itemResolver);

		for (ResolutionStrategy t : allResolutionStrategies) {
			log.debug(t.getClass().getName());
		}
	}

	public Mono<Resolution> resolvePatronRequest(PatronRequest patronRequest) {
		log.debug("resolvePatronRequest(id={}) current status ={} resolver={}",
			patronRequest.getId(), patronRequest.getStatus(), itemResolver);

		final var clusterRecordId = patronRequest.getBibClusterId();

		final var resolutionStrategy = allResolutionStrategies.stream()
			.filter(strategy -> strategy.getCode().equals(this.itemResolver))
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No resolver with code " + this.itemResolver));

		return liveAvailabilityService.checkAvailability(clusterRecordId)
			.onErrorMap(NoBibsForClusterRecordException.class, error -> {
				log.error("Something went wrong with liveAvailabilityService.getAvailableItems", error);
				return new UnableToResolvePatronRequest(error.getMessage());
			})
			// ToDo ROTA : Filter the list by any suppliers we have already tried for this request
			.map(AvailabilityReport::getItems)
			.map(this::excludeItemsWithoutAgencyOrHostLms)
			.flatMap(items -> resolutionStrategy.chooseItem(items, clusterRecordId, patronRequest))
			.doOnNext(item -> log.debug("Selected item {}", item))
			.map(item -> mapToSupplierRequest(item, patronRequest))
			.map(Resolution::resolveToChosenItem)
			.doOnError(error -> log.warn(
				"There was an error in the liveAvailabilityService.getAvailableItems stream : {}", error.getMessage()))
			.switchIfEmpty(Mono.defer(() -> Mono.just(resolveToNoItemsSelectable(patronRequest))));
	}

	private List<Item> excludeItemsWithoutAgencyOrHostLms(List<Item> items) {
		return items.stream()
			.filter(item -> item.getHostLms() != null)
			.toList();
	}

	// Right now we assume that this is always the first supplier we are talking to.. In the future we need to
	// be able to handle a supplier failing to deliver and creating a new request for a different supplier.
	// isActive is intended to identify the "Current" supplier as we try different agencies.
	private static SupplierRequest mapToSupplierRequest(Item item, PatronRequest patronRequest) {
		log.debug("mapToSupplierRequest({}, {})", item, patronRequest);

		final var supplierRequestId = UUID.randomUUID();

		log.debug("create SupplierRequest: {}, {}, {}", supplierRequestId, item, item.getHostLmsCode());

		final var updatedPatronRequest = patronRequest.resolve();

		return SupplierRequest.builder()
			.id(supplierRequestId)
			.patronRequest(updatedPatronRequest)
			.localItemId(item.getLocalId())
			.localBibId(item.getLocalBibId())
			.localItemBarcode(item.getBarcode())
			.localItemLocationCode(item.getLocation().getCode())
			.localItemType(item.getLocalItemType())
			.canonicalItemType(item.getCanonicalItemType())
			.hostLmsCode(item.getHostLmsCode())
			.localAgency(item.getAgencyCode())
			.statusCode(PENDING)
			.isActive(true)
			.resolvedAgency(item.getAgency())
			.build();
	}
}
