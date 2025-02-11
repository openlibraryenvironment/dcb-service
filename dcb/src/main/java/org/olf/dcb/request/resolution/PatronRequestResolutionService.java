package org.olf.dcb.request.resolution;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.olf.dcb.core.ConsortiumService;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.*;
import org.olf.dcb.item.availability.AvailabilityReport;
import org.olf.dcb.item.availability.LiveAvailabilityService;
import org.zalando.problem.Problem;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.olf.dcb.core.interaction.shared.NumericItemTypeMapper.*;
import static org.olf.dcb.core.model.FunctionalSettingType.OWN_LIBRARY_BORROWING;
import static org.olf.dcb.core.model.FunctionalSettingType.SELECT_UNAVAILABLE_ITEMS;
import static org.olf.dcb.request.resolution.Resolution.noItemsSelectable;
import static org.olf.dcb.request.resolution.ResolutionSortOrder.CODE_AVAILABILITY_DATE;
import static org.olf.dcb.request.resolution.ResolutionStep.applyOperationOnCondition;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static services.k_int.utils.ReactorUtils.raiseError;

@Slf4j
@Singleton
public class PatronRequestResolutionService {
	private final LiveAvailabilityService liveAvailabilityService;
	private final List<ResolutionSortOrder> allResolutionStrategies;
	private final String itemResolver;
	private final HostLmsService hostLmsService;
	private final ManualSelection manualSelection;
	private final ConsortiumService consortiumService;

	public PatronRequestResolutionService(LiveAvailabilityService liveAvailabilityService,
		@Value("${dcb.itemresolver.code:}") @Nullable String itemResolver,
		List<ResolutionSortOrder> allResolutionStrategies,
		HostLmsService hostLmsService,
		ManualSelection manualSelection, ConsortiumService consortiumService)
	{
		this.liveAvailabilityService = liveAvailabilityService;
		this.itemResolver = itemResolver;
		this.allResolutionStrategies = allResolutionStrategies;
		this.hostLmsService = hostLmsService;
		this.manualSelection = manualSelection;
		this.consortiumService = consortiumService;

		log.debug("Available item resolver strategies (selected={})", this.itemResolver);

		for (ResolutionSortOrder t : allResolutionStrategies) {
			log.debug(t.getClass().getName());
		}
	}

	public Mono<Resolution> resolvePatronRequest(PatronRequest patronRequest) {
		return resolvePatronRequest(patronRequest, null);
	}

	public Mono<Resolution> resolvePatronRequest(PatronRequest patronRequest,
		@Nullable String excludedAgencyCode) {

		log.debug("resolvePatronRequest(id={}) current status ={} resolver={}",
			patronRequest.getId(), patronRequest.getStatus(), itemResolver);

		final var resolutionSteps = patronRequest.getIsManuallySelectedItem()
			? manualResolutionSteps()
			: specifiedResolutionSteps();

		return Mono.just(Resolution.forPatronRequest(patronRequest))
			.map(this::borrowingAgency)
			.map(resolution -> resolution.excludeAgency(excludedAgencyCode))
			.flatMap(initialResolution -> executeSteps(initialResolution, resolutionSteps))
			.doOnError(error -> log.warn(
				"There was an error in the liveAvailabilityService.getAvailableItems stream : {}", error.getMessage()))
			.switchIfEmpty(Mono.defer(() -> Mono.just(noItemsSelectable(patronRequest))));
	}

	private Resolution borrowingAgency(Resolution resolution) {
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

		return resolution.borrowingAgency(borrowingAgencyCode);
	}

	private List<ResolutionStep> manualResolutionSteps() {
		return List.of(
			new ResolutionStep("Get Available Items", this::getAvailableItems),
			new ResolutionStep("Filter Items", this::filterItems),
			new ResolutionStep("Manual Selection", this::handleManualSelection)
		);
	}

	private List<ResolutionStep> specifiedResolutionSteps() {
		return List.of(
			new ResolutionStep("Get Available Items", this::getAvailableItems),
			new ResolutionStep("Filter Items", this::filterItems),
			new ResolutionStep("Sort Items By Availability Date", this::sortItemsByAvailabilityDate),
			new ResolutionStep("Sort Items By tie breaker Strategy", this::sortByTieBreakerStrategy, isTieBreaker()),
			new ResolutionStep("Select First Requestable Item", this::firstRequestableItem)
			);
	}

