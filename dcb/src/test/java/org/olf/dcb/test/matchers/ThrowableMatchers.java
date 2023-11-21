package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;

import org.hamcrest.Matcher;

public class ThrowableMatchers {
	public static Matcher<Throwable> hasMessage(String expectedMessage) {
		return hasProperty("message", is(expectedMessage));
	}
}
