package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasProperty;

import org.hamcrest.Matcher;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequestAudit;

public class PatronRequestAuditMatchers {
	public static Matcher<PatronRequestAudit> hasBriefDescription(String expectedDescription) {
		return hasBriefDescription(is(expectedDescription));
	}

	public static Matcher<PatronRequestAudit> briefDescriptionContains(String description) {
		return hasBriefDescription(containsString(description));
	}

	private static <T> Matcher<PatronRequestAudit> hasBriefDescription(Matcher<T> valueMatcher) {
		return hasProperty("briefDescription", valueMatcher);
	}

	public static Matcher<PatronRequestAudit> hasToStatus(PatronRequest.Status expectedStatus) {
		return hasProperty("toStatus", is(expectedStatus));
	}

	public static Matcher<PatronRequestAudit> hasFromStatus(PatronRequest.Status expectedStatus) {
		return hasProperty("fromStatus", is(expectedStatus));
	}

	public static Matcher<PatronRequestAudit> hasNestedAuditDataProperty(
		String expectedPropertyName, String expectedNestedPropertyName,
		Object expectedNestedPropertyValue) {

		return hasProperty("auditData", hasEntry(equalTo(expectedPropertyName),
			hasEntry(expectedNestedPropertyName, expectedNestedPropertyValue)));
	}

	public static Matcher<PatronRequestAudit> hasAuditDataProperty(
		String expectedPropertyName, Object expectedPropertyValue) {

		return hasProperty("auditData",
			hasEntry(equalTo(expectedPropertyName), is(expectedPropertyValue)));
	}

	public static Matcher<PatronRequestAudit> hasAuditDataProperty(
		String expectedPropertyName) {

		return hasProperty("auditData", hasKey(equalTo(expectedPropertyName)));
	}

	public static Matcher<PatronRequestAudit> hasAuditDataDetail(String expectedDetail) {
		return hasAuditDataProperty("detail", expectedDetail);
	}
}