	/**
	 * Checks if the resolution is a tie breaker.
	 * A tie breaker is when at least the first two items in the sorted list have the same available date.
	 *
	 * @return true if the resolution is a tie breaker, false otherwise
	 */
	private static Function<Resolution, Boolean> isTieBreaker() {
		return resolution -> {

			final var sortedItems = resolution.getSortedItems();

			if (sortedItems == null || sortedItems.size() < 2) {
				return false;
			}

			return Objects.equals(sortedItems.get(0).getAvailableDate(), sortedItems.get(1).getAvailableDate());
		};
	}

	private Mono<Resolution> executeSteps(Resolution initialResolution, List<ResolutionStep> steps) {
		return Flux.fromIterable(steps)
			.reduce(Mono.just(initialResolution), this::applyStep)
			.flatMap(Function.identity());
	}

	private Mono<Resolution> applyStep(Mono<Resolution> monoResolution, ResolutionStep step) {
		return monoResolution.flatMap(resolution -> applyOperationOnCondition(step, resolution));
	}

	private Mono<Resolution> handleManualSelection(Resolution resolution) {
		return Mono.justOrEmpty(manualSelection.chooseItem(resolution))
			.map(resolution::selectItem);
	}

	private Mono<Resolution> sortItemsByAvailabilityDate(Resolution resolution) {
		return applySortingStrategy(CODE_AVAILABILITY_DATE, resolution);
	}

	private Mono<Resolution> sortByTieBreakerStrategy(Resolution resolution) {
		final var sortedItems = resolution.getSortedItems();
		final var firstAvailableDate = sortedItems.get(0).getAvailableDate();
		final var sameAvailabilityDateItems = sortedItems.stream()
			.filter(item -> Objects.equals(item.getAvailableDate(), firstAvailableDate))
			.collect(Collectors.toList());

		log.debug("Items with same availability date as the first item: {}", sameAvailabilityDateItems.size());

		resolution.trackSortedItems(sameAvailabilityDateItems);

		return applySortingStrategy(itemResolver, resolution);
	}

	// Only apply the sorting strategy if it is a valid strategy code
	private Mono<Resolution> applySortingStrategy(String code, Resolution resolution) {
		return Mono.justOrEmpty(code)
			.flatMap(codeParam -> allResolutionStrategies.stream()
				.filter(strategy -> strategy.getCode().equals(codeParam))
				.findFirst()
				.map(strategy -> sortItems(strategy, resolution))
				.orElse(Mono.empty())
			)
			.switchIfEmpty(Mono.defer(() -> Mono.just(noStrategyToApply(resolution, code))));
	}

	private static Resolution noStrategyToApply(Resolution resolution, String code) {
		log.warn("Sorting strategy code [{}] could not be applied", code);
		return resolution;
	}

	public Mono<Resolution> firstRequestableItem(Resolution resolution) {
		final var itemList = getItemList(resolution);

		log.debug("Selecting first requestable item from item list size {}", itemList.size());

		return Mono.justOrEmpty(
			itemList.stream()
				.findFirst())
			.map(resolution::selectItem);
	}

	private Mono<Resolution> getAvailableItems(Resolution resolution) {
		return Mono.justOrEmpty(resolution.getBibClusterId())
			.flatMap(liveAvailabilityService::checkAvailability)
			.onErrorMap(NoBibsForClusterRecordException.class,
				error -> new UnableToResolvePatronRequest(error.getMessage()))
			.map(AvailabilityReport::getItems)
			.map(resolution::trackAllItems);
	}

	private Mono<Resolution> sortItems(ResolutionSortOrder resolutionSortOrder,
		Resolution resolution) {

		final var listToSort = getItemList(resolution);

		return resolutionSortOrder.sortItems(listToSort,
			resolution.getBibClusterId(), resolution.getPatronRequest())
			.doOnNext(items -> {
				if (items != null && !items.isEmpty()) {
					log.debug("First item in sorted list is: {}", items.get(0));
				}
			})
			.map(resolution::trackSortedItems);
	}

