package org.olf.dcb.test.matchers.interaction;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasProperty;

import org.hamcrest.Matcher;
import org.olf.dcb.core.interaction.Patron;

public class PatronMatchers {
	public static Matcher<Patron> hasLocalIds(String... expectedLocalIds) {
		return hasProperty("localId", containsInAnyOrder(expectedLocalIds));
	}

	public static Matcher<Patron> hasNoLocalIds() {
		return hasProperty("localId", anyOf(nullValue(), empty()));
	}

	public static Matcher<Patron> hasLocalNames(String... expectedNames) {
		return hasProperty("localNames", contains(expectedNames));
	}

	public static Matcher<Patron> hasNoLocalNames() {
		return hasProperty("localNames", anyOf(nullValue(), empty()));
	}

	public static Matcher<Patron> hasHomeLibraryCode(String expectedCode) {
		return hasProperty("localHomeLibraryCode", is(expectedCode));
	}

	public static Matcher<Patron> hasNoHomeLibraryCode() {
		return hasProperty("localHomeLibraryCode", nullValue());
	}

	public static Matcher<Patron> hasLocalBarcodes(String... expectedBarcodes) {
		return hasProperty("localBarcodes", containsInAnyOrder(expectedBarcodes));
	}

	public static Matcher<Patron> hasNoLocalBarcodes() {
		return hasProperty("localBarcodes", anyOf(nullValue(), empty()));
	}

	public static Matcher<Patron> hasLocalPatronType(String expectedPatronGroup) {
		return hasProperty("localPatronType", is(expectedPatronGroup));
	}

	public static Matcher<Patron> hasLocalPatronType(Integer expectedPatronGroup) {
		return hasLocalPatronType(Integer.toString(expectedPatronGroup));
	}

	public static Matcher<Patron> hasNoLocalPatronType() {
		return hasProperty("localPatronType", is(nullValue()));
	}

	public static Matcher<Patron> hasCanonicalPatronType(String expectedPatronType) {
		return hasProperty("canonicalPatronType", is(expectedPatronType));
	}

	public static Matcher<Patron> hasNoCanonicalPatronType() {
		return hasProperty("canonicalPatronType", anyOf(nullValue(), empty()));
	}

	public static Matcher<Patron> isBlocked() {
		return hasProperty("isBlocked", is(true));
	}

	public static Matcher<Patron> isNotBlocked() {
		return hasProperty("isBlocked", is(false));
	}

	public static Matcher<Patron> isActive() {
		return hasProperty("isActive", is(true));
	}

	public static Matcher<Patron> isNotDeleted() {
		return hasProperty("isDeleted", is(false));
	}
}
