package org.olf.dcb.request.resolution;

import static java.util.Collections.emptyList;
import static org.olf.dcb.core.interaction.shared.NumericItemTypeMapper.UNKNOWN_INVALID_LOCAL_ITEM_TYPE;
import static org.olf.dcb.core.interaction.shared.NumericItemTypeMapper.UNKNOWN_NO_MAPPING_FOUND;
import static org.olf.dcb.core.interaction.shared.NumericItemTypeMapper.UNKNOWN_NULL_HOSTLMSCODE;
import static org.olf.dcb.core.interaction.shared.NumericItemTypeMapper.UNKNOWN_NULL_LOCAL_ITEM_TYPE;
import static org.olf.dcb.core.interaction.shared.NumericItemTypeMapper.UNKNOWN_UNEXPECTED_FAILURE;
import static org.olf.dcb.request.resolution.ResolutionSortOrder.CODE_AVAILABILITY_DATE;
import static org.olf.dcb.request.resolution.ResolutionStep.applyOperationOnCondition;
import static org.olf.dcb.request.workflow.PresentableItem.toPresentableItem;
import static org.olf.dcb.request.workflow.PresentableItem.toPresentableItems;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static reactor.core.publisher.Flux.fromIterable;
import static services.k_int.utils.MapUtils.putNonNullValue;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.item.availability.AvailabilityReport;
import org.olf.dcb.item.availability.LiveAvailabilityService;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.zalando.problem.Problem;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
public class PatronRequestResolutionService {
	private final LiveAvailabilityService liveAvailabilityService;
	private final PatronRequestAuditService patronRequestAuditService;

	private final List<ResolutionSortOrder> allResolutionStrategies;
	private final String itemResolver;
	private final ManualSelection manualSelection;
	private final AllItemFilters allItemFilters;
	private final Duration timeout;

	public PatronRequestResolutionService(LiveAvailabilityService liveAvailabilityService,
		PatronRequestAuditService patronRequestAuditService,
		@Value("${dcb.itemresolver.code:}") @Nullable String itemResolver,
		List<ResolutionSortOrder> allResolutionStrategies, ManualSelection manualSelection,
		AllItemFilters allItemFilters,
		@Value("${dcb.resolution.live-availability.timeout:PT30S}") Duration timeout) {

		this.liveAvailabilityService = liveAvailabilityService;
		this.patronRequestAuditService = patronRequestAuditService;
		this.itemResolver = itemResolver;
		this.allResolutionStrategies = allResolutionStrategies;
		this.manualSelection = manualSelection;
		this.allItemFilters = allItemFilters;
		this.timeout = timeout;

		log.debug("Using live availability timeout of {} during resolution", timeout);
		log.debug("Available item resolver strategies (selected={})", this.itemResolver);

		for (ResolutionSortOrder t : allResolutionStrategies) {
			log.debug(t.getClass().getName());
		}
	}

	public Mono<Resolution> resolve(ResolutionParameters parameters) {
		log.debug("resolve(parameters={}) resolver={}", parameters, itemResolver);

		final var isManuallySelected = getValue(parameters, ResolutionParameters::getManualItemSelection,
			ManualItemSelection::getIsManuallySelected, false);

		final var resolutionSteps = isManuallySelected
			? manualResolutionSteps()
			: specifiedResolutionSteps();

		final var initialResolution = Resolution.forParameters(parameters);

		return Mono.just(initialResolution)
			.flatMap(resolution -> executeSteps(resolution, resolutionSteps))
			.doOnError(error -> log.warn(
				"There was an error in the liveAvailabilityService.getAvailableItems stream : {}", error.getMessage()))
			.defaultIfEmpty(initialResolution);
	}

	public Mono<Resolution> auditResolution(Resolution resolution,
		PatronRequest patronRequest, String processName) {

		final var successful = getValue(resolution, Resolution::successful, false);

		return successful
			? auditSuccessfulResolution(resolution, patronRequest, processName)
			: auditUnsuccessfulResolution(resolution, patronRequest, processName);
	}

