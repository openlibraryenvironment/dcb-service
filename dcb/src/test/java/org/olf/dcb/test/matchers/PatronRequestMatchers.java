package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;

import java.util.UUID;

import org.hamcrest.Matcher;
import org.olf.dcb.core.model.PatronRequest;

public class PatronRequestMatchers {
	public static Matcher<PatronRequest> hasId(UUID expectedId) {
		return hasProperty("id", is(expectedId));
	}

	public static Matcher<PatronRequest> hasStatus(PatronRequest.Status expectedStatus) {
		return hasProperty("status", is(expectedStatus));
	}

	public static Matcher<PatronRequest> hasErrorMessage(String expectedErrorMessage) {
		return hasProperty("errorMessage", containsString(expectedErrorMessage));
	}

	public static Matcher<PatronRequest> hasLocalPatronType(String expectedLocalPatronType) {
		return hasProperty("requestingIdentity",
			hasProperty("localPtype", is(expectedLocalPatronType)));
	}
}
