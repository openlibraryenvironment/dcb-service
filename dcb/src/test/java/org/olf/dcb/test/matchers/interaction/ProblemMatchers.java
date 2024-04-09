package org.olf.dcb.test.matchers.interaction;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasProperty;

import org.hamcrest.Matcher;
import org.olf.dcb.core.interaction.CannotPlaceRequestProblem;

public class ProblemMatchers {

	public static Matcher<CannotPlaceRequestProblem> hasTitle(
		String expectedTitle) {
		return hasProperty("title", is(expectedTitle));
	}

	public static Matcher<CannotPlaceRequestProblem> hasDetail(
		String expectedDetail) {
		return hasProperty("detail", is(expectedDetail));
	}
}
