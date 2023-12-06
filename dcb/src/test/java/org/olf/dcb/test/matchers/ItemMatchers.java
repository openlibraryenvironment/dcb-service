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

	public static Matcher<Item> hasNoBarcode() {
		return hasProperty("barcode", is(nullValue()));
	}

	public static Matcher<Item> hasStatus(ItemStatusCode expectedStatus) {
		return hasProperty("status", hasProperty("code", is(expectedStatus)));
	}

	public static Matcher<Item> hasCallNumber(String expectedCallNumber) {
		return hasProperty("callNumber", is(expectedCallNumber));
	}

	public static Matcher<Item> hasLocation(String expectedName, String expectedCode) {
		return hasProperty("location", allOf(
			hasProperty("name", is(expectedName)),
			hasProperty("code", is(expectedCode))
		));
	}

	public static Matcher<Item> hasLocation(String expectedName) {
		return hasProperty("location", allOf(
			hasProperty("name", is(expectedName)),
			hasProperty("code", is(nullValue()))
		));
	}

	public static Matcher<Item> hasDueDate(String expectedDueDate) {
		return hasDueDate(Instant.parse(expectedDueDate));
	}

	public static Matcher<Item> hasDueDate(Instant expectedDueDate) {
		return hasProperty("dueDate", is(expectedDueDate));
	}

	public static Matcher<Item> hasNoDueDate() {
		return hasProperty("dueDate", is(nullValue()));
	}

	public static Matcher<Item> hasLocalBibId(String expectedLocalBibId) {
		return hasProperty("localBibId", is(expectedLocalBibId));
	}

	public static Matcher<Item> hasHostLmsCode(String expectedHostLmsCode) {
		return hasProperty("hostLmsCode", is(expectedHostLmsCode));
	}

	public static Matcher<Item> hasLocalItemType(String expectedLocalItemType) {
		return hasProperty("localItemType", is(expectedLocalItemType));
	}

	public static Matcher<Item> hasNoLocalItemType() {
		return hasProperty("localItemType", is(nullValue()));
	}

	public static Matcher<Item> hasLocalItemTypeCode(String expectedLocalItemTypeCode) {
		return hasProperty("localItemTypeCode", is(expectedLocalItemTypeCode));
	}

	public static Matcher<Item> hasCanonicalItemType(String expectedCanonicalItemType) {
		return hasProperty("canonicalItemType", is(expectedCanonicalItemType));
	}

	public static Matcher<Item> hasHoldCount(Integer expectedHoldCount) {
		return hasProperty("holdCount", is(expectedHoldCount));
	}

	public static Matcher<Item> hasNoHoldCount() {
		return hasProperty("holdCount", is(nullValue()));
	}

	public static Matcher<Item> isNotSuppressed() {
		return hasProperty("suppressed", is(false));
	}

	public static Matcher<Item> suppressionUnknown() {
		return hasProperty("suppressed", is(nullValue()));
	}

	public static Matcher<Item> isNotDeleted() {
		return hasProperty("deleted", is(false));
	}

	public static Matcher<Item> hasAgencyCode(String expectedAgencyCode) {
		return hasProperty("agencyCode", is(expectedAgencyCode));
	}

	public static Matcher<Item> hasNoAgencyCode() {
		return hasProperty("agencyCode", is(nullValue()));
	}

	public static Matcher<Item> hasAgencyName(String expectedAgencyDescription) {
		return hasProperty("agencyName", is(expectedAgencyDescription));
	}

	public static Matcher<Item> hasNoAgencyName() {
		return hasProperty("agencyName", is(nullValue()));
	}
}
