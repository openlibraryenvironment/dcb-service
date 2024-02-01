package org.olf.dcb.test.matchers.interaction;

import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;

import java.util.Map;

import org.hamcrest.Matcher;
import org.zalando.problem.ThrowableProblem;

public class UnexpectedResponseProblemMatchers {
	public static Matcher<ThrowableProblem> hasMessageForHostLms(String expectedHostLmsCode) {
		return hasMessage("Unexpected response from Host LMS: \"%s\""
			.formatted(expectedHostLmsCode));
	}

	public static Matcher<ThrowableProblem> hasResponseStatusCodeParameter(
		Integer expectedStatusCode) {

		return hasProperty("parameters", hasEntry("responseStatusCode", expectedStatusCode));
	}

	public static Matcher<ThrowableProblem> hasJsonResponseBodyParameter(
		Map<String, String> expectedProperties) {

		return hasProperty("parameters", hasEntry("responseBody", expectedProperties));
	}

	public static Matcher<ThrowableProblem> hasTextResponseBodyParameter(
		String expectedBody) {

		return hasProperty("parameters", hasEntry("responseBody", expectedBody));
	}

	public static Matcher<ThrowableProblem> hasRequestMethodParameter(
		String expectedValue) {
		return hasProperty("parameters", hasEntry("requestMethod", expectedValue));
	}
}
