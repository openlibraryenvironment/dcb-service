package org.olf.dcb.core.interaction;

import static io.micronaut.http.HttpResponse.badRequest;
import static io.micronaut.http.MediaType.APPLICATION_JSON_TYPE;
import static io.micronaut.http.MediaType.TEXT_PLAIN_TYPE;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.core.interaction.UnexpectedHttpResponseProblem.unexpectedResponseProblem;
import static org.olf.dcb.test.matchers.interaction.UnexpectedResponseProblemMatchers.hasJsonResponseBodyParameter;
import static org.olf.dcb.test.matchers.interaction.UnexpectedResponseProblemMatchers.hasMessageForHostLms;
import static org.olf.dcb.test.matchers.interaction.UnexpectedResponseProblemMatchers.hasResponseStatusCodeParameter;
import static org.olf.dcb.test.matchers.interaction.UnexpectedResponseProblemMatchers.hasTextResponseBodyParameter;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

class UnexpectedResponseProblemTests {
	@Test
	void shouldCreateProblemFromJsonResponse() {
		// Act
		final var exception = createResponseException(badRequest()
			.contentType(APPLICATION_JSON_TYPE)
			.body(Map.of("error", "something went wrong")));

		final var problem = unexpectedResponseProblem(exception, "example-host-lms");

		// Assert
		assertThat(problem, allOf(
			hasMessageForHostLms("example-host-lms"),
			hasResponseStatusCodeParameter(400),
			hasJsonResponseBodyParameter(Map.of("error", "something went wrong"))
		));
	}

	@Test
	void shouldCreateProblemFromTextResponse() {
		// Act
		final var exception = createResponseException(badRequest()
			.contentType(TEXT_PLAIN_TYPE)
			.body("something went wrong"));

		final var problem = unexpectedResponseProblem(exception, "example-host-lms");

		// Assert
		assertThat(problem, allOf(
			hasMessageForHostLms("example-host-lms"),
			hasResponseStatusCodeParameter(400),
			hasTextResponseBodyParameter("something went wrong")
		));
	}

	@Test
	void shouldCreateProblemFromResponseWithNoBody() {
		// Act
		final var exception = createResponseException(badRequest());

		final var problem = unexpectedResponseProblem(exception, "example-host-lms");

		// Assert
		assertThat(problem, allOf(
			hasMessageForHostLms("example-host-lms"),
			hasResponseStatusCodeParameter(400),
			hasTextResponseBodyParameter("No body")
		));
	}

	private static <T> HttpClientResponseException createResponseException(
		MutableHttpResponse<T> response) {

		return new HttpClientResponseException("", response);
	}
}
