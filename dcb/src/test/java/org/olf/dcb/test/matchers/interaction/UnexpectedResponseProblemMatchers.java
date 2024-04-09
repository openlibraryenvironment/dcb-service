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

	public static Matcher<ThrowableProblem> hasResponseStatusCodeParameter(
		Object expectedStatusCode) {

		return hasParameters(hasEntry(equalTo("responseStatusCode"), is(expectedStatusCode)));
	}

	public static Matcher<ThrowableProblem> hasJsonResponseBodyProperty(
		String expectedPropertyName, Object expectedValue) {

		return hasResponseBodyParameter(hasEntry(equalTo(expectedPropertyName), is(expectedValue)));
	}

	public static Matcher<ThrowableProblem> hasTextResponseBodyParameter(String expectedBody) {
		return hasResponseBodyParameter(is(expectedBody));
	}

	public static Matcher<ThrowableProblem> hasNoResponseBodyParameter() {
		return hasTextResponseBodyParameter("No body");
	}

	private static Matcher<ThrowableProblem> hasResponseBodyParameter(Matcher<?> valueMatcher) {
		return hasParameters(hasEntry(equalTo("responseBody"), valueMatcher));
	}

	public static Matcher<ThrowableProblem> hasRequestMethodParameter(String expectedValue) {
		return hasParameters(hasEntry(equalTo("requestMethod"), is(expectedValue)));
	}

	public static Matcher<ThrowableProblem> hasRequestUrlParameter(String expectedUrl) {
		return hasParameters(hasEntry(equalTo("requestUrl"), is(expectedUrl)));
	}

	public static Matcher<ThrowableProblem> hasRequestBodyParameter(Matcher<?> valueMatcher) {
		return hasParameters(hasEntry(equalTo("requestBody"), valueMatcher));
	}

	public static Matcher<ThrowableProblem> hasNoRequestBody() {
		return hasRequestBodyParameter(is("No body"));
	}
}
