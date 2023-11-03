package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;

import org.hamcrest.Matcher;
import org.olf.dcb.core.model.PatronRequest;

public class PatronRequestMatchers {
	public static Matcher<PatronRequest> hasLocalPatronType(String expectedLocalPatronType) {
		return hasProperty("requestingIdentity",
			hasProperty("localPtype", is(expectedLocalPatronType)));
	}
}
