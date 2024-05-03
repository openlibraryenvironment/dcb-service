package org.olf.dcb.core.interaction.polaris;

import java.net.URI;

import org.zalando.problem.AbstractThrowableProblem;

public class CannotGetPatronBlocksProblem extends AbstractThrowableProblem {
	CannotGetPatronBlocksProblem(Integer code, String message) {
		super(URI.create("https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/overview"),
			"Circulation blocks endpoint returned error code [%d] with message: %s".formatted(code, message));

	}
}
