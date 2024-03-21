package org.olf.dcb.request.resolution;

import static org.olf.dcb.request.fulfilment.SupplierRequestStatusCode.PENDING;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
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
	private final LocationToAgencyMappingService locationToAgencyMappingService;

	private final List<ResolutionStrategy> allResolutionStrategies;
	private final String itemResolver;

	public PatronRequestResolutionService(LiveAvailabilityService liveAvailabilityService,
		LocationToAgencyMappingService locationToAgencyMappingService,
		@Value("${dcb.itemresolver.code}") String itemResolver,
		List<ResolutionStrategy> allResolutionStrategies) {

		this.liveAvailabilityService = liveAvailabilityService;
		this.locationToAgencyMappingService = locationToAgencyMappingService;
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
				log.error("Something went wrong with liveAvailabilityService.getAvailableItems",error);
				return new UnableToResolvePatronRequest(error.getMessage());
			})
			// ToDo ROTA : Filter the list by any suppliers we have already tried for this request
			.map(AvailabilityReport::getItems)
			.map(this::excludeItemsWithoutAgency)
			.flatMap(items -> resolutionStrategy.chooseItem(items, clusterRecordId, patronRequest))
			.doOnNext(item -> log.debug("Selected item {}", item))
			.flatMap(item -> createSupplierRequest(item, patronRequest))
			.map(PatronRequestResolutionService::mapToResolution)
			// pretty sure this onErrorReturn is being evaluated eagerly and is updating the patron request regardless of the 
			// presence of an error. If there was an error, the stream should be empty and the case should be caught by the
			// switchIfEmpty, so trying without the explicitOnErrorReturn for now
			// .onErrorReturn(NoItemsRequestableAtAnyAgency.class, resolveToNoItemsAvailable(patronRequest))
			.doOnError( error -> log.warn(
				"There was an error in the liveAvailabilityService.getAvailableItems stream : {}", error.getMessage()))
			.onErrorResume(NoItemsRequestableAtAnyAgency.class,
				error -> Mono.defer(() -> Mono.just(resolveToNoItemsAvailable(patronRequest,error))))
			.switchIfEmpty(Mono.defer(() -> Mono.just(resolveToNoItemsAvailable(patronRequest))));
	}

	private List<Item> excludeItemsWithoutAgency(List<Item> items) {
		return items.stream()
			.filter(item -> item.getAgency() != null)
			.toList();
	}

	private Mono<SupplierRequest> createSupplierRequest(Item item, PatronRequest patronRequest) {
		log.debug("createSupplierRequest() current pr status = {}",patronRequest.getStatus());
		return resolveSupplyingAgency(item)
			.map(agency -> mapToSupplierRequest(item, patronRequest, agency))
			// This is fugly - it happens because some of the tests don't care about the agency being real
			.switchIfEmpty(Mono.fromCallable(() -> mapToSupplierRequest(item, patronRequest, null)));
	}

	private Mono<DataAgency> resolveSupplyingAgency(Item item) {
		final var host_lms_code = item.getHostLmsCode().trim();
		final var shelving_location = item.getLocation().getCode().trim();

		log.debug("Attempting to resolveSupplyingAgency(hostSystem={},shelvingLocation={})",
			host_lms_code, shelving_location);

		return locationToAgencyMappingService.mapLocationToAgency(host_lms_code, shelving_location);
	}

	// Right now we assume that this is always the first supplier we are talking to.. In the future we need to
	// be able to handle a supplier failing to deliver and creating a new request for a different supplier.
	// isActive is intended to identify the "Current" supplier as we try different agencies.
	private static SupplierRequest mapToSupplierRequest(Item item,
		PatronRequest patronRequest, DataAgency agency) {

		log.debug("mapToSupplierRequest({}, {}, {})", item, patronRequest,agency);

		if (agency == null)
			log.error("\n\n** NO AGENCY ATTEMPTING TO MAP SUPPLIER REQUEST - The SupplierRequest localAgency will be null - this is test only behaviour**\n\n");

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
			// ToDo - This no longer holds true - we need to find the hostLMS attached to the supplying agency and use that
			// instead - as the item may have come from a different HostLMS
			.hostLmsCode(item.getHostLmsCode())
			.localAgency(agency != null ? agency.getCode() : null)
			.statusCode(PENDING)
			.isActive(Boolean.TRUE)
			.resolvedAgency(agency)
			.build();
	}

	private static Resolution resolveToNoItemsAvailable(PatronRequest patronRequest, Throwable reason) {
		log.error("PatronRequestResolutionService::resolveToNoItemsAvailable called because", reason);
		return new Resolution(patronRequest.resolveToNoItemsAvailable(), Optional.empty());
	}

	private static Resolution resolveToNoItemsAvailable(PatronRequest patronRequest) {
		log.debug("PatronRequestResolutionService::resolveToNoItemsAvailable called");
		return new Resolution(patronRequest.resolveToNoItemsAvailable(), Optional.empty());
	}

	private static Resolution mapToResolution(SupplierRequest supplierRequest) {
		return new Resolution(supplierRequest.getPatronRequest(),
			Optional.of(supplierRequest));
	}
}
