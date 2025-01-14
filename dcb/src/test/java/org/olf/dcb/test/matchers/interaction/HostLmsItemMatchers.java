package org.olf.dcb.test.matchers.interaction;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;

import org.hamcrest.Matcher;
import org.olf.dcb.core.interaction.HostLmsItem;

public class HostLmsItemMatchers {
	public static Matcher<HostLmsItem> hasStatus(String expectedStatus) {
		return hasProperty("status", is(expectedStatus));
	}

	public static Matcher<HostLmsItem> hasLocalId(String expectedId) {
		return hasProperty("localId", is(expectedId));
	}

	public static Matcher<HostLmsItem> hasBarcode(String expectedBarcode) {
		return hasProperty("barcode", is(expectedBarcode));
	}

	public static Matcher<HostLmsItem> hasRawStatus(String expectedStatus) {
		return hasProperty("rawStatus", is(expectedStatus));
	}

	public static Matcher<HostLmsItem> hasRenewalCount(Integer count) {
		return hasProperty("renewalCount", is(count));
	}
}
