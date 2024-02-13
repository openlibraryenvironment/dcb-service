package org.olf.dcb.test.matchers;

import static org.hamcrest.Matchers.hasProperty;

import org.hamcrest.Matcher;
import org.zalando.problem.ThrowableProblem;

public class ThrowableProblemMatchers {
	public static Matcher<ThrowableProblem> hasParameters(Matcher<?> matcher) {
		return hasProperty("parameters", matcher);
	}
}
