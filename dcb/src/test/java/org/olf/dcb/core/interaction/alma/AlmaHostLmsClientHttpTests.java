package org.olf.dcb.core.interaction.alma;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.olf.dcb.test.MockServerCommonResponses.okJson;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.Times;
import org.mockserver.verify.VerificationTimes;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.test.HostLmsFixture;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class AlmaHostLmsClientHttpTests {
	private static final String HOST_LMS_CODE = "alma-http";
	private static final String BASE_URL = "https://alma-http-tests.com";
	private static final String AUTH_PROFILE = "BASIC/BARCODE+PASSWORD";

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockServerClient mockServerClient;
	private HostLmsClient client;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		this.mockServerClient = mockServerClient;

		hostLmsFixture.deleteAll();
		hostLmsFixture.createAlmaHostLms(HOST_LMS_CODE, BASE_URL);
	}

	@BeforeEach
	void beforeEach() {
		mockServerClient.reset();
		client = hostLmsFixture.createClient(HOST_LMS_CODE);
	}

	@Test
	void shouldSendThePasswordInAHeaderAndNeverInAUrl() {
		mockServerClient.when(request()
				.withMethod("POST")
				.withPath("/almaws/v1/users/BAR1")
				.withQueryStringParameter("op", "auth")
				.withHeader("Exl-User-Pw", "correct-5518"))
			.respond(response().withStatusCode(204));

		mockServerClient.when(request()
				.withMethod("GET")
				.withPath("/almaws/v1/users/BAR1"))
			.respond(okJson(almaUser("BAR1")));

		final var patron = singleValueFrom(client.patronAuth(AUTH_PROFILE, "BAR1", "correct-5518"));

		assertThat(patron.getLocalId(), contains("BAR1"));

		final var recorded = mockServerClient.retrieveRecordedRequests(request());

		assertThat(Arrays.stream(recorded)
			.anyMatch(recordedRequest -> recordedRequest.getQueryStringParameters() != null
				&& recordedRequest.getQueryStringParameters().toString().contains("correct-5518")), is(false));
	}

	@Test
	void shouldTreatARejectedPasswordAsAnInvalidLogin() {
		mockServerClient.when(request()
				.withMethod("POST")
				.withPath("/almaws/v1/users/BAR2")
				.withQueryStringParameter("op", "auth"))
			.respond(response().withStatusCode(400)
				.withBody(json(almaError("401866", "User authentication failed"))));

		final var patron = singleValueFrom(client.patronAuth(AUTH_PROFILE, "BAR2", "wrong"));

		assertThat(patron, is(nullValue()));

		mockServerClient.verify(request()
			.withMethod("GET")
			.withPath("/almaws/v1/users/BAR2"), VerificationTimes.never());
	}

	@Test
	void shouldKeepHeadersAndBodiesOutOfTheProblemRaisedForAnAlmaError() {
		mockServerClient.when(request()
				.withMethod("GET")
				.withPath("/almaws/v1/users/BAR3"))
			.respond(response().withStatusCode(400)
				.withBody(json(almaError("401861", "User with identifier BAR3 was not found"))));

		final var problem = assertThrows(ThrowableProblem.class,
			() -> client.getPatronByLocalId("BAR3").block());

		assertThat(problem.getParameters(), not(hasKey("Request Headers")));
		assertThat(problem.getParameters(), not(hasKey("Request Body")));
		assertThat(problem.getParameters(), not(hasKey("Raw Error Body")));
		assertThat(problem.getParameters().toString(), not(containsString("alma-api-key")));
		assertThat(problem.getParameters(), hasKey("Alma Error response"));
	}

	private static Map<String, Object> almaUser(String primaryId) {
		return Map.of(
			"primary_id", primaryId,
			"first_name", "Test",
			"last_name", "Patron",
			"user_group", Map.of("value", "UNDRGRD"));
	}

	private static Map<String, Object> almaError(String code, String message) {
		return Map.of(
			"errorsExist", true,
			"errorList", Map.of("error", List.of(Map.of("errorCode", code, "errorMessage", message))));
	}
}
