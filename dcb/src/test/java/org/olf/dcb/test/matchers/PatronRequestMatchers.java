package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.hasProperty;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.PATRON_VERIFIED;

import java.util.UUID;

import org.hamcrest.Matcher;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;

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
