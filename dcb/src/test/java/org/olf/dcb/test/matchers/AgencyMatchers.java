package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.nullValue;
import static org.olf.dcb.test.matchers.ModelMatchers.hasId;
import static org.olf.dcb.utils.PropertyAccessUtils.getValueOrThrow;

import org.hamcrest.Matcher;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;

public class AgencyMatchers {
	public static Matcher<Object> hasCode(String expectedCode) {
		return hasProperty("code", is(expectedCode));
	}

	public static Matcher<DataAgency> hasHostLms(DataHostLms expectedHostLms) {
		final var expectedId = getValueOrThrow(expectedHostLms, DataHostLms::getId,
			() -> new RuntimeException("Expected host LMS does not have an ID"));

		final var expectedCode = getValueOrThrow(expectedHostLms, DataHostLms::getCode,
			() -> new RuntimeException("Expected host LMS does not have a code"));

		return allOf(hasProperty("hostLms", allOf(
			hasId(expectedId),
			hasCode(expectedCode)
		)));
	}

	public static Matcher<DataAgency> hasNoHostLms() {
		return hasProperty("hostLms", is(nullValue()));
	}
}
