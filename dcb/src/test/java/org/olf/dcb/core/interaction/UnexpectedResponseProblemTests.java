package org.olf.dcb.core.interaction;

import static io.micronaut.http.HttpResponse.badRequest;
import static io.micronaut.http.MediaType.APPLICATION_JSON_TYPE;
import static io.micronaut.http.MediaType.TEXT_PLAIN_TYPE;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.core.interaction.UnexpectedHttpResponseProblem.unexpectedResponseProblem;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasJsonResponseBodyProperty;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasMessageForHostLms;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasMessageForRequest;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasNoRequestBody;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasNoResponseBody;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasRequestBody;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasRequestMethod;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasRequestUrl;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasResponseStatusCode;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasTextResponseBody;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Builder;
import lombok.Value;

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

		final var url = "http://some-host-lms/make-request?parameter=true";

		final var problem = unexpectedResponseProblem(exception,
			HttpRequest.POST(url, null), "example-host-lms");

		// Assert
		assertThat(problem, allOf(
			hasMessageForHostLms("example-host-lms"),
			hasResponseStatusCode(400),
			hasJsonResponseBodyProperty("error", "something went wrong"),
			hasJsonResponseBodyProperty("code", 109),
			hasRequestMethod("POST"),
			hasRequestUrl(url)
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
			hasResponseStatusCode(400),
			hasTextResponseBody("something went wrong"),
			hasRequestMethod("GET")
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
			hasResponseStatusCode(400),
			hasNoResponseBody()
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
			hasResponseStatusCode(400),
			hasRequestMethod("Unknown")
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
	void shouldIncludeTextRequestBody() {
		// Act
		final var exception = createResponseException(badRequest());

		final var problem = unexpectedResponseProblem(exception,
			examplePostRequest()
				.contentType(TEXT_PLAIN_TYPE)
				.body("Some body"),
			"example-host-lms");

		// Assert
		assertThat(problem, allOf(
			hasMessageForHostLms("example-host-lms"),
			hasRequestBody(is("Some body"))
		));
	}

	@Test
	void shouldIncludeJsonRequestBody() {
		// Act
		final var exception = createResponseException(badRequest());

		final var requestBody = ExampleBody.builder()
			.id("6835489")
			.build();

		final var problem = unexpectedResponseProblem(exception,
			examplePostRequest()
				.contentType(APPLICATION_JSON_TYPE)
				.body(requestBody),
			"example-host-lms");

		// Assert
		assertThat(problem, allOf(
			hasMessageForHostLms("example-host-lms"),
			// The request body hasn't been serialised yet
			// so toString of the object is closest we can get
			// without invoking the serialisation manually
			hasRequestBody(is(requestBody.toString()))
		));
	}

	@Test
	void shouldTolerateNoRequestBody() {
		// Act
		final var exception = createResponseException(badRequest());

		final var problem = unexpectedResponseProblem(exception,
			examplePostRequest(), "example-host-lms");

		// Assert
		assertThat(problem, allOf(
			hasMessageForHostLms("example-host-lms"),
			hasNoRequestBody()
		));
	}

	@Test
	void shouldTolerateNullResponseException() {
		// Act
		final var problem = unexpectedResponseProblem(null,
			exampleGetRequest(), "example-host-lms");

		// Assert
		assertThat(problem, allOf(
			hasMessageForHostLms("example-host-lms"),
			hasResponseStatusCode("Unknown"),
			hasNoResponseBody()
		));
	}

	@Test
	void shouldTolerateNoRequestOrHostLmsCode() {
		// Act
		final var exception = createResponseException(badRequest());

		final var problem = unexpectedResponseProblem(exception, null, null);

		// Assert
		assertThat(problem, allOf(
			hasMessage("Unexpected response received for unknown request or Host LMS"),
			hasResponseStatusCode(400),
			hasRequestMethod("Unknown")
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

	@Serdeable
	@Builder
	@Value
	public static class ExampleBody {
		String id;
	}
}
