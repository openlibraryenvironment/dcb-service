package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasProperty;
import static services.k_int.utils.StringUtils.convertIntegerToString;

import org.hamcrest.Matcher;
import org.olf.dcb.core.interaction.LocalRequest;

public class LocalRequestMatchers {
	public static Matcher<LocalRequest> hasLocalStatus(String expectedLocalStatus) {
		return hasProperty("localStatus", is(expectedLocalStatus));
	}

	public static Matcher<LocalRequest> hasRawLocalStatus(String expectedRawLocalStatus) {
		return hasProperty("rawLocalStatus", is(expectedRawLocalStatus));
	}

	public static Matcher<LocalRequest> hasLocalId(Integer expectedLocalId) {
		return hasLocalId(expectedLocalId.toString());
	}

	public static Matcher<LocalRequest> hasLocalId(String expectedLocalId) {
		return hasProperty("localId", is(expectedLocalId));
	}

	public static Matcher<LocalRequest> hasLocalId() {
		return hasProperty("localId", is(notNullValue()));
	}

	public static Matcher<LocalRequest> hasRequestedItemId(Integer expectedItemId) {
		return hasRequestedItemId(convertIntegerToString(expectedItemId));
	}

	public static Matcher<LocalRequest> hasRequestedItemId(String expectedItemId) {
		return hasProperty("requestedItemId", is(expectedItemId));
	}

	public static Matcher<LocalRequest> hasNoRequestedItemId() {
		return hasProperty("requestedItemId", is(nullValue()));
	}

	public static Matcher<LocalRequest> hasRequestedItemBarcode(String expectedBarcode) {
		return hasProperty("requestedItemBarcode", is(expectedBarcode));
	}

	public static Matcher<LocalRequest> hasNoRequestedItemBarcode() {
		return hasProperty("requestedItemBarcode", is(nullValue()));
	}
}
