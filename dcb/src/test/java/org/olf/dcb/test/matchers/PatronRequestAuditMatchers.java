package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasProperty;

import org.hamcrest.Matcher;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;

public class PatronRequestAuditMatchers {
	public static Matcher<PatronRequestAudit> hasNoBriefDescription() {
		return hasProperty("briefDescription", is(nullValue()));
	}

	public static Matcher<PatronRequestAudit> hasToStatus(PatronRequest.Status expectedStatus) {
		return hasProperty("toStatus", is(expectedStatus));
	}

	public static Matcher<PatronRequestAudit> hasFromStatus(PatronRequest.Status expectedStatus) {
		return hasProperty("fromStatus", is(expectedStatus));
	}

	public static Matcher<PatronRequestAudit> hasBriefDescription(String expectedDescription) {
		return hasProperty("briefDescription", is(expectedDescription));
	}
}
