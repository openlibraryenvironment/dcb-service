package org.olf.dcb.test.matchers;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;

import org.hamcrest.Matcher;

public class ThrowableMatchers {
	public static <T extends Throwable> Matcher<T> hasMessage(String expectedMessage) {
		return messageProperty(is(expectedMessage));
	}

	public static <T extends Throwable> Matcher<T> messageContains(String expectedMessage) {
		return messageProperty(containsString(expectedMessage));
	}

	private static <T extends Throwable> Matcher<T> messageProperty(Matcher<String> matcher) {
		return hasProperty("message", matcher);
	}
}
