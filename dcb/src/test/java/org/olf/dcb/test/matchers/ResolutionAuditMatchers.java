package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.allOf;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasAuditDataProperty;
import static org.olf.dcb.test.matchers.PatronRequestAuditMatchers.hasBriefDescription;

import org.hamcrest.Matcher;
import org.olf.dcb.core.model.PatronRequestAudit;

public class ResolutionAuditMatchers {
	public static Matcher<PatronRequestAudit> isNoSelectableItemResolutionAudit(
		String expectedProcessName) {

		return allOf(
			hasBriefDescription("%s could not select an item".formatted(expectedProcessName)),
			hasAuditDataProperty("filteredItems"),
			hasAuditDataProperty("sortedItems"),
			hasAuditDataProperty("allItems")
		);
	}
}
