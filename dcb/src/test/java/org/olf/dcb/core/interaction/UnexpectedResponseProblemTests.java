package org.olf.dcb.core.interaction;

import static io.micronaut.http.HttpResponse.badRequest;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.core.interaction.UnexpectedHttpResponseProblem.unexpectedResponseProblem;
import static org.olf.dcb.test.matchers.interaction.UnexpectedResponseProblemMatchers.hasMessageForHostLms;
import static org.olf.dcb.test.matchers.interaction.UnexpectedResponseProblemMatchers.hasResponseStatusCodeParameter;

import org.junit.jupiter.api.Test;

import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

class UnexpectedResponseProblemTests {
	@Test
	void shouldCreateProblemFromHttpClientResponseException() {
		// Act
		final var exception = createResponseException(badRequest());

		final var problem = unexpectedResponseProblem(exception, "example-host-lms");

		// Assert
		assertThat(problem, allOf(
			hasMessageForHostLms("example-host-lms"),
			hasResponseStatusCodeParameter(400)
		));
	}

	private static <T> HttpClientResponseException createResponseException(
		MutableHttpResponse<T> response) {

		return new HttpClientResponseException("", response);
	}
}
