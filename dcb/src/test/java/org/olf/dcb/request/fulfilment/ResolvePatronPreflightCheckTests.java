package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture.Patron;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import jakarta.inject.Inject;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class ResolvePatronPreflightCheckTests extends AbstractPreflightCheckTests {
	private static final String BORROWING_HOST_LMS_CODE = "borrowing-host-lms";

	@Inject
	private ResolvePatronPreflightCheck check;

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;

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

		hostLmsFixture.createSierraHostLms(BORROWING_HOST_LMS_CODE, KEY, SECRET, BASE_URL, "item");
	}

	@BeforeEach
	void beforeEach() {
		referenceValueMappingFixture.deleteAll();
	}

	@Test
	void shouldPassWhenPatronCanBeFoundInHostLms(MockServerClient mockServerClient) {
		// Arrange
		final var localPatronId = "345358";

		final var sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localPatronId,
			Patron.builder()
				.id(1000002)
				.patronType(15)
				.homeLibraryCode("home-library")
				.barcodes(List.of("647647746"))
				.names(List.of("Bob"))
				.build());

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			BORROWING_HOST_LMS_CODE, 15, 15, "DCB", "UNDERGRAD");

		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localSystemCode(BORROWING_HOST_LMS_CODE)
				.localId(localPatronId)
				.build())
			.build();

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldFailWhenPatronCannotBeFoundInHostLms(MockServerClient mockServerClient) {
		final var localPatronId = "673825";

		final var sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);

		sierraPatronsAPIFixture.noRecordsFoundWhenGettingPatronByLocalId(localPatronId);

		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localSystemCode(BORROWING_HOST_LMS_CODE)
				.localId(localPatronId)
				.build())
			.build();

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(failedCheck(
			"Patron \"%s\" is not recognised in \"%s\""
				.formatted(localPatronId, BORROWING_HOST_LMS_CODE)
		)));
	}

	@Test
	void shouldFailWhenLocalPatronTypeIsNotMappedToCanonicalPatronType(
		MockServerClient mockServerClient) {

		// Arrange
		final var sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);

		final var localPatronId = "578374";
		final var unmappedLocalPatronType = 15;

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localPatronId,
			Patron.builder()
				.id(1000002)
				.patronType(unmappedLocalPatronType)
				.homeLibraryCode("home-library")
				.barcodes(List.of("647647746"))
				.names(List.of("Bob"))
				.build());

		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localSystemCode(BORROWING_HOST_LMS_CODE)
				.localId(localPatronId)
				.build())
			.build();

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(failedCheck(
			"Local patron type \"%d\" from \"%s\" is not mapped to a DCB canonical patron type"
				.formatted(unmappedLocalPatronType, BORROWING_HOST_LMS_CODE))
		));
	}

	@Test
	void shouldFailWhenNoLocalPatronTypeIsDefined(MockServerClient mockServerClient) {
		// Arrange
		final var localPatronId = "683945";

		final var sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localPatronId, Patron.builder()
			.id(Integer.parseInt(localPatronId))
			.homeLibraryCode("home-library")
			.barcodes(List.of("647647746"))
			.names(List.of("Bob"))
			.build());

		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localSystemCode(BORROWING_HOST_LMS_CODE)
				.localId(localPatronId)
				.build())
			.build();

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(failedCheck(
			"Local patron \"%s\" from \"%s\" has non-numeric patron type \"null\""
				.formatted(localPatronId, BORROWING_HOST_LMS_CODE)
		)));
	}

	@Test
	void shouldFailWhenHostLmsIsNotRecognised() {
		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localSystemCode("unknown-host-lms")
				.build())
			.build();

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("\"unknown-host-lms\" is not a recognised Host LMS")));
	}

	private List<CheckResult> check(PlacePatronRequestCommand command) {
		return singleValueFrom(check.check(command));
	}
}
