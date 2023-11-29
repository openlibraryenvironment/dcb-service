package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasProperty;

import java.time.Instant;

import org.hamcrest.Matcher;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatusCode;

public class ItemMatchers {
	public static Matcher<Item> hasLocalId(String expectedId) {
		return hasProperty("localId", is(expectedId));
	}

	public static Matcher<Item> hasBarcode(String expectedBarcode) {
		return hasProperty("barcode", is(expectedBarcode));
	}

	public static Matcher<Item> hasCallNumber(String expectedCallNumber) {
		return hasProperty("callNumber", is(expectedCallNumber));
	}

	public static Matcher<Item> hasStatus(ItemStatusCode expectedStatus) {
		return hasProperty("status", hasProperty("code", is(expectedStatus)));
	}

	public static Matcher<Item> hasLocation(String expectedName, String expectedCode) {
		return hasProperty("location", allOf(
			hasProperty("name", is(expectedName)),
			hasProperty("code", is(expectedCode))
		));
	}

	public static Matcher<Item> hasDueDate(String expectedDueDate) {
		return hasProperty("dueDate", is(Instant.parse(expectedDueDate)));
	}

	public static Matcher<Item> hasNoDueDate() {
		return hasProperty("dueDate", is(nullValue()));
	}
}
