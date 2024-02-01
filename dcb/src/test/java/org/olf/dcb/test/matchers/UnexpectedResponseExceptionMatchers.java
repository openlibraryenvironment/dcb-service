package org.olf.dcb.test.matchers;

import static org.olf.dcb.test.matchers.ThrowableMatchers.messageContains;

import org.hamcrest.Matcher;
import org.olf.dcb.core.interaction.UnexpectedResponseException;

public class UnexpectedResponseExceptionMatchers {
	public static Matcher<UnexpectedResponseException> containsRequestInformation(
		String method, String path) {
		return messageContains(
			"Unexpected HTTP response from: %s %s".formatted(method, path));
	}

	public static Matcher<UnexpectedResponseException> containsResponseStatus(
		int statusCode) {
		return messageContains("Response: Status: %s".formatted(statusCode));
	}

	public static Matcher<UnexpectedResponseException> containsJsonResponseProperty(
		String propertyName, String value) {

		return messageContains("\"%s\" : \"%s\"".formatted(propertyName, value));
	}
}
