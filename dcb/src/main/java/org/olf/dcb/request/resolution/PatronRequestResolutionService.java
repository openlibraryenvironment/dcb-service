package org.olf.dcb.request.resolution;

import static java.lang.Boolean.TRUE;
import static org.olf.dcb.core.interaction.shared.NumericItemTypeMapper.*;
import static org.olf.dcb.request.resolution.Resolution.noItemsSelectable;
import static org.olf.dcb.request.resolution.ResolutionStrategy.MANUAL_SELECTION;
import static org.olf.dcb.request.resolution.SupplierRequestService.findFirstSupplierRequestOrNull;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static reactor.function.TupleUtils.function;
import static services.k_int.utils.ReactorUtils.raiseError;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.*;
import org.olf.dcb.item.availability.AvailabilityReport;
import org.olf.dcb.item.availability.LiveAvailabilityService;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.zalando.problem.Problem;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@Slf4j
@Singleton
public class PatronRequestResolutionService {
	private final LiveAvailabilityService liveAvailabilityService;
	private final List<ResolutionStrategy> allResolutionStrategies;
	private final String itemResolver;
	private final HostLmsService hostLmsService;

	public PatronRequestResolutionService(LiveAvailabilityService liveAvailabilityService,
		@Value("${dcb.itemresolver.code}") String itemResolver,
		List<ResolutionStrategy> allResolutionStrategies,
		HostLmsService hostLmsService)
	{
		this.liveAvailabilityService = liveAvailabilityService;
		this.itemResolver = itemResolver;
		this.allResolutionStrategies = allResolutionStrategies;
		this.hostLmsService = hostLmsService;

		log.debug("Available item resolver strategies (selected={})", this.itemResolver);

		for (ResolutionStrategy t : allResolutionStrategies) {
			log.debug(t.getClass().getName());
		}
	}

	public Mono<Resolution> retryResolvePatronRequest(PatronRequest patronRequest) {
		log.info("Re-resolving Patron Request {}", patronRequest.getId());

		return resolvePatronRequest(patronRequest);
	}

	public Mono<Resolution> resolvePatronRequest(PatronRequest patronRequest) {
		log.debug("resolvePatronRequest(id={}) current status ={} resolver={}",
			patronRequest.getId(), patronRequest.getStatus(), itemResolver);

		patronRequest.incrementResolutionCount();

		// ToDo ROTA : Filter the list by any suppliers we have already tried for this request
		return Mono.just(Resolution.forPatronRequest(patronRequest))
			.zipWhen(this::getAvailableItems, Resolution::trackAllItems)
			.flatMap(this::filterItems)
			.flatMap(this::decideResolutionStrategy)
			.flatMap(function(this::applyResolutionStrategy))
			.doOnError(error -> log.warn(
				"There was an error in the liveAvailabilityService.getAvailableItems stream : {}", error.getMessage()))
			.switchIfEmpty(Mono.defer(() -> Mono.just(noItemsSelectable(patronRequest))));
	}

	private Mono<Tuple2<ResolutionStrategy, Resolution>> decideResolutionStrategy(Resolution resolution) {
		final var patronRequest = resolution.getPatronRequest();
		final var code = decideCode(patronRequest);

		return Mono.defer(() -> Mono.fromCallable(() -> getResolutionStrategyBy(code))
			.doOnSuccess(strategy -> log.info("Selecting item by {} for Patron Request {}",
				strategy.getCode(), patronRequest.getId()))
			.single()
			.zipWith(Mono.just(resolution)));
	}

	private String decideCode(PatronRequest patronRequest) {
		log.debug("Deciding strategy code for {}", patronRequest);

		final var isManualSelection = patronRequest.getIsManuallySelectedItem();

		final var chosenStrategy = TRUE.equals(isManualSelection)
			? MANUAL_SELECTION
			: itemResolverFor(patronRequest);

		log.debug("Chosen selection strategy: {}", chosenStrategy);

		return chosenStrategy;
	}

	private String itemResolverFor(PatronRequest patronRequest) {
		// manual selection failed at submission
		// but the item resolver could be set to the manual selection resolver code
		if (MANUAL_SELECTION.equals(itemResolver)) {
			validateManualSelectionFor(patronRequest);
			// manual selection now passed checks so set the pr to manual selection
			patronRequest.setIsManuallySelectedItem(TRUE);
		}

		return itemResolver;
	}

