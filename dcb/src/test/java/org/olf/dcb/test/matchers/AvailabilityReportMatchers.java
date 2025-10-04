package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;

import org.hamcrest.Matcher;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.item.availability.AvailabilityReport;

public class AvailabilityReportMatchers {
	public static Matcher<AvailabilityReport> hasNoItems() {
		return hasProperty("items", empty());
	}

	public static Matcher<AvailabilityReport> hasItems(int expectedCount) {
		return hasProperty("items", hasSize(expectedCount));
	}

	@SafeVarargs
	public static Matcher<AvailabilityReport> hasItemsInOrder(
		Matcher<Item>... matchers) {
		return hasProperty("items", contains(matchers));
	}

	public static Matcher<AvailabilityReport> hasNoErrors() {
		return hasProperty("errors", empty());
	}

	public static Matcher<AvailabilityReport> hasError(String expectedMessage) {
		return hasProperty("errors", containsInAnyOrder(
			hasProperty("message", is(expectedMessage)))
		);
	}
}
