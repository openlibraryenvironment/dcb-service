package org.olf.dcb.core.interaction;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.olf.dcb.core.interaction.UnexpectedHttpResponseProblem.OUTCOME;
import static org.olf.dcb.core.interaction.UnexpectedHttpResponseProblem.UNKNOWN_TIMEOUT;
import static org.olf.dcb.core.interaction.UnexpectedHttpResponseProblem.hasUnknownOutcome;
import static org.olf.dcb.core.interaction.UnexpectedHttpResponseProblem.unexpectedResponseProblem;

import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.sierra.SierraReadTimeoutProblem;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.exceptions.ReadTimeoutException;

class UnexpectedHttpResponseProblemTests {
	private static final HttpRequest<?> REQUEST = HttpRequest.POST("/patrons/1/holds/requests", "");

	@Test
	void shouldRecordATimeoutFoundInTheCauseChain() {
		final var problem = unexpectedResponseProblem(
			new IllegalStateException("placing hold", ReadTimeoutException.TIMEOUT_EXCEPTION),
			REQUEST, "borrower");

		assertThat(problem.getParameters(), hasEntry(OUTCOME, UNKNOWN_TIMEOUT));
		assertThat(hasUnknownOutcome(problem), is(true));
	}

	@Test
	void shouldRecordAReactorStyleTimeout() {
		final var problem = unexpectedResponseProblem(new TimeoutException("Did not observe any item"),
			REQUEST, "borrower");

		assertThat(problem.getParameters(), hasEntry(OUTCOME, UNKNOWN_TIMEOUT));
	}

	@Test
	void shouldRecordTheTimeoutOnASierraReadTimeoutProblem() {
		final var problem = new SierraReadTimeoutProblem(ReadTimeoutException.TIMEOUT_EXCEPTION,
			REQUEST, "sierra");

		assertThat(problem.getParameters(), hasEntry(OUTCOME, UNKNOWN_TIMEOUT));
	}

	@Test
	void shouldNotRecordAnOutcomeForAFailureThatIsNotATimeout() {
		final var problem = unexpectedResponseProblem(new IllegalStateException("connection closed"),
			REQUEST, "borrower");

		assertThat(problem.getParameters(), not(hasKey(OUTCOME)));
		assertThat(hasUnknownOutcome(problem), is(false));
	}

	@Test
	void shouldFindARawTimeoutUnderAnotherError() {
		assertThat(hasUnknownOutcome(new RuntimeException("placing hold",
			ReadTimeoutException.TIMEOUT_EXCEPTION)), is(true));
	}

	@Test
	void shouldStopAtACycleInTheCauseChain() {
		final var first = new RuntimeException("first");
		final var second = new RuntimeException("second", first);
		first.initCause(second);

		assertThat(hasUnknownOutcome(first), is(false));
		assertThat(unexpectedResponseProblem(first, REQUEST, "borrower").getParameters(),
			not(hasKey(OUTCOME)));
	}
}