	private void validateManualSelectionFor(PatronRequest patronRequest) {
		if (patronRequest.getLocalItemId() == null) {
			throw new IllegalArgumentException("localItemId is required for manual item selection");
		}
		if (patronRequest.getLocalItemHostlmsCode() == null) {
			throw new IllegalArgumentException("localItemHostlmsCode is required for manual item selection");
		}
		if (patronRequest.getLocalItemAgencyCode() == null) {
			throw new IllegalArgumentException("localItemAgencyCode is required for manual item selection");
		}
	}

	private ResolutionStrategy getResolutionStrategyBy(String code) {
		return allResolutionStrategies.stream()
			.filter(strategy -> strategy.getCode().equals(code))
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No resolver with code " + code));
	}

	private Mono<Resolution> applyResolutionStrategy(ResolutionStrategy strategy, Resolution resolution) {
		return selectItem(strategy, resolution)
			.map(resolution::selectItem);
	}

	private Mono<List<Item>> getAvailableItems(Resolution resolution) {
		return getAvailableItems(resolution.getBibClusterId());
	}

	private Mono<List<Item>> getAvailableItems(UUID clusterRecordId) {
		return liveAvailabilityService.checkAvailability(clusterRecordId)
			.onErrorMap(NoBibsForClusterRecordException.class, error -> new UnableToResolvePatronRequest(error.getMessage()))
			.map(AvailabilityReport::getItems);
	}

	private Mono<Item> selectItem(ResolutionStrategy resolutionStrategy,
		Resolution resolution) {

		return resolutionStrategy.chooseItem(resolution.getFilteredItems(),
			resolution.getBibClusterId(), resolution.getPatronRequest())
			.doOnNext(item -> log.debug("Selected item {}", item));
	}

	private Mono<Resolution> filterItems(Resolution resolution) {
		final var patronRequest = getValueOrNull(resolution, Resolution::getPatronRequest);
		final var patron = getValueOrNull(patronRequest, PatronRequest::getPatron);
		final var optionalHomeIdentity = getValueOrNull(patron, Patron::getHomeIdentity);
		
		if (optionalHomeIdentity.isEmpty()) {
			throw new NoHomeIdentityException(getValueOrNull(patron, Patron::getId),
				getValueOrNull(patron, Patron::getPatronIdentities));
		}

		final var homeIdentity = optionalHomeIdentity.get();

		final var borrowingAgency = getValueOrNull(homeIdentity, PatronIdentity::getResolvedAgency);

		final var borrowingAgencyCode = getValueOrNull(borrowingAgency, DataAgency::getCode);

		if (borrowingAgencyCode == null) {
			log.warn("Borrowing agency code during resolution is null");
		}

		final var allItems = resolution.getAllItems();

		return Flux.fromIterable(allItems)
			.filter(item -> excludeItemFromSameAgency(item, borrowingAgencyCode))
			.filter(item -> excludeItemFromPreviouslyResolvedAgency(item, patronRequest))
			.filter(Item::getIsRequestable)
			.filter(Item::hasNoHolds)
			.filterWhen(item -> fromSameServer(item, patronRequest))
			.collectList()
			.map(resolution::trackFilteredItems);
	}

	/**
	 * Checks if an item should be excluded from the resolution process because it belongs to an excluded agency.
	 *
	 * This method checks if the item's agency code matches the excluded agency code from the first supplier request.
	 * If the item's agency code matches, it is excluded from the resolution process.
	 *
	 * @param item the item to check
	 * @param patronRequest the patron request including the first supplier request and supplier's resolved agency
	 * @return true if the item should be included in the resolution process, false otherwise
	 */
	private boolean excludeItemFromPreviouslyResolvedAgency(Item item, PatronRequest patronRequest) {

		final var resolutionCount = patronRequest.getResolutionCount();
		final var supplierRequests = getValueOrNull(patronRequest, PatronRequest::getSupplierRequests);

		// If the resolution count is more than 1 the patron request is being re-resolved
		if (resolutionCount > 1 && supplierRequests != null) {
			log.debug("Resolution count was more than 1 for Patron Request {}", patronRequest.getId());

			final var firstSupplierRequest = findFirstSupplierRequestOrNull(supplierRequests);

			final var excludedAgencyCode = Optional.of(firstSupplierRequest)
				.map(SupplierRequest::getResolvedAgency)
				.map(DataAgency::getCode)
				.orElse(null);

			if (excludedAgencyCode != null) {
				// Check if the item's agency code matches the excluded agency code
				return Optional.ofNullable(item) // if the item is present
					.map(Item::getAgencyCode) // and the item has an agency code
					.filter(itemAgencyCode -> itemAgencyCode.equals(excludedAgencyCode)) // and the agency code is the same
					.isEmpty(); // our conditions didn't match so include the item
			}
		}

		// If none of the above conditions are met, include the item in the resolution process
		return true;
	}

