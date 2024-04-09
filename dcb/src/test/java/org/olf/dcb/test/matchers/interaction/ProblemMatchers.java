package org.olf.dcb.test.matchers.interaction;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;

import org.hamcrest.Matcher;
import org.zalando.problem.Problem;

public class ProblemMatchers {
	public static Matcher<Problem> hasTitle(String expectedTitle) {
		return hasProperty("title", is(expectedTitle));
	}

	public static Matcher<Problem> hasDetail(String expectedDetail) {
		return hasProperty("detail", is(expectedDetail));
	}
}
