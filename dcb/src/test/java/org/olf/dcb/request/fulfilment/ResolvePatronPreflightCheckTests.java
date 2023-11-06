package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;

@DcbTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResolvePatronPreflightCheckTests extends AbstractPreflightCheckTests {
	private static final String HOST_LMS_CODE = "host-lms";

	@Inject
	private ResolvePatronPreflightCheck check;

	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeAll
	void beforeAll() {
		final String BASE_URL = "https://resolve-patron-tests.com";
		final String KEY = "resolve-patron-key";
		final String SECRET = "resolve-patron-secret";

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(KEY, SECRET, BASE_URL, HOST_LMS_CODE);
	}

	@Test
	void shouldPass() {
		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localSystemCode("host-lms")
				.localId("345358")
				.build())
			.build();

		final var results = check.check(command).block();

		// Assert
		assertThat(results, containsInAnyOrder(passedCheck()));
	}
}
