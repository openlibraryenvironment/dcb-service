package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;
import static org.olf.dcb.core.model.PatronRequest.Status.FINALISED;

import java.util.UUID;

import org.hamcrest.Matcher;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.PatronRequest;

public class PatronRequestMatchers {
	public static Matcher<PatronRequest> hasId(UUID expectedId) {
		return hasProperty("id", is(expectedId));
	}

	public static Matcher<PatronRequest> hasStatus(PatronRequest.Status expectedStatus) {
		return hasProperty("status", is(expectedStatus));
	}

	public static Matcher<PatronRequest> isFinalised() {
		return hasStatus(FINALISED);
	}

	public static Matcher<PatronRequest> hasErrorMessage(String expectedErrorMessage) {
		return hasErrorMessage(containsString(expectedErrorMessage));
	}

	public static Matcher<PatronRequest> hasErrorMessage(Matcher<String> valueMatcher) {
		return hasProperty("errorMessage", valueMatcher);
	}

	public static Matcher<PatronRequest> hasLocalPatronType(String expectedLocalPatronType) {
		return hasProperty("requestingIdentity",
			hasProperty("localPtype", is(expectedLocalPatronType)));
	}

	public static Matcher<PatronRequest> hasResolvedAgency(DataAgency expectedResolvedAgency) {
		return hasProperty("requestingIdentity",
			hasProperty("resolvedAgency", allOf(
				hasResolvedAgencyId(expectedResolvedAgency.getId()),
				hasResolvedAgencyCode(expectedResolvedAgency.getCode()),
				hasResolvedAgencyName(expectedResolvedAgency.getName())
			)));
	}

	private static Matcher<DataAgency> hasResolvedAgencyId(UUID expectedId) {
		return hasProperty("id", is(expectedId));
	}

	private static Matcher<DataAgency> hasResolvedAgencyCode(String expectedCode) {
		return hasProperty("code", is(expectedCode));
	}

	private static Matcher<DataAgency> hasResolvedAgencyName(String expectedName) {
		return hasProperty("name", is(expectedName));
	}
}
