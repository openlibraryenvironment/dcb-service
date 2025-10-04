package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasProperty;
import static org.olf.dcb.utils.PropertyAccessUtils.getValue;

import java.time.Instant;

import org.hamcrest.Matcher;
import org.olf.dcb.core.model.DataHostLms;
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

	public static Matcher<Item> hasNoCallNumber() {
		return hasProperty("callNumber", is(nullValue()));
	}

	public static Matcher<Item> hasRawVolumeStatement(String expectedStatement) {
		return hasProperty("rawVolumeStatement", is(expectedStatement));
	}

	public static Matcher<Item> hasNoRawVolumeStatement() {
		return hasProperty("rawVolumeStatement", is(nullValue()));
	}

	public static Matcher<Item> hasParsedVolumeStatement(String expectedStatement) {
		return hasProperty("parsedVolumeStatement", is(expectedStatement));
	}

	public static Matcher<Item> hasNoParsedVolumeStatement() {
		return hasProperty("parsedVolumeStatement", is(nullValue()));
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

	public static Matcher<Item> hasLocationCode(String expectedCode) {
		return hasProperty("location", allOf(
			hasProperty("code", is(expectedCode))
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

	public static Matcher<Item> hasNoHostLmsCode() {
		return hasProperty("hostLmsCode", is(nullValue()));
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

	public static Matcher<Item> hasNoLocalItemTypeCode() {
		return hasProperty("localItemTypeCode", is(nullValue()));
	}

	public static Matcher<Item> hasCanonicalItemType(String expectedCanonicalItemType) {
		return hasProperty("canonicalItemType", is(expectedCanonicalItemType));
	}

	public static Matcher<Item> hasHoldCount(Integer expectedHoldCount) {
		return hasProperty("holdCount", is(expectedHoldCount));
	}

	public static Matcher<Item> hasZeroHoldCount() {
		return hasProperty("holdCount", is(0));
	}

	public static Matcher<Item> isSuppressed() {
		return hasProperty("suppressed", is(true));
	}

	public static Matcher<Item> isNotSuppressed() {
		return hasProperty("suppressed", is(false));
	}

	public static Matcher<Item> isNotDeleted() {
		return hasProperty("deleted", is(false));
	}

	public static Matcher<Item> hasNoLocation() {
		return hasProperty("location", allOf(
			hasProperty("name", is(nullValue())),
			hasProperty("code", is(nullValue()))));
	}

	public static Matcher<Item> hasNoAgency() {
		return hasProperty("agency", is(nullValue()));
	}
	
	public static Matcher<Item> hasAgencyCode(String expectedAgencyCode) {
		return hasProperty("agency", hasProperty("code", is(expectedAgencyCode)));
	}

	public static Matcher<Item> hasAgencyName(String expectedAgencyName) {
		return hasProperty("agency", hasProperty("name", is(expectedAgencyName)));
	}

	public static Matcher<Item> hasOwningContext(String expectedOwningContext) {
		return hasProperty("owningContext", is(expectedOwningContext));
	}

	public static Matcher<Item> hasSourceHostLmsCode(DataHostLms expectedHostLms) {
		return hasSourceHostLmsCode(getValue(expectedHostLms, DataHostLms::getCode, "Null Host LMS"));
	}

	public static Matcher<Item> hasSourceHostLmsCode(String expectedCode) {
		return hasProperty("sourceHostLmsCode", is(expectedCode));
	}
}