	private static boolean excludeItemFromSameAgency(Item item, String borrowingAgencyCode) {
		final var itemAgencyCode = getValueOrNull(item, Item::getAgencyCode);

		return itemAgencyCode != null && !itemAgencyCode.equals(borrowingAgencyCode);
	}

	/**
	 * Determines if an item should be excluded based on server configuration comparison.
	 * Returns true if the item should be kept, false if it should be excluded.
	 *
	 * @param item the item to check
	 * @param patronRequest the property to compare against
	 * @return Mono<Boolean> indicating if the item should be kept
	 */
	private Mono<Boolean> fromSameServer(Item item, PatronRequest patronRequest) {
		final var itemLmsCode = getValueOrNull(item, Item::getHostLmsCode);
		final var borrowingLmsCode = getValueOrNull(patronRequest, PatronRequest::getPatronHostlmsCode);

		if (itemLmsCode == null || borrowingLmsCode == null) return raiseError(Problem.builder()
			.withTitle("Missing required value to evaluate item fromSameServer")
			.withDetail("Could not compare LMS codes")
			.with("itemLmsCode", itemLmsCode)
			.with("borrowingLmsCode", borrowingLmsCode)
			.build());

		return Mono.zip(
			hostLmsService.getHostLmsBaseUrl(itemLmsCode),
			hostLmsService.getHostLmsBaseUrl(borrowingLmsCode)
		).map(tuple -> {
			final var itemBaseUrl = getValueOrNull(tuple, Tuple2::getT1);
			final var borrowingBaseUrl = getValueOrNull(tuple, Tuple2::getT2);

			if (itemBaseUrl == null || borrowingBaseUrl == null) {
				throw Problem.builder()
					.withTitle("Missing required value to evaluate item fromSameServer")
					.withDetail("Could not compare base-url")
					.with("itemBaseUrl", itemBaseUrl)
					.with("borrowingBaseUrl", borrowingBaseUrl)
					.build();
			}

			boolean isSameServer = itemBaseUrl.equals(borrowingBaseUrl);
			boolean isDifferentLms = !itemLmsCode.equals(borrowingLmsCode);
			boolean shouldExclude = isSameServer && isDifferentLms;

			if (shouldExclude) {
				log.warn("Excluding item from same server: itemLms={}, borrowingLms={}, baseUrl={}",
					itemLmsCode, borrowingLmsCode, itemBaseUrl);
			}

			return !shouldExclude;
		});
	}

	public static Resolution checkMappedCanonicalItemType(Resolution resolution) {

		final var chosenItem = getValueOrNull(resolution, Resolution::getChosenItem);

		// NO_ITEMS_SELECTABLE_AT_ANY_AGENCY
		if (chosenItem == null) return resolution;

		final var canonicalItemType = getValue(chosenItem, Item::getCanonicalItemType, "null");
		final var localItemType = getValue(chosenItem, Item::getLocalItemType, "null");
		final var owningContext = getValue(chosenItem, Item::getOwningContext, "null");

		return switch (canonicalItemType) {
			case UNKNOWN_NULL_LOCAL_ITEM_TYPE -> throw Problem.builder()
				.withTitle("NumericItemTypeMapper")
				.withDetail("No localItemType provided")
				.with("hostLmsCode", owningContext)
				.build();
			case UNKNOWN_NULL_HOSTLMSCODE -> throw Problem.builder()
				.withTitle("NumericItemTypeMapper")
				.withDetail("No hostLmsCode provided")
				.build();
			case UNKNOWN_INVALID_LOCAL_ITEM_TYPE -> throw Problem.builder()
				.withTitle("NumericItemTypeMapper")
				.withDetail("Problem trying to convert " + localItemType + " into long value")
				.with("hostLmsCode", owningContext)
				.build();
			case UNKNOWN_NO_MAPPING_FOUND -> throw Problem.builder()
				.withTitle("NumericItemTypeMapper")
				.withDetail("No canonical item type found for localItemTypeCode " + localItemType)
				.with("hostLmsCode", owningContext)
				.build();
			case UNKNOWN_UNEXPECTED_FAILURE, "null" -> throw Problem.builder()
				.withTitle("NumericItemTypeMapper")
				.withDetail("Unexpected failure")
				.with("hostLmsCode", owningContext)
				.with("localItemTypeCode", localItemType)
				.build();

			// canonicalItemType looks ok, continue
			default -> resolution;
		};
	}
}
