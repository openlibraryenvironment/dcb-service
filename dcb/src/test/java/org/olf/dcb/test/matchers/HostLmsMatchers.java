package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.notNullValue;

import java.util.UUID;

import org.hamcrest.Matcher;
import org.olf.dcb.core.model.DataHostLms;

public class HostLmsMatchers {
	public static Matcher<DataHostLms> hasId(UUID hostLmsId) {
		return hasId(is(hostLmsId));
	}

	public static Matcher<DataHostLms> hasNonNullId() {
		return hasId(notNullValue());
	}

	private static Matcher<DataHostLms> hasId(Matcher<Object> matcher) {
		return hasProperty("id", matcher);
	}

	public static Matcher<DataHostLms> hasName(String expectedName) {
		return hasProperty("name", is(expectedName));
	}

	public static Matcher<DataHostLms> hasCode(String expectedCode) {
		return hasProperty("code", is(expectedCode));
	}

	public static Matcher<DataHostLms> hasClientClass(String expectedClientClass) {
		return hasProperty("lmsClientClass", is(expectedClientClass));
	}

	public static Matcher<DataHostLms> hasNoClientClass() {
		return hasProperty("lmsClientClass", is(nullValue()));
	}

	public static Matcher<DataHostLms> hasIngestSourceClass(String expectedIngestSourceClass) {
		return hasProperty("ingestSourceClass", is(expectedIngestSourceClass));
	}

	public static Matcher<DataHostLms> hasNoIngestSourceClass() {
		return hasProperty("ingestSourceClass", is(nullValue()));
	}
}
