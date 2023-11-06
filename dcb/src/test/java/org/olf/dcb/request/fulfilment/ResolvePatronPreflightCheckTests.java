package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResolvePatronPreflightCheckTests extends AbstractPreflightCheckTests {
	private static final String HOST_LMS_CODE = "host-lms";

	@Inject
	private ResolvePatronPreflightCheck check;

	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		final String BASE_URL = "https://resolve-patron-tests.com";
		final String KEY = "resolve-patron-key";
		final String SECRET = "resolve-patron-secret";

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, "test-token", 60);

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(KEY, SECRET, BASE_URL, HOST_LMS_CODE);
	}

	@Test
	void shouldPassWhenHostLmsIsRecognised() {
		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localSystemCode(HOST_LMS_CODE)
				.localId("345358")
				.build())
			.build();

		final var results = check.check(command).block();

		// Assert
		assertThat(results, containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldFailWhenHostLmsIsNotRecognised() {
		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localSystemCode("unknown-host-lms")
				.build())
			.build();

		final var results = check.check(command).block();

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("\"unknown-host-lms\" is not a recognised host LMS")));
	}
}
