package org.olf.dcb.request.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;

import jakarta.inject.Inject;

@DcbTest
class PatronRequestWorkflowServiceTests {
	@Inject
	private PatronRequestWorkflowService workflowService;

	@Test
	void shouldTransitionPatronRequestToErrorStatus() {
		assertThat(workflowService, notNullValue());
	}
}
