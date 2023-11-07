package org.olf.dcb.request.fulfilment;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasProperty;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.core.io.ResourceLoader;
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
	private ResourceLoader loader;

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

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

	@BeforeEach
	void beforeEach() {
		referenceValueMappingFixture.deleteAll();
	}

	@Test
	void shouldPassWhenPatronCanBeFoundInHostLms(MockServerClient mockServerClient) {
		// Arrange
		final var LOCAL_ID = "345358";

		final var sierraPatronsAPIFixture = new SierraPatronsAPIFixture(mockServerClient, loader);

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(LOCAL_ID);

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			HOST_LMS_CODE, 10, 25, "DCB", "15");

		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localSystemCode(HOST_LMS_CODE)
				.localId(LOCAL_ID)
				.build())
			.build();

		final var results = check.check(command).block();

		// Assert
		assertThat(results, containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldFailWhenPatronCannotBeFoundInHostLms(MockServerClient mockServerClient) {
		final var LOCAL_ID = "673825";

		final var sierraPatronsAPIFixture = new SierraPatronsAPIFixture(mockServerClient, loader);

		sierraPatronsAPIFixture.noRecordsFoundWhenGettingPatronByLocalId(LOCAL_ID);

		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localSystemCode(HOST_LMS_CODE)
				.localId(LOCAL_ID)
				.build())
			.build();

		final var results = check.check(command).block();

		// Assert
		assertThat(results, containsInAnyOrder(failedCheck(
			"Patron \"" + LOCAL_ID + "\" is not recognised in \"" + HOST_LMS_CODE + "\"")));
	}

	@Test
	void shouldFailWhenLocalPatronTypeIsNotMappedToCanonicalPatronType(MockServerClient mockServerClient) {
		// Arrange
		final var LOCAL_ID = "578374";

		final var sierraPatronsAPIFixture = new SierraPatronsAPIFixture(mockServerClient, loader);

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(LOCAL_ID);

		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localSystemCode(HOST_LMS_CODE)
				.localId(LOCAL_ID)
				.build())
			.build();

		final var exception = assertThrows(RuntimeException.class, () -> check.check(command).block());

		// Assert
		assertThat(exception, hasProperty("message",
			is("Unable to map patronType host-lms:15 To DCB context")));
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
