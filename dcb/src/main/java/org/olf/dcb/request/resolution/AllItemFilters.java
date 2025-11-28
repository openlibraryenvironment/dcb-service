package org.olf.dcb.request.resolution;

import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrNull;
import static services.k_int.utils.ReactorUtils.raiseError;

import java.util.List;

import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.model.Item;
import org.zalando.problem.Problem;

import io.micronaut.context.annotation.Primary;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@Slf4j
@Primary
@Singleton
public class AllItemFilters {
	// Todo: This currently includes all of the current filters
	// they could be split into separate classes
	// then this can act as a composite https://guides.micronaut.io/latest/micronaut-patterns-composite-maven-java.html)
	private final HostLmsService hostLmsService;
	private final AgencyExclusionItemFilter agencyExclusionItemFilter;
	private final ExcludeFromSameAgencyItemFilter excludeFromSameAgencyItemFilter;
	private final IsRequestableItemFilter isRequestableItemFilter;
	private final IncludeItemWithHoldsItemFilter includeItemWithHoldsItemFilter;

	AllItemFilters(HostLmsService hostLmsService,
		ExcludeFromSameAgencyItemFilter excludeFromSameAgencyItemFilter,
		AgencyExclusionItemFilter agencyExclusionItemFilter,
		IsRequestableItemFilter isRequestableItemFilter,
		IncludeItemWithHoldsItemFilter includeItemWithHoldsItemFilter) {

		this.hostLmsService = hostLmsService;
		this.excludeFromSameAgencyItemFilter = excludeFromSameAgencyItemFilter;
		this.agencyExclusionItemFilter = agencyExclusionItemFilter;
		this.isRequestableItemFilter = isRequestableItemFilter;
		this.includeItemWithHoldsItemFilter = includeItemWithHoldsItemFilter;
	}

	public Mono<List<Item>> filterItems(Flux<Item> items, ItemFilterParameters parameters) {
		final var borrowingHostLmsCode = getValueOrNull(parameters,
			ItemFilterParameters::getBorrowingHostLmsCode);

		return items
			.filterWhen(excludeFromSameAgencyItemFilter.predicate(parameters))
			.filterWhen(agencyExclusionItemFilter.predicate(parameters))
			.filterWhen(isRequestableItemFilter.predicate(parameters))
			.filterWhen(includeItemWithHoldsItemFilter.predicate(parameters))
			.filterWhen(item -> fromSameServer(item, borrowingHostLmsCode))
			.collectList();
	}

	/**
	 * Determines if an item should be excluded based on server configuration comparison.
	 * Returns true if the item should be kept, false if it should be excluded.
	 *
	 * @param item the item to check
	 * @param borrowingHostLmsCode code of the Host LMS the borrowing library is using
	 * @return Mono<Boolean> indicating if the item should be kept
	 */
	private Mono<Boolean> fromSameServer(Item item, String borrowingHostLmsCode) {
		final var itemLmsCode = getValueOrNull(item, Item::getHostLmsCode);

		if (itemLmsCode == null || borrowingHostLmsCode == null) {
			return raiseError(Problem.builder()
				.withTitle("Missing required value to evaluate item fromSameServer")
				.withDetail("Could not compare LMS codes")
				.with("itemLmsCode", itemLmsCode)
				.with("borrowingHostLmsCode", borrowingHostLmsCode)
				.build());
		}

		return Mono.zip(
			hostLmsService.getHostLmsBaseUrl(itemLmsCode),
			hostLmsService.getHostLmsBaseUrl(borrowingHostLmsCode)
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
			boolean isDifferentLms = !itemLmsCode.equals(borrowingHostLmsCode);
			boolean shouldExclude = isSameServer && isDifferentLms;

			if (shouldExclude) {
				log.warn("Excluding item from same server: itemLms={}, borrowingLms={}, baseUrl={}",
					itemLmsCode, borrowingHostLmsCode, itemBaseUrl);
			}

			return !shouldExclude;
		});
	}
}