	// Prioritise the sorted list over the filtered list
	private static List<Item> getItemList(Resolution resolution) {

		return resolution.getSortedItems() != null && !resolution.getSortedItems().isEmpty()
			? resolution.getSortedItems()
			: resolution.getFilteredItems();
	}

	private Mono<Resolution> filterItems(Resolution resolution) {
		final var patronRequest = getValueOrNull(resolution, Resolution::getPatronRequest);
		final var excludedAgencyCode = getValueOrNull(resolution, Resolution::getExcludedAgencyCode);
		final var borrowingAgencyCode = getValueOrNull(resolution, Resolution::getBorrowingAgencyCode);

		final var allItems = resolution.getAllItems();

		return Flux.fromIterable(allItems)
			.filterWhen(item -> excludeItemFromSameAgency(item, borrowingAgencyCode))
			.filter(item -> excludeItemFromPreviouslyResolvedAgency(item, excludedAgencyCode))
			.filter(Item::getIsRequestable)
			.filterWhen(this::includeItemWithHolds)
			.filterWhen(item -> fromSameServer(item, patronRequest))
			.collectList()
			.map(resolution::trackFilteredItems);
	}

	/**
	 * Checks if an item with holds should be included in the resolution process.
	 *
	 * This method checks the consortium's functional setting for selecting unavailable items.
	 * If the setting is enabled, items with holds are included. Otherwise, only items with no holds are included.
	 *
	 * @param item the item to check
	 * @return true if the item should be included, false otherwise
	 */
	private Mono<Boolean> includeItemWithHolds(Item item) {
		return consortiumService.isEnabled(SELECT_UNAVAILABLE_ITEMS)
			.map(enabled -> {
				final boolean includeItem = enabled || item.hasNoHolds();

				log.debug("Include item with holds: enabled={}, item.hasNoHolds={}, includeItem={}",
					enabled, item.hasNoHolds(), includeItem);

				return includeItem;
			});
	}

	/**
	 * Checks if an item should be excluded from the resolution process because it belongs to an excluded agency.
	 *
	 * This method checks if the item's agency code matches the excluded agency code from the first supplier request.
	 * If the item's agency code matches, it is excluded from the resolution process.
	 *
	 * @param item the item to check
	 * @param excludedAgencyCode code of the agency to exclude items from
	 * @return true if the item should be included in the resolution process, false otherwise
	 */
	private boolean excludeItemFromPreviouslyResolvedAgency(Item item, String excludedAgencyCode) {
		if (excludedAgencyCode == null) {
			return true;
		}

		// Check if the item's agency code matches the excluded agency code
		return Optional.ofNullable(item) // if the item is present
			.map(Item::getAgencyCode) // and the item has an agency code
			.filter(itemAgencyCode -> itemAgencyCode.equals(excludedAgencyCode)) // and the agency code is the same
			.isEmpty(); // our conditions didn't match so include the item
	}

	Mono<Boolean> excludeItemFromSameAgency(Item item, String borrowingAgencyCode) {
		return consortiumService.isEnabled(OWN_LIBRARY_BORROWING)
			.map(enabled -> {
				if (enabled) {
					return true;
				}

				final var itemAgencyCode = getValueOrNull(item, Item::getAgencyCode);

				return itemAgencyCode != null && !itemAgencyCode.equals(borrowingAgencyCode);
			});
	}

	/**
	 * Determines if an item should be excluded based on server configuration comparison.
	 * Returns true if the item should be kept, false if it should be excluded.
	 *
	 * @param item the item to check
	 * @param patronRequest the property to compare against
	 * @return Mono<Boolean> indicating if the item should be kept
	 */

	Mono<Boolean> fromSameServer(Item item, PatronRequest patronRequest) {
		final var itemLmsCode = getValueOrNull(item, Item::getHostLmsCode);
		final var borrowingLmsCode = getValueOrNull(patronRequest, PatronRequest::getPatronHostlmsCode);

		if (itemLmsCode == null || borrowingLmsCode == null) {
			return raiseError(Problem.builder()
				.withTitle("Missing required value to evaluate item fromSameServer")
				.withDetail("Could not compare LMS codes")
				.with("itemLmsCode", itemLmsCode)
				.with("borrowingLmsCode", borrowingLmsCode)
				.build());
		}

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