	private Mono<Resolution> auditSuccessfulResolution(Resolution resolution,
		PatronRequest patronRequest, String processName) {

		final var auditData = new HashMap<String, Object>();

		final var chosenItem = getValueOrNull(resolution, Resolution::getChosenItem);

		putNonNullValue(auditData, "selectedItem", toPresentableItem(chosenItem));

		addItemCollectionsToAuditData(resolution, auditData);

		return patronRequestAuditService.addAuditEntry(patronRequest,
				formatResolutionAuditMessage(processName, chosenItem), auditData)
			.thenReturn(resolution);
	}

	private static String formatResolutionAuditMessage(String processName, Item chosenItem) {
		final var localId = getValue(chosenItem, Item::getLocalId, "null");
		final var supplyingHostLmsCode = getValue(chosenItem, Item::getHostLmsCode, "null");

		return ("%s selected an item with local ID \"%s\" from Host LMS \"%s\"")
			.formatted(processName, localId, supplyingHostLmsCode);
	}

	private Mono<Resolution> auditUnsuccessfulResolution(Resolution resolution,
		PatronRequest patronRequest, String processName) {

		final var auditData = new HashMap<String, Object>();

		addItemCollectionsToAuditData(resolution, auditData);

		final var message = "%s could not select an item".formatted(processName);

		return patronRequestAuditService.addAuditEntry(patronRequest, message, auditData)
			.thenReturn(resolution);
	}

	private static void addItemCollectionsToAuditData(Resolution resolution,
		HashMap<String, Object> auditData) {

		putNonNullValue(auditData, "filteredItems", toPresentableItems(resolution.getFilteredItems()));
		putNonNullValue(auditData, "sortedItems", toPresentableItems(resolution.getSortedItems()));
		putNonNullValue(auditData, "allItems", toPresentableItems(resolution.getAllItems()));
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
	 * @return resolution and true if the resolution is a tie breaker, false otherwise
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
		return fromIterable(steps)
			.reduce(Mono.just(initialResolution), this::applyStep)
			.flatMap(Function.identity());
	}

	private Mono<Resolution> applyStep(Mono<Resolution> monoResolution, ResolutionStep step) {
		return monoResolution.flatMap(resolution -> applyOperationOnCondition(step, resolution));
	}

	private Mono<Resolution> handleManualSelection(Resolution resolution) {
		final var itemSelection = getValueOrNull(resolution, Resolution::getParameters,
			ResolutionParameters::getManualItemSelection);

		final List<Item> items = getValue(resolution, Resolution::getFilteredItems, emptyList());

		return Mono.justOrEmpty(manualSelection.chooseItem(items, itemSelection))
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

		// If there are no items to select from, return the resolution as is (so no chosen item)
		if (itemList.isEmpty()) {
			return Mono.just(resolution);
		}

		return Mono.just(resolution.selectItem(itemList.stream().findFirst().orElseThrow()));
	}

	private Mono<Resolution> getAvailableItems(Resolution resolution) {
		return Mono.justOrEmpty(resolution.getBibClusterId())
			.flatMap(this::checkAvailability)
			.onErrorMap(NoBibsForClusterRecordException.class,
				error -> new UnableToResolvePatronRequest(error.getMessage()))
			.map(AvailabilityReport::getItems)
			.map(resolution::trackAllItems);
	}

	private Mono<AvailabilityReport> checkAvailability(UUID clusteredBibId) {
		return liveAvailabilityService.checkAvailabilityNoCache(clusteredBibId, Optional.of(timeout));
	}

	private Mono<Resolution> sortItems(ResolutionSortOrder sortOrder, Resolution resolution) {
		final var sortParameters = ResolutionSortOrder.Parameters.builder()
			.items(getItemList(resolution))
			.pickupLocationCode(getValueOrNull(resolution, Resolution::getParameters,
				ResolutionParameters::getPickupLocationCode))
			.build();

		return sortOrder.sortItems(sortParameters)
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
		final List<Item> allItems = getValue(resolution, Resolution::getAllItems, emptyList());

		log.debug("All items from live availability: {}", allItems);

		return allItemFilters.filterItems(fromIterable(allItems), resolution)
			.map(resolution::trackFilteredItems);
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
