package org.olf.dcb.request.resolution;

import static io.micronaut.core.util.CollectionUtils.isEmpty;
import static org.olf.dcb.item.availability.AvailabilityReport.emptyReport;
import static org.olf.dcb.request.fulfilment.SupplierRequestStatusCode.PENDING;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.ReferenceValueMappingRepository;
import org.olf.dcb.item.availability.AvailabilityReport;
import org.olf.dcb.item.availability.LiveAvailabilityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;


import io.micronaut.context.annotation.Value;


@Singleton
public class PatronRequestResolutionService {
	private static final Logger log = LoggerFactory.getLogger(PatronRequestResolutionService.class);

	private final SharedIndexService sharedIndexService;
	private final LiveAvailabilityService liveAvailabilityService;
	private final ReferenceValueMappingRepository referenceValueMappingRepository;
	private final AgencyRepository agencyRepository;
        private final List<ResolutionStrategy> allResolutionStrategies;
	private String itemResolver = null;

	public PatronRequestResolutionService(SharedIndexService sharedIndexService,
		ReferenceValueMappingRepository referenceValueMappingRepository,
		AgencyRepository agencyRepository,
		LiveAvailabilityService liveAvailabilityService,
                @Value("${dcb.itemResolver.code}") String itemResolver,
                List<ResolutionStrategy> allResolutionStrategies) {

		this.sharedIndexService = sharedIndexService;
		this.referenceValueMappingRepository = referenceValueMappingRepository;
		this.agencyRepository = agencyRepository;
		this.liveAvailabilityService = liveAvailabilityService;
		this.itemResolver = itemResolver;
		this.allResolutionStrategies = allResolutionStrategies;


                log.debug("Available item resolver strategies (selected={})",this.itemResolver);
                for (ResolutionStrategy t : allResolutionStrategies) {
                        log.debug(t.getClass().getName());
                }

	}

	public Mono<Resolution> resolvePatronRequest(PatronRequest patronRequest) {
		log.debug("resolvePatronRequest(id={}) current status ={} resolver={}", patronRequest.getId(),patronRequest.getStatus(),itemResolver);

		final var clusterRecordId = patronRequest.getBibClusterId();

		// final var resolutionStrategy = new FirstRequestableItemResolutionStrategy();
		final var resolutionStrategy = allResolutionStrategies.stream()
			.filter( strat -> strat.getCode().equals(this.itemResolver))
			.findFirst()
			.get();

		if ( resolutionStrategy==null )
			throw new RuntimeException("No resolver with code "+this.itemResolver);


		return findClusterRecord(clusterRecordId)
			.map(this::validateClusteredBib)
			.flatMap(this::getItems)
			.map(items -> resolutionStrategy.chooseItem(items, clusterRecordId, patronRequest))
			.doOnNext(item -> log.debug("Selected item {}",item))
			.flatMap(item -> createSupplierRequest(item, patronRequest))
			.map(PatronRequestResolutionService::mapToResolution)
			.onErrorReturn(NoItemsRequestableAtAnyAgency.class, resolveToNoItemsAvailable(patronRequest));
	}

	private Mono<ClusteredBib> findClusterRecord(UUID clusterRecordId) {
		return sharedIndexService.findClusteredBib(clusterRecordId);
	}

	private ClusteredBib validateClusteredBib(ClusteredBib clusteredBib) {
		log.debug("validateClusteredBib({})", clusteredBib);

		final var bibs = clusteredBib.getBibs();

		if (isEmpty(bibs)) {
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

	private Mono<SupplierRequest> createSupplierRequest(Item item, PatronRequest patronRequest) {
		log.debug("createSupplierRequest() current pr status = {}",patronRequest.getStatus());
		return resolveSupplyingAgency(item)
			.map(agency -> mapToSupplierRequest(item, patronRequest, agency))
			// This is fugly - it happens because some of the tests don't care about the agency being real
			.defaultIfEmpty( mapToSupplierRequest(item, patronRequest, null));
	}

	private Mono<DataAgency> resolveSupplyingAgency(Item item) {

                log.debug("Attempting to resolveSupplyingAgency(hostSystem={},shelvingLocation={})",item.getHostLmsCode().trim(), item.getLocation().getCode().trim());

                return Mono.from(referenceValueMappingRepository.findOneByFromCategoryAndFromContextAndFromValueAndToCategoryAndToContext(
                                                "ShelvingLocation", item.getHostLmsCode().trim(), item.getLocation().getCode().trim(), "AGENCY", "DCB"))
                                .map(mapping -> mapping.getToValue() )
                                .doOnSuccess(mapping -> log.debug("Result from getting agency for shelving location: {}", mapping))
				.flatMap(agencyCode -> Mono.from(agencyRepository.findOneByCode(agencyCode)) )
                                .doOnSuccess(agency -> log.debug("Located agency record {}",agency));
	}


	// Right now we assume that this is always the first supplier we are talking to.. In the future we need to
	// be able to handle a supplier failing to deliver and creating a new request for a different supplier.
	// isActive is intended to identify the "Current" supplier as we try different agencies.
	private static SupplierRequest mapToSupplierRequest(Item item, PatronRequest patronRequest, DataAgency agency) {

		log.debug("mapToSupplierRequest({}}, {}, {})", item, patronRequest,agency);

                if ( agency == null )
                        log.error("\n\n** NO AGENCY ATTEMPTING TO MAP SUPPLIER REQUEST - The SupplierRequest localAgency will be null - this is test only behaviour**\n\n");

		final var supplierRequestId = UUID.randomUUID();

		log.debug("create SupplierRequest: {}, {}, {}", supplierRequestId, item, item.getHostLmsCode());

		final var updatedPatronRequest = patronRequest.resolve();

		return SupplierRequest.builder()
			.id(supplierRequestId)
			.patronRequest(updatedPatronRequest)
			.localItemId(item.getId())
			.localBibId(item.getBibId())
			.localItemBarcode(item.getBarcode())
			.localItemLocationCode(item.getLocation().getCode())
			.localItemType(item.getLocalItemType())
			.canonicalItemType(item.getCanonicalItemType())
			.hostLmsCode(item.getHostLmsCode())
                        .localAgency( agency != null ? agency.getCode() : null )
			.statusCode(PENDING)
			.isActive(Boolean.TRUE)
			.resolvedAgency(agency)
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
