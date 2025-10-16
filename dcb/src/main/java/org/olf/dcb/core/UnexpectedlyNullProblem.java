package org.olf.dcb.core;

import static java.util.Collections.emptyMap;

import java.net.URI;

import org.zalando.problem.AbstractThrowableProblem;

public class UnexpectedlyNullProblem extends AbstractThrowableProblem {
	public UnexpectedlyNullProblem(String description) {
		super(URI.create("https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/overview"),
			"Object was unexpectedly null: \"%s\"".formatted(description),
			null, null, null, null, emptyMap());
	}
}
