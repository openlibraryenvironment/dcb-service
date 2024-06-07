package org.olf.dcb.request.resolution;

import static java.lang.Boolean.TRUE;
import static org.olf.dcb.request.resolution.Resolution.noItemsSelectable;
import static org.olf.dcb.request.resolution.ResolutionStrategy.MANUAL_SELECTION;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;
import static reactor.function.TupleUtils.function;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.NoHomeIdentityException;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.item.availability.AvailabilityReport;
import org.olf.dcb.item.availability.LiveAvailabilityService;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;


@Slf4j
@Singleton
public class PatronRequestResolutionService {
	private final LiveAvailabilityService liveAvailabilityService;
	private final List<ResolutionStrategy> allResolutionStrategies;
	private final String itemResolver;

	public PatronRequestResolutionService(LiveAvailabilityService liveAvailabilityService,
		@Value("${dcb.itemresolver.code}") String itemResolver,
		List<ResolutionStrategy> allResolutionStrategies)
	{
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

		// ToDo ROTA : Filter the list by any suppliers we have already tried for this request
		return Mono.just(Resolution.forPatronRequest(patronRequest))
			.zipWhen(this::getAvailableItems, Resolution::trackAllItems)
			.map(this::filterItems)
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

	private Resolution filterItems(Resolution resolution) {
		final var patronRequest = getValue(resolution, Resolution::getPatronRequest);
		final var patron = getValue(patronRequest, PatronRequest::getPatron);
		final var optionalHomeIdentity = getValue(patron, Patron::getHomeIdentity);
		
		if (optionalHomeIdentity.isEmpty()) {
			throw new NoHomeIdentityException(getValue(patron, Patron::getId),
				getValue(patron, Patron::getPatronIdentities));
		}

		final var homeIdentity = optionalHomeIdentity.get();

		final var borrowingAgency = getValue(homeIdentity, PatronIdentity::getResolvedAgency);

		final var borrowingAgencyCode = getValue(borrowingAgency, DataAgency::getCode);

		if (borrowingAgencyCode == null) {
			log.warn("Borrowing agency code during resolution is null");
		}

		final var allItems = resolution.getAllItems();

		final var filteredItems = allItems.stream()
			.filter(item -> excludeItemFromSameAgency(item, borrowingAgencyCode))
			.toList();

		return resolution.trackFilteredItems(filteredItems);
	}

	private static boolean excludeItemFromSameAgency(Item item, String borrowingAgencyCode) {
		final var itemAgencyCode = getValue(item, Item::getAgencyCode);

		return itemAgencyCode != null && !itemAgencyCode.equals(borrowingAgencyCode);
	}
}
