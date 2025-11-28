package org.olf.dcb.request.resolution;

import java.util.List;

import org.olf.dcb.core.model.Item;

import io.micronaut.context.annotation.Primary;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Primary
@Singleton
public class AllItemFilters {
	// Todo: This currently includes all of the current filters
	// they could be split into separate classes
	// then this can act as a composite https://guides.micronaut.io/latest/micronaut-patterns-composite-maven-java.html)
	private final AgencyExclusionItemFilter agencyExclusionItemFilter;
	private final ExcludeFromSameAgencyItemFilter excludeFromSameAgencyItemFilter;
	private final IsRequestableItemFilter isRequestableItemFilter;
	private final IncludeItemWithHoldsItemFilter includeItemWithHoldsItemFilter;
	private final SameServerItemFilter sameServerItemFilter;

	AllItemFilters(ExcludeFromSameAgencyItemFilter excludeFromSameAgencyItemFilter,
		AgencyExclusionItemFilter agencyExclusionItemFilter,
		IsRequestableItemFilter isRequestableItemFilter,
		IncludeItemWithHoldsItemFilter includeItemWithHoldsItemFilter,
		SameServerItemFilter sameServerItemFilter) {

		this.excludeFromSameAgencyItemFilter = excludeFromSameAgencyItemFilter;
		this.agencyExclusionItemFilter = agencyExclusionItemFilter;
		this.isRequestableItemFilter = isRequestableItemFilter;
		this.includeItemWithHoldsItemFilter = includeItemWithHoldsItemFilter;
		this.sameServerItemFilter = sameServerItemFilter;
	}

	public Mono<List<Item>> filterItems(Flux<Item> items, ItemFilterParameters parameters) {
		return items
			.filterWhen(excludeFromSameAgencyItemFilter.predicate(parameters))
			.filterWhen(agencyExclusionItemFilter.predicate(parameters))
			.filterWhen(isRequestableItemFilter.predicate(parameters))
			.filterWhen(includeItemWithHoldsItemFilter.predicate(parameters))
			.filterWhen(sameServerItemFilter.predicate(parameters))
			.collectList();
	}
}
