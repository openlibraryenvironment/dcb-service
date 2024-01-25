package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;

import org.hamcrest.Matcher;
import org.olf.dcb.core.interaction.HostLmsRequest;

public class HostLmsRequestMatchers {
	public static Matcher<HostLmsRequest> hasStatus(String expectedStatus) {
		return hasProperty("status", is(expectedStatus));
	}

	public static Matcher<HostLmsRequest> hasLocalId(String expectedId) {
		return hasProperty("localId", is(expectedId));
	}
}
