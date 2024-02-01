package org.olf.dcb.test.matchers.interaction;

import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasProperty;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;

import org.hamcrest.Matcher;
import org.zalando.problem.DefaultProblem;

public class UnexpectedResponseProblemMatchers {
	public static Matcher<DefaultProblem> hasMessageForHostLms(String expectedHostLmsCode) {
		return hasMessage("Unexpected response from Host LMS: \"%s\""
			.formatted(expectedHostLmsCode));
	}

	public static Matcher<DefaultProblem> hasResponseStatusCodeParameter(
		Integer expectedStatusCode) {

		return hasProperty("parameters", hasEntry("responseStatusCode", expectedStatusCode));
	}
}
