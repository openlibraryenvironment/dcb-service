package org.olf.dcb.request.resolution;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;

import jakarta.inject.Inject;

/**
 * The composite applies filters in the order the container hands them over, so
 * that order is part of the design rather than an accident of bean discovery.
 * <p>
 * Cheapest first: an item rejected on a plain field comparison should never reach
 * a filter that would go to the database or build a Host LMS client for it.
 */
@DcbTest
class ItemFilterOrderingTests {
	@Inject
	List<ItemFilter> itemFilters;

	@Test
	void shouldApplyFiltersCheapestFirst() {
		final var order = itemFilters.stream()
			.map(filter -> filter.getClass().getSimpleName())
			.filter(name -> !name.contains("AllItemFilters"))
			.toList();

		assertThat(order, contains(
			"IsRequestableItemFilter",        // plain field read
			"AgencyExclusionItemFilter",      // plain list membership
			"ExcludeFromSameAgencyItemFilter",// consortium functional setting
			"ExcludeSupplierPickupFilter",    // consortium functional setting
			"IncludeItemWithHoldsItemFilter", // consortium functional setting
			"SameServerItemFilter"            // builds two Host LMS clients
		));
	}
}
