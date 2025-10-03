package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasProperty;

import java.util.UUID;

import org.hamcrest.Matcher;
import org.olf.dcb.api.AvailabilityResponse;

public class AvailabilityResponseMatchers {
	@SafeVarargs
	public static Matcher<AvailabilityResponse> hasItems(
		Matcher<AvailabilityResponse.Item>... itemMatchers) {

		return hasProperty("itemList", containsInAnyOrder(itemMatchers));
	}

	public static Matcher<AvailabilityResponse> hasNoItems() {
		return hasProperty("itemList", is(nullValue()));
	}

	public static Matcher<AvailabilityResponse> hasNoErrors() {
		return hasProperty("errors", is(nullValue()));
	}

	public static Matcher<AvailabilityResponse> hasClusterRecordId(UUID clusterRecordId) {
		return hasProperty("clusteredBibId", is(clusterRecordId));
	}

	public static Matcher<AvailabilityResponse.Item> hasId(String expectedId) {
		return hasProperty("id", is(expectedId));
	}

	public static Matcher<AvailabilityResponse.Item> hasCallNumber(String expectedCallNumber) {
		return hasProperty("callNumber", is(expectedCallNumber));
	}

	public static Matcher<AvailabilityResponse.Item> hasBarcode(String expectedBarcode) {
		return hasProperty("barcode", is(expectedBarcode));
	}

	public static Matcher<AvailabilityResponse.Item> hasAgency(
		String expectedCode, String expectedName) {

		return hasProperty("agency", allOf(
			notNullValue(),
			hasProperty("code", is(expectedCode)),
			hasProperty("description", is(expectedName))
		));
	}

	private static Matcher<AvailabilityResponse.Item> hasNoAgency() {
		return hasProperty("agency", nullValue());
	}

	public static Matcher<AvailabilityResponse.Item> hasLocation(
		String expectedCode, String expectedName) {

		return hasProperty("location", allOf(
			notNullValue(),
			hasProperty("code", is(expectedCode)),
			hasProperty("name", is(expectedName))
		));
	}

	public static Matcher<AvailabilityResponse.Item> hasHostLms(
		String expectedHostLmsCode) {
		return hasProperty("hostLmsCode", is(expectedHostLmsCode));
	}

	private static Matcher<AvailabilityResponse.Item> hasNoHostLms() {
		return hasProperty("hostLmsCode", nullValue());
	}

	public static Matcher<AvailabilityResponse.Item> hasDueDate(String expectedDueDate) {
		return hasProperty("dueDate", is(expectedDueDate));
	}

	public static Matcher<AvailabilityResponse.Item> hasNoDueDate() {
		return hasProperty("dueDate", nullValue());
	}

	public static Matcher<AvailabilityResponse.Item> hasCanonicalItemType(String expectedItemType) {
		return hasProperty("canonicalItemType", is(expectedItemType));
	}

	public static Matcher<AvailabilityResponse.Item> hasLocalItemType(String expectedItemType) {
		return hasProperty("localItemType", is(expectedItemType));
	}

	public static Matcher<AvailabilityResponse.Item> hasNoHolds() {
		return hasProperty("holdCount", is(0));
	}

	public static Matcher<AvailabilityResponse.Item> isRequestable() {
		return hasProperty("isRequestable", is(true));
	}

	public static Matcher<AvailabilityResponse.Item> isNotRequestable() {
		return hasProperty("isRequestable", is(false));
	}

	public static Matcher<AvailabilityResponse.Item> hasStatus(String expectedStatus) {
		return hasProperty("status", hasProperty("code", is(expectedStatus)));
	}

	public static Matcher<? super AvailabilityResponse.Item> hasAvailabilityDate() {
		return hasProperty("availabilityDate");
	}
}
