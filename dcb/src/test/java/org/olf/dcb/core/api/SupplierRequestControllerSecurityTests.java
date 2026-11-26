package org.olf.dcb.core.api;

import static java.util.UUID.randomUUID;
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
import java.util.UUID;

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
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;

/**
 * Who may reach the supplier request routes. Each request names a supplier request that does
 * not exist, so 404 means authorisation passed and 401/403 means it did not.
 */
@DcbTest
@TestInstance(PER_CLASS)
class SupplierRequestControllerSecurityTests {

	private static final String ADMIN_TOKEN = "supplier-request-tests-admin-token";
	private static final String CONSORTIUM_ADMIN_TOKEN = "supplier-request-tests-consortium-admin-token";
	private static final String LIBRARY_ADMIN_TOKEN = "supplier-request-tests-library-admin-token";

	private static final UUID NO_SUCH_SUPPLIER_REQUEST = randomUUID();

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
	void anAdministratorMaySkipASupplier(String token) {
		assertThat(statusOf(skip(), token), is(HttpStatus.NOT_FOUND));
	}

	@ParameterizedTest
	@ValueSource(strings = {ADMIN_TOKEN, CONSORTIUM_ADMIN_TOKEN})
	void anAdministratorMayCancelASupplierHold(String token) {
		assertThat(statusOf(cancel(), token), is(HttpStatus.NOT_FOUND));
	}

	@ParameterizedTest
	@ValueSource(strings = {ADMIN_TOKEN, CONSORTIUM_ADMIN_TOKEN})
	void anAdministratorMayReactivateASupplierRequest(String token) {
		assertThat(statusOf(reactivate(), token), is(HttpStatus.NOT_FOUND));
	}

	@Test
	void aLibraryAdministratorMayNotChangeASupplierRequest() {
		for (final var request : List.of(skip(), cancel(), reactivate())) {
			assertThat(request.getPath(), statusOf(request, LIBRARY_ADMIN_TOKEN),
				is(oneOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)));
		}
	}

	private static MutableHttpRequest<?> skip() {
		return HttpRequest.POST("/suppliers/requests/" + NO_SUCH_SUPPLIER_REQUEST + "/skip", Map.of());
	}

	private static MutableHttpRequest<?> cancel() {
		return HttpRequest.POST("/suppliers/requests/" + NO_SUCH_SUPPLIER_REQUEST + "/cancel",
			Map.of("reason", "borrowing side failed"));
	}

	private static MutableHttpRequest<?> reactivate() {
		return HttpRequest.PATCH("/suppliers/requests/" + NO_SUCH_SUPPLIER_REQUEST + "/active",
			Map.of("active", true));
	}

	private HttpStatus statusOf(MutableHttpRequest<?> request, String token) {
		return assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(request.bearerAuth(token)))
			.getStatus();
	}
}
