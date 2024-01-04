package org.olf.dcb.test.matchers.interaction;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasProperty;

import org.hamcrest.Matcher;
import org.olf.dcb.core.interaction.Patron;

public class PatronMatchers {
	public static Matcher<Patron> hasLocalIds(String... expectedLocalIds) {
		return hasProperty("localId", containsInAnyOrder(expectedLocalIds));
	}

	public static Matcher<Patron> hasNoLocalNames() {
		return hasProperty("localNames", nullValue());
	}

	public static Matcher<Patron> hasNoHomeLibraryCode() {
		return hasProperty("localHomeLibraryCode", nullValue());
	}

	public static Matcher<Patron> hasLocalBarcodes(String... expectedBarcodes) {
		return hasProperty("localBarcodes", containsInAnyOrder(expectedBarcodes));
	}

	public static Matcher<Patron> hasLocalPatronType(String expectedPatronGroup) {
		return hasProperty("localPatronType", is(expectedPatronGroup));
	}

	public static Matcher<Patron> hasLocalNames(String... expectedNames) {
		return hasProperty("localNames", contains(expectedNames));
	}
}
