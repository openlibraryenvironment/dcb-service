package org.olf.dcb.core.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;
import static org.olf.dcb.security.RoleNames.CONSORTIUM_ADMIN;
import static org.olf.dcb.security.RoleNames.LIBRARY_ADMIN;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.test.DcbTest;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;

@DcbTest
@TestInstance(PER_CLASS)
class ObjectRulesetControllerSecurityTests {

	private static final String ADMIN_TOKEN = "object-ruleset-tests-admin-token";
	private static final String CONSORTIUM_ADMIN_TOKEN = "object-ruleset-tests-consortium-admin-token";
	private static final String LIBRARY_ADMIN_TOKEN = "object-ruleset-tests-library-admin-token";

	@Inject
	@Client("/")
	private HttpClient client;

	@BeforeAll
	void beforeAll() {
		TestStaticTokenValidator.add(ADMIN_TOKEN, "a-support-troubleshooter", List.of(ADMINISTRATOR));
		TestStaticTokenValidator.add(CONSORTIUM_ADMIN_TOKEN, "a-consortium-admin", List.of(CONSORTIUM_ADMIN));
		TestStaticTokenValidator.add(LIBRARY_ADMIN_TOKEN, "a-library-admin", List.of(LIBRARY_ADMIN));
	}

	@AfterAll
	void afterAll() {
		TestStaticTokenValidator.invalidateToken(ADMIN_TOKEN);
		TestStaticTokenValidator.invalidateToken(CONSORTIUM_ADMIN_TOKEN);
		TestStaticTokenValidator.invalidateToken(LIBRARY_ADMIN_TOKEN);
	}

	@ParameterizedTest
	@ValueSource(strings = {ADMIN_TOKEN, CONSORTIUM_ADMIN_TOKEN})
	void anAdministratorMayListRulesets(String token) {
		final var response = client.toBlocking().exchange(
			HttpRequest.GET("/object-rules").bearerAuth(token));

		assertThat(response.getStatus(), is(HttpStatus.OK));
	}

	@ParameterizedTest
	@ValueSource(strings = {ADMIN_TOKEN, CONSORTIUM_ADMIN_TOKEN})
	void anAdministratorMayShowARuleset(String token) {
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(
				HttpRequest.GET("/object-rules/no-such-ruleset").bearerAuth(token)));

		assertThat(exception.getStatus(), is(HttpStatus.NOT_FOUND));
	}

	@Test
	void aConsortiumAdministratorMayNotWriteARuleset() {
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(
				HttpRequest.POST("/object-rules", Map.of("name", "not-written"))
					.bearerAuth(CONSORTIUM_ADMIN_TOKEN)));

		assertThat(exception.getStatus(), is(oneOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)));
	}

	@Test
	void aLibraryAdministratorMayNotReadRulesets() {
		final var exception = assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(
				HttpRequest.GET("/object-rules").bearerAuth(LIBRARY_ADMIN_TOKEN)));

		assertThat(exception.getStatus(), is(oneOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)));
	}
}
