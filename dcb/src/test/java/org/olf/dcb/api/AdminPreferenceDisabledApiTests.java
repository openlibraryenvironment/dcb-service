package org.olf.dcb.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.api.serde.PreferenceAvailability;
import org.olf.dcb.core.api.serde.PreferenceInputs.SaveDisplay;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.test.DcbTest;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;

/** A deployment that declines preferences starts, says so on /availability, and answers 503. */
@DcbTest
@Property(name = "dcb.preferences.enabled", value = "false")
@TestInstance(PER_CLASS)
class AdminPreferenceDisabledApiTests {
	private static final String ADMIN_TOKEN = "preferences-disabled-tests-admin";

	@Inject
	@Client("/")
	private HttpClient client;

	@BeforeAll
	void beforeAll() {
		TestStaticTokenValidator.add(ADMIN_TOKEN, "an-admin", List.of(ADMINISTRATOR),
			Map.of("sub", "disabled-tests-subject-0001"));
	}

	@AfterAll
	void afterAll() {
		TestStaticTokenValidator.invalidateToken(ADMIN_TOKEN);
	}

	@Test
	void availabilityAnswers200AndSaysItIsOff() {
		final var availability = client.toBlocking().retrieve(
			HttpRequest.GET("/preferences/availability").bearerAuth(ADMIN_TOKEN), PreferenceAvailability.class);

		assertThat(availability.available(), is(false));
	}

	@Test
	void everyDataRouteAnswers503RatherThan404() {
		for (HttpRequest<?> request : List.<HttpRequest<?>>of(
			HttpRequest.GET("/preferences"),
			HttpRequest.PUT("/preferences/display", new SaveDisplay("dark", null, null, null, null, null)),
			HttpRequest.PUT("/preferences/apps/libraries", Map.of()),
			HttpRequest.GET("/preferences/views"),
			HttpRequest.GET("/preferences/watched"))) {

			final var error = assertThrows(HttpClientResponseException.class,
				() -> client.toBlocking().exchange(((io.micronaut.http.MutableHttpRequest<?>) request).bearerAuth(ADMIN_TOKEN), String.class));

			assertThat(request.getPath(), error.getStatus(), is(HttpStatus.SERVICE_UNAVAILABLE));
			assertThat(error.getResponse().getBody(String.class).orElse(""), containsString("PREFERENCES_UNAVAILABLE"));
		}
	}

	@Test
	void theRoutesAreStillSecuredWhileOff() {
		final var error = assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(HttpRequest.GET("/preferences/availability"), String.class));

		assertThat(error.getStatus(), is(oneOf(HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)));
	}
}
