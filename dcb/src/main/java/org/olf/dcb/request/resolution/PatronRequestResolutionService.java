package org.olf.dcb.request.resolution;

import static org.olf.dcb.request.resolution.Resolution.noItemsSelectable;

import java.util.List;
import java.util.UUID;

import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronRequest;
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

		final var resolutionStrategy = allResolutionStrategies.stream()
			.filter(strategy -> strategy.getCode().equals(this.itemResolver))
			.findFirst()
			.orElseThrow(() -> new RuntimeException("No resolver with code " + this.itemResolver));

		// ToDo ROTA : Filter the list by any suppliers we have already tried for this request
		return Mono.just(Resolution.forPatronRequest(patronRequest))
			.zipWhen(this::getAvailableItems, Resolution::trackAllItems)
			.map(this::filterItems)
			.zipWhen(resolution -> selectItem(resolutionStrategy, resolution), Resolution::selectItem)
			.doOnError(error -> log.warn(
				"There was an error in the liveAvailabilityService.getAvailableItems stream : {}", error.getMessage()))
			.switchIfEmpty(Mono.defer(() -> Mono.just(noItemsSelectable(patronRequest))));
	}

	private Mono<List<Item>> getAvailableItems(Resolution resolution) {
		return getAvailableItems(resolution.getBibClusterId());
	}

	private Mono<List<Item>> getAvailableItems(UUID clusterRecordId) {
		return liveAvailabilityService.checkAvailability(clusterRecordId)
			.onErrorMap(NoBibsForClusterRecordException.class, error -> new UnableToResolvePatronRequest(error.getMessage()))
			.map(AvailabilityReport::getItems);
	}

	private static Mono<Item> selectItem(ResolutionStrategy resolutionStrategy,
		Resolution resolution) {

		return resolutionStrategy.chooseItem(resolution.getFilteredItems(),
			resolution.getBibClusterId(), resolution.getPatronRequest())
			.doOnNext(item -> log.debug("Selected item {}", item));
	}

	private Resolution filterItems(Resolution resolution) {
		return resolution.trackFilteredItems(excludeItemsWithNoHostLmsOrAgency(
			resolution.getAllItems()));
	}

	private static List<Item> excludeItemsWithNoHostLmsOrAgency(List<Item> items) {
		return items.stream()
			.filter(item -> item.getHostLms() != null)
			.toList();
	}
}
