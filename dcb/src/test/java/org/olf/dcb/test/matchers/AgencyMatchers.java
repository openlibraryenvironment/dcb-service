package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;

import org.hamcrest.Matcher;

public class AgencyMatchers {
	public static Matcher<Object> hasCode(String expectedCode) {
		return hasProperty("code", is(expectedCode));
	}
}
