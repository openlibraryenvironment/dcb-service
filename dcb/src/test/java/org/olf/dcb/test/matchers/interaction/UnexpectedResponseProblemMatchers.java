package org.olf.dcb.test.matchers.interaction;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;
import static org.olf.dcb.test.matchers.ThrowableProblemMatchers.hasParameters;

import org.hamcrest.Matcher;
import org.zalando.problem.ThrowableProblem;

public class UnexpectedResponseProblemMatchers {
	public static Matcher<ThrowableProblem> hasMessageForHostLms(String expectedHostLmsCode) {
		return hasMessage("Unexpected response from Host LMS: \"%s\""
			.formatted(expectedHostLmsCode));
	}

	public static Matcher<ThrowableProblem> hasMessageForRequest(String method, String path) {
		return hasMessage("Unexpected response from: %s %s".formatted(method, path));
	}

	public static Matcher<ThrowableProblem> hasResponseStatusCode(
		Object expectedStatusCode) {

		return hasParameters(hasEntry(equalTo("responseStatusCode"), is(expectedStatusCode)));
	}

	public static Matcher<ThrowableProblem> hasJsonResponseBodyProperty(
		String expectedPropertyName, Object expectedValue) {

		return hasResponseBody(hasEntry(equalTo(expectedPropertyName), is(expectedValue)));
	}

	public static Matcher<ThrowableProblem> hasTextResponseBody(String expectedBody) {
		return hasResponseBody(is(expectedBody));
	}

	public static Matcher<ThrowableProblem> hasNoResponseBody() {
		return hasTextResponseBody("No body");
	}

	private static Matcher<ThrowableProblem> hasResponseBody(Matcher<?> valueMatcher) {
		return hasParameters(hasEntry(equalTo("responseBody"), valueMatcher));
	}

	public static Matcher<ThrowableProblem> hasRequestMethod(String expectedValue) {
		return hasParameters(hasEntry(equalTo("requestMethod"), is(expectedValue)));
	}

	public static Matcher<ThrowableProblem> hasRequestUrl(String expectedUrl) {
		return hasParameters(hasEntry(equalTo("requestUrl"), is(expectedUrl)));
	}

	public static Matcher<ThrowableProblem> hasRequestBody(Matcher<?> valueMatcher) {
		return hasParameters(hasEntry(equalTo("requestBody"), valueMatcher));
	}

	public static Matcher<ThrowableProblem> hasNoRequestBody() {
		return hasRequestBody(is("No body"));
	}
}
