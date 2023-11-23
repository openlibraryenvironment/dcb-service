package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;

import org.hamcrest.Matcher;
import org.olf.dcb.core.interaction.LocalRequest;

public class LocalRequestMatchers {
	public static Matcher<LocalRequest> hasLocalStatus(String expectedLocalStatus) {
		return hasProperty("localStatus", is(expectedLocalStatus));
	}

	public static Matcher<LocalRequest> hasLocalId(String expectedLocalId) {
		return hasProperty("localId", is(expectedLocalId));
	}
}
