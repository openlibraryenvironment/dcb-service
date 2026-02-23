package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasProperty;
import static services.k_int.utils.StringUtils.convertIntegerToString;

import org.hamcrest.Matcher;
import org.olf.dcb.core.interaction.HostLmsRequest;

public class HostLmsRequestMatchers {
	public static Matcher<HostLmsRequest> hasStatus(String expectedStatus) {
		return hasProperty("status", is(expectedStatus));
	}

	public static Matcher<HostLmsRequest> hasRawStatus(String expectedStatus) {
		return hasProperty("rawStatus", is(expectedStatus));
	}

	public static Matcher<HostLmsRequest> hasNoStatus() {
		return hasProperty("status", is(nullValue()));
	}

	public static Matcher<HostLmsRequest> hasLocalId(Integer expectedId) {
		return hasLocalId(expectedId.toString());
	}

	public static Matcher<HostLmsRequest> hasLocalId(String expectedId) {
		return hasProperty("localId", is(expectedId));
	}

	public static Matcher<HostLmsRequest> hasNoLocalId() {
		return hasProperty("localId", is(nullValue()));
	}

	public static Matcher<HostLmsRequest> hasRequestedItemId(Integer expectedId) {
		return hasRequestedItemId(convertIntegerToString(expectedId));
	}

	public static Matcher<HostLmsRequest> hasRequestedItemId(String expectedId) {
		return hasProperty("requestedItemId", is(expectedId));
	}

	public static Matcher<HostLmsRequest> hasNoRequestedItemId() {
		return hasProperty("requestedItemId", is(nullValue()));
	}

	public static Matcher<HostLmsRequest> hasRequestedItemBarcode(String expectedBarcode) {
		return hasProperty("requestedItemBarcode", is(expectedBarcode));
	}

	public static Matcher<HostLmsRequest> hasNoRequestedItemBarcode() {
		return hasProperty("requestedItemBarcode", is(nullValue()));
	}
}
