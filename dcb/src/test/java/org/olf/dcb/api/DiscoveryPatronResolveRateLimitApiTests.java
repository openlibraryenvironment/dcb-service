package org.olf.dcb.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.security.RoleNames.DISCOVERY_SERVICE;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.test.DcbTest;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;

/**
 * Each resolve is a live read at a member library, so one trusted service resolves at most
 * dcb.discovery.patron-resolves-per-minute sign-ins a minute.
 */
@DcbTest
@Property(name = "dcb.discovery.enabled", value = "true")
@Property(name = "dcb.discovery.patron-resolves-per-minute", value = "2")
@Property(name = "dcb.discovery.trusted-services-json", value = """
	[{"service-id": "a-limited-service", "issuer": "https://limited.test.invalid",
	  "jwks-uri": "https://limited.test.invalid/jwks.json", "client-id": "limited-client"}]""")
@TestInstance(PER_CLASS)
class DiscoveryPatronResolveRateLimitApiTests {
	private static final String ACCESS_TOKEN = "discovery-resolve-limit-token";

	@Inject
	@Client("/")
	private HttpClient client;

	@BeforeAll
	void beforeAll() {
		TestStaticTokenValidator.add(ACCESS_TOKEN, "a-limited-service",
			List.of(DISCOVERY_SERVICE), Map.of("azp", "limited-client"));
	}

	@Test
	void shouldRefuseAServiceThatHasResolvedItsLimitForTheMinute() {
		// An agency DCB does not know is answered without asking any library
		assertThat(statusOf(), is(HttpStatus.NOT_FOUND));
		assertThat(statusOf(), is(HttpStatus.NOT_FOUND));

		final var refused = assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(request()));

		assertThat(refused.getStatus(), is(HttpStatus.TOO_MANY_REQUESTS));
		assertThat(refused.getResponse().getHeaders().get("Retry-After"), is("60"));
	}

	private HttpStatus statusOf() {
		return assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(request())).getStatus();
	}

	private HttpRequest<?> request() {
		return HttpRequest.POST("/discovery/patrons/resolve",
				Map.of("agencyCode", "no-such-agency", "principal", "someone"))
			.bearerAuth(ACCESS_TOKEN);
	}
}
