package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;

import org.hamcrest.Matcher;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.request.resolution.Resolution;

public class ResolutionMatchers {
	@SafeVarargs
	public static Matcher<Resolution> hasChosenItem(Matcher<Item>... matchers) {
		return hasProperty("chosenItem", allOf(matchers));
	}

	public static Matcher<Resolution> hasNoChosenItem() {
		return hasProperty("chosenItem", is(nullValue()));
	}

	@SafeVarargs
	public static Matcher<Resolution> hasAllItems(Matcher<Item>... matchers) {
		return hasProperty("allItems", contains(matchers));
	}

	@SafeVarargs
	public static Matcher<Resolution> hasFilteredItems(
		Matcher<Item>... matchers) {
		return hasProperty("filteredItems", containsInAnyOrder(matchers));
	}

	public static Matcher<Resolution> hasFilteredItemsSize(int size) {
		return hasProperty("filteredItems", hasSize(size));
	}
}
