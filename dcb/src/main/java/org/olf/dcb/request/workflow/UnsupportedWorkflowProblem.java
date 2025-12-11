package org.olf.dcb.request.workflow;

import java.net.URI;

import org.zalando.problem.AbstractThrowableProblem;

public class UnsupportedWorkflowProblem extends AbstractThrowableProblem {
	public UnsupportedWorkflowProblem(String workflowDescription) {
		super(URI.create("https://openlibraryfoundation.atlassian.net/wiki/spaces/DCB/overview"),
			"Unsupported workflow: %s".formatted(workflowDescription));
	}
}
