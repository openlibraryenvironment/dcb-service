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

@Slf4j
@Primary
@Singleton
@AllArgsConstructor
public class AllItemFilters implements ItemFilter {
	// Acts as a composite (https://guides.micronaut.io/latest/micronaut-patterns-composite-maven-java.html)
	private final List<ItemFilter> itemFilters;

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
