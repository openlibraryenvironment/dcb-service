package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.notNullValue;

import java.util.UUID;

import org.hamcrest.Matcher;
import org.olf.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.dcb.core.model.DataHostLms;

public class HostLmsMatchers {
	public static Matcher<DataHostLms> hasId(UUID hostLmsId) {
		return hasProperty("id", is(hostLmsId));
	}

	public static Matcher<DataHostLms> hasNonNullId() {
		return hasProperty("id", notNullValue());
	}

	public static Matcher<DataHostLms> hasName(String expectedName) {
		return hasProperty("name", is(expectedName));
	}

	public static Matcher<DataHostLms> hasCode(String expectedCode) {
		return hasProperty("code", is(expectedCode));
	}

	public static Matcher<DataHostLms> hasType(Class<SierraLmsClient> expectedType) {
		return hasProperty("type", is(expectedType));
	}
}
