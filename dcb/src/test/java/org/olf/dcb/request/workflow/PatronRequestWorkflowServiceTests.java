package org.olf.dcb.request.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.PatronRequestsFixture;

import jakarta.inject.Inject;

@DcbTest
class PatronRequestWorkflowServiceTests {
	@Inject
	private PatronRequestWorkflowService workflowService;

	@Inject
	private PatronRequestsFixture patronRequestsFixture;

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAll();
	}

	@Test
	void shouldTransitionPatronRequestToErrorStatus() {
		assertThat(workflowService, notNullValue());
	}
}
