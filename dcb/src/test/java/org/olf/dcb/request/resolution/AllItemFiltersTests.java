package org.olf.dcb.request.resolution;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.Item;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

/**
 * A resolution that ends with nothing has to be able to say which filter emptied it.
 */
@TestInstance(PER_CLASS)
class AllItemFiltersTests {
	@Test
	void shouldNameTheFilterThatExcludedAnItem() {
		final var filters = new AllItemFilters(List.of(
			acceptEverything("FirstFilter"),
			reject("SecondFilter", "item-2"),
			acceptEverything("ThirdFilter")));

		final var outcome = filters.partition(
			List.of(item("item-1"), item("item-2")), parameters()).block();

		assertThat(outcome.included(), hasSize(1));
		assertThat(outcome.included().get(0).getLocalId(), is("item-1"));

		assertThat(outcome.excluded(), hasSize(1));
		assertThat(outcome.excluded().get(0).getDecisionLogEntries(),
			contains("Excluded by SecondFilter"));
	}

	@Test
	void shouldNameOnlyTheFirstFilterToRejectAnItem() {
		// Filters run in their declared order and stop at the first rejection, as they did
		// when this was a predicate: the audit names the reason that actually applied
		final var filters = new AllItemFilters(List.of(
			reject("FirstFilter", "item-1"),
			reject("SecondFilter", "item-1")));

		final var outcome = filters.partition(List.of(item("item-1")), parameters()).block();

		assertThat(outcome.excluded().get(0).getDecisionLogEntries(),
			contains("Excluded by FirstFilter"));
	}

	private static ItemFilter acceptEverything(String name) {
		return named(name, item -> Mono.just(true));
	}

	private static ItemFilter reject(String name, String localId) {
		return named(name, item -> Mono.just(!localId.equals(item.getLocalId())));
	}

	private static ItemFilter named(String name, Function<Item, Publisher<Boolean>> decision) {
		return new ItemFilter() {
			@Override
			public String getName() {
				return name;
			}

			@Override
			public Function<Item, Publisher<Boolean>> filterItem(ItemFilterParameters parameters) {
				return decision;
			}
		};
	}

	private static ItemFilterParameters parameters() {
		// The filters under test read nothing from the parameters; the real ones are covered
		// by their own tests
		return new ItemFilterParameters() {
			@Override
			public List<String> excludedSupplyingAgencyCodes() {
				return List.of();
			}

			@Override
			public String borrowingAgencyCode() {
				return "BORROWER";
			}

			@Override
			public String borrowingHostLmsCode() {
				return "BORROWING-SYSTEM";
			}

			@Override
			public String pickupAgencyCode() {
				return "BORROWER";
			}

			@Override
			public Boolean isExpeditedCheckout() {
				return false;
			}
		};
	}

	private static Item item(String localId) {
		return Item.builder().localId(localId).build();
	}
}
