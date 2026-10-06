package org.olf.dcb.request.resolution;

import java.util.List;
import java.util.function.Function;

import org.olf.dcb.core.model.Item;
import org.reactivestreams.Publisher;

import io.micronaut.context.annotation.Primary;
import jakarta.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

@Slf4j
@Primary
@Singleton
@AllArgsConstructor
public class AllItemFilters implements ItemFilter {
	// Acts as a composite (https://guides.micronaut.io/latest/micronaut-patterns-composite-maven-java.html)
	private final List<ItemFilter> itemFilters;

	/**
	 * Partitions items, recording on each excluded item the filter that excluded it.
	 * <p>
	 * The predicate below answers only yes or no, so a resolution that ended with nothing could
	 * not say which of six filters emptied it. Filters run in their declared order and the first
	 * to reject an item is the one named: later filters are not consulted, exactly as before.
	 */
	public Mono<FilterOutcome> partition(List<Item> items, ItemFilterParameters parameters) {
		return Flux.fromIterable(items)
			.concatMap(item -> firstRejectingFilter(item, parameters)
				.map(filter -> excluded(item, filter))
				.map(Either::excluded)
				.defaultIfEmpty(Either.included(item)))
			.collectList()
			.map(FilterOutcome::of);
	}

	private Mono<ItemFilter> firstRejectingFilter(Item item, ItemFilterParameters parameters) {
		return Flux.fromIterable(itemFilters)
			.concatMap(filter -> Mono.from(filter.filterItem(parameters).apply(item))
				.defaultIfEmpty(false)
				.filter(included -> !included)
				.map(rejected -> filter))
			.next();
	}

	private static Item excluded(Item item, ItemFilter filter) {
		return item.toBuilder()
			.decisionLogEntry("Excluded by " + filter.getName())
			.build();
	}

	public record FilterOutcome(List<Item> included, List<Item> excluded) {
		private static FilterOutcome of(List<Either> results) {
			return new FilterOutcome(
				results.stream().filter(Either::isIncluded).map(Either::item).toList(),
				results.stream().filter(result -> !result.isIncluded()).map(Either::item).toList());
		}
	}

	private record Either(Item item, boolean isIncluded) {
		private static Either included(Item item) {
			return new Either(item, true);
		}

		private static Either excluded(Item item) {
			return new Either(item, false);
		}
	}

	public Function<Item, Publisher<Boolean>> filterItem(ItemFilterParameters parameters) {
		// This is a bit of workaround to allow the predicate method to work with
		// filterWhen for either a mono or a flux and still implement a composite
		return item -> {
			var filterMono = Mono.just(item);

			for (ItemFilter itemFilter : itemFilters) {
				filterMono = filterMono.filterWhen(itemFilter.filterItem(parameters));
			}

			return filterMono
				.map(i -> true) // Return true if all the filters pass
				.defaultIfEmpty(false); // Return false if any filter fails;
		};
	}
}
