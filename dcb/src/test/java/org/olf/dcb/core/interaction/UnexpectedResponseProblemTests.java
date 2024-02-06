package org.olf.dcb.core.interaction;

import static io.micronaut.http.HttpResponse.badRequest;
import static io.micronaut.http.MediaType.APPLICATION_JSON_TYPE;
import static io.micronaut.http.MediaType.TEXT_PLAIN_TYPE;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.core.interaction.UnexpectedHttpResponseProblem.unexpectedResponseProblem;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;
import static org.olf.dcb.test.matchers.interaction.UnexpectedResponseProblemMatchers.hasJsonResponseBodyProperty;
import static org.olf.dcb.test.matchers.interaction.UnexpectedResponseProblemMatchers.hasMessageForHostLms;
import static org.olf.dcb.test.matchers.interaction.UnexpectedResponseProblemMatchers.hasMessageForRequest;
import static org.olf.dcb.test.matchers.interaction.UnexpectedResponseProblemMatchers.hasNoResponseBodyParameter;
import static org.olf.dcb.test.matchers.interaction.UnexpectedResponseProblemMatchers.hasRequestMethodParameter;
import static org.olf.dcb.test.matchers.interaction.UnexpectedResponseProblemMatchers.hasResponseStatusCodeParameter;
import static org.olf.dcb.test.matchers.interaction.UnexpectedResponseProblemMatchers.hasTextResponseBodyParameter;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

class UnexpectedResponseProblemTests {
	@Test
	void shouldCreateProblemFromJsonResponse() {
		// Act
		final var exception = createResponseException(badRequest()
			.contentType(APPLICATION_JSON_TYPE)
			.body(Map.of(
				"error", "something went wrong",
				"code", 109
			)));

		final var problem = unexpectedResponseProblem(exception,
			examplePostRequest(), "example-host-lms");

		// Assert
		assertThat(problem, allOf(
			hasMessageForHostLms("example-host-lms"),
			hasResponseStatusCodeParameter(400),
			hasJsonResponseBodyProperty("error", "something went wrong"),
			hasJsonResponseBodyProperty("code", 109),
			hasRequestMethodParameter("POST")
		));
	}

	@Test
	void shouldCreateProblemFromTextResponse() {
		// Act
		final var exception = createResponseException(badRequest()
			.contentType(TEXT_PLAIN_TYPE)
			.body("something went wrong"));

		final var problem = unexpectedResponseProblem(exception,
			exampleGetRequest(), "example-host-lms");

		// Assert
		assertThat(problem, allOf(
			hasMessageForHostLms("example-host-lms"),
			hasResponseStatusCodeParameter(400),
			hasTextResponseBodyParameter("something went wrong"),
			hasRequestMethodParameter("GET")
		));
	}

	@Test
	void shouldCreateProblemFromResponseWithNoResponseBody() {
		// Act
		final var exception = createResponseException(badRequest());

		final var problem = unexpectedResponseProblem(exception,
			exampleGetRequest(), "example-host-lms");

		// Assert
		assertThat(problem, allOf(
			hasMessageForHostLms("example-host-lms"),
			hasResponseStatusCodeParameter(400),
			hasNoResponseBodyParameter()
		));
	}

	@Test
	void shouldCreateProblemFromResponseWithNoRequest() {
		// Act
		final var exception = createResponseException(badRequest());

		final var problem = unexpectedResponseProblem(exception, null,
			"example-host-lms");

		// Assert
		assertThat(problem, allOf(
			hasMessageForHostLms("example-host-lms"),
			hasResponseStatusCodeParameter(400),
			hasRequestMethodParameter(null)
		));
	}

	@Test
	void shouldCreateProblemFromResponseWithNoHostLmsCode() {
		// Act
		final var exception = createResponseException(badRequest());

		final var problem = unexpectedResponseProblem(exception,
			HttpRequest.GET("http://some-host-lms/some-path"), null);

		// Assert
		assertThat(problem, hasMessageForRequest("GET", "/some-path"));
	}

	@Test
	void shouldTolerateNoRequestOrHostLmsCode() {
		// Act
		final var exception = createResponseException(badRequest());

		final var problem = unexpectedResponseProblem(exception, null, null);

		// Assert
		assertThat(problem, allOf(
			hasMessage("Unexpected response received for unknown request or Host LMS"),
			hasResponseStatusCodeParameter(400),
			hasRequestMethodParameter(null)
		));
	}

	private static <T> HttpClientResponseException createResponseException(
		MutableHttpResponse<T> response) {

		return new HttpClientResponseException("", response);
	}

	private static MutableHttpRequest<Object> examplePostRequest() {
		return HttpRequest.POST("http://some-host-lms", null);
	}

	private static MutableHttpRequest<Object> exampleGetRequest() {
		return HttpRequest.GET("http://some-host-lms");
	}
}
