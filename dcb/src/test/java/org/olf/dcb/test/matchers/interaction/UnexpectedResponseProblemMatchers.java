package org.olf.dcb.test.matchers.interaction;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.equalTo;
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

		return hasParameters(hasEntry(equalTo("responseStatusCode"), is(expectedStatusCode)));
	}

	public static Matcher<ThrowableProblem> hasJsonResponseBodyParameter(
		Map<String, Object> expectedProperties) {

		return hasParameters(hasEntry(equalTo("responseBody"), is(expectedProperties)));
	}

	public static Matcher<ThrowableProblem> hasTextResponseBodyParameter(String expectedBody) {
		return hasParameters(hasEntry(equalTo("responseBody"), is(expectedBody)));
	}

	public static Matcher<ThrowableProblem> hasRequestMethodParameter(String expectedValue) {
		return hasParameters(hasEntry(equalTo("requestMethod"), is(expectedValue)));
	}

	static Matcher<ThrowableProblem> hasParameters(
		Matcher<Map<? extends String, ? extends Object>> matcher) {

		return hasProperty("parameters", matcher);
	}
}
