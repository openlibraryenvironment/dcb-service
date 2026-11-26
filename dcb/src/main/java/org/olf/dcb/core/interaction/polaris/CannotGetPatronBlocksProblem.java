package org.olf.dcb.core.interaction.polaris;

import java.net.URI;

import org.zalando.problem.AbstractThrowableProblem;

public class CannotGetPatronBlocksProblem extends AbstractThrowableProblem {
	private static final URI TYPE = URI.create("https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/overview");

	CannotGetPatronBlocksProblem(Integer code, String message) {
		this("Circulation blocks endpoint returned error code [%d] with message: %s".formatted(code, message));
	}

	private CannotGetPatronBlocksProblem(String title) {
		super(TYPE, title);
	}

	static CannotGetPatronBlocksProblem withoutCanPatronCirculate() {
		return new CannotGetPatronBlocksProblem(
			"Circulation blocks endpoint did not say whether the patron can circulate");
	}
}
