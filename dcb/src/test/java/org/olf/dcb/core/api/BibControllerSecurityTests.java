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
 * Who may read a bib's match points and audit log. Each request names a bib that does not
 * exist, which both routes answer with an empty 200, so 200 means authorisation passed and
 * 401/403 means it did not.
 */
@DcbTest
@TestInstance(PER_CLASS)
class BibControllerSecurityTests {

	private static final String ADMIN_TOKEN = "bib-tests-admin-token";
	private static final String CONSORTIUM_ADMIN_TOKEN = "bib-tests-consortium-admin-token";
	private static final String LIBRARY_ADMIN_TOKEN = "bib-tests-library-admin-token";

	private static final UUID NO_SUCH_BIB = randomUUID();

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
	void anAdministratorMayReadABibsMatchPoints(String token) {
		assertThat(client.toBlocking().exchange(matchPoints().bearerAuth(token)).getStatus(),
			is(HttpStatus.OK));
	}

	@Test
	void aLibraryAdministratorMayNotReadABibsMatchPoints() {
		assertThat(refusalOf(matchPoints(), LIBRARY_ADMIN_TOKEN),
			is(oneOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)));
	}

	@Test
	void theAuditLogStaysWithTheAdministrator() {
		assertThat(client.toBlocking().exchange(auditLog().bearerAuth(ADMIN_TOKEN)).getStatus(),
			is(HttpStatus.OK));

		for (final var token : List.of(CONSORTIUM_ADMIN_TOKEN, LIBRARY_ADMIN_TOKEN)) {
			assertThat(token, refusalOf(auditLog(), token),
				is(oneOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)));
		}
	}

	private static MutableHttpRequest<?> matchPoints() {
		return HttpRequest.GET("/bibs/" + NO_SUCH_BIB + "/matchpoints");
	}

	private static MutableHttpRequest<?> auditLog() {
		return HttpRequest.GET("/bibs/" + NO_SUCH_BIB + "/audit-log");
	}

	private HttpStatus refusalOf(MutableHttpRequest<?> request, String token) {
		return assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(request.bearerAuth(token)))
			.getStatus();
	}
}
