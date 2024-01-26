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
}
