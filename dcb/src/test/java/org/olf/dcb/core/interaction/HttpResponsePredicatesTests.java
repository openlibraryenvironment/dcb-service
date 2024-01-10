package org.olf.dcb.core.interaction;

import static io.micronaut.http.HttpResponse.ok;
import static io.micronaut.http.HttpResponse.unauthorized;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.core.interaction.HttpResponsePredicates.isUnauthorised;

import org.junit.jupiter.api.Test;

import io.micronaut.http.client.exceptions.HttpClientResponseException;

public class HttpResponsePredicatesTests {
	@Test
	void shouldNotBeUnauthorisedWhenStatusCodeIsAnythingExceptUnauthorised() {
		final var exception = new HttpClientResponseException("", ok());

		assertThat(isUnauthorised(exception), is(false));
	}

	@Test
	void shouldBeUnauthorisedWhenStatusCodeIsUnauthorised() {
		final var exception = new HttpClientResponseException("", unauthorized());

		assertThat(isUnauthorised(exception), is(true));
	}
}
