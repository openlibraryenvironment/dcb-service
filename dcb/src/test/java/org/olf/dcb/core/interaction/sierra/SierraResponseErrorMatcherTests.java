package org.olf.dcb.core.interaction.sierra;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.sierra.SierraResponseErrorMatcher;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import services.k_int.interaction.sierra.SierraError;

class SierraResponseErrorMatcherTests {
	private final SierraResponseErrorMatcher errorMatcher = new SierraResponseErrorMatcher();

	@Test
	void shouldNotBeNoRecordsWhenExceptionIsNotHttpClientResponseException() {
		assertThat(errorMatcher.isNoRecordsError(new RuntimeException()), is(false));
	}

	@Test
	void shouldNotBeNoRecordsFoundWhenStatusCodeIsAnythingExceptNotFound() {
		final var exception = new HttpClientResponseException("",
			HttpResponse.ok());

		assertThat(errorMatcher.isNoRecordsError(exception), is(false));
	}

	@Test
	void shouldNotBeNoRecordsFoundWhenBodyIsNotSierraError() {
		final var exception = new HttpClientResponseException("",
			HttpResponse.notFound("Some message"));

		assertThat(errorMatcher.isNoRecordsError(exception), is(false));
	}

	@Test
	void shouldNotBeNoRecordsFoundWhenCodeIsAnythingExcept107() {
		final var exception = new HttpClientResponseException("",
			HttpResponse.notFound(createSierraError(345)));

		assertThat(errorMatcher.isNoRecordsError(exception), is(false));
	}

	@Test
	void shouldBeNoRecordsFoundWhenCodeIs107() {
		final var exception = new HttpClientResponseException("",
			HttpResponse.notFound().body(createSierraError(107)));

		assertThat(errorMatcher.isNoRecordsError(exception), is(true));
	}

	private static SierraError createSierraError(int code) {
		return new SierraError("", code, 0, "", "");
	}
}
