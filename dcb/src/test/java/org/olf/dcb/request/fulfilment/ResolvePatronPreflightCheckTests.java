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
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
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

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		final String BASE_URL = "https://resolve-patron-tests.com";
		final String KEY = "resolve-patron-key";
		final String SECRET = "resolve-patron-secret";

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, "test-token", 60);

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(BORROWING_HOST_LMS_CODE, KEY, SECRET, BASE_URL, "item");

		sierraPatronsAPIFixture = sierraApiFixtureProvider.patronsApiFor(mockServerClient);
	}

	@BeforeEach
	void beforeEach() {
		referenceValueMappingFixture.deleteAll();
	}

	@Test
	void shouldPassWhenPatronCanBeFoundInHostLms() {
		// Arrange
		final var localPatronId = "345358";
		final var localPatronType = 15;

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localPatronId,
			Patron.builder()
				.id(Integer.parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode("home-library")
				.barcodes(List.of("647647746"))
				.names(List.of("Bob"))
				.build());

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			BORROWING_HOST_LMS_CODE, localPatronType, localPatronType, "DCB", "UNDERGRAD");

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
	void shouldFailWhenPatronIsIneligible() {
		// Arrange
		final var localPatronId = "345358";
		final var localPatronType = 15;

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localPatronId,
			Patron.builder()
				.id(Integer.parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode("home-library")
				.barcodes(List.of("27536633"))
				.names(List.of("Bob"))
				.build());

		final var notEligibleCanonicalPatronType = "NOT_ELIGIBLE";

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			BORROWING_HOST_LMS_CODE, localPatronType, localPatronType, "DCB",
			notEligibleCanonicalPatronType);

		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localSystemCode(BORROWING_HOST_LMS_CODE)
				.localId(localPatronId)
				.build())
			.build();

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("PATRON_INELIGIBLE",
				"Patron \"%s\" from \"%s\" is of type \"%s\" which is \"%s\" for consortial borrowing"
					.formatted(localPatronId, BORROWING_HOST_LMS_CODE, localPatronType,
						notEligibleCanonicalPatronType))
		));
	}

	@Test
	void shouldFailWhenPatronIsBlocked() {
		// Arrange
		final var localPatronId = "164266";
		final var localPatronType = 15;

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localPatronId,
			Patron.builder()
				.id(Integer.parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode("home-library")
				.barcodes(List.of("27536633"))
				.names(List.of("Bob"))
				.blockInfo(SierraPatronsAPIFixture.PatronBlock.builder()
					.code("blocked")
					.build())
				.build());

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			BORROWING_HOST_LMS_CODE, localPatronType, localPatronType, "DCB", "UNDERGRAD");

		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localSystemCode(BORROWING_HOST_LMS_CODE)
				.localId(localPatronId)
				.build())
			.build();

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("PATRON_BLOCKED",
				"Patron \"%s\" from \"%s\" has a local account block"
					.formatted(localPatronId, BORROWING_HOST_LMS_CODE))
		));
	}

	@Test
	void shouldFailWhenPatronIsIneligibleAndBlocked() {
		// Arrange
		final var localPatronId = "2656774";
		final var localPatronType = 15;

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localPatronId,
			Patron.builder()
				.id(Integer.parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode("home-library")
				.barcodes(List.of("27536633"))
				.names(List.of("Bob"))
				.blockInfo(SierraPatronsAPIFixture.PatronBlock.builder()
					.code("blocked")
					.build())
				.build());

		final var notEligibleCanonicalPatronType = "NOT_ELIGIBLE";

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			BORROWING_HOST_LMS_CODE, localPatronType, localPatronType, "DCB",
			notEligibleCanonicalPatronType);

		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localSystemCode(BORROWING_HOST_LMS_CODE)
				.localId(localPatronId)
				.build())
			.build();

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("PATRON_INELIGIBLE"),
			failedCheck("PATRON_BLOCKED")
		));
	}

	@Test
	void shouldFailWhenPatronCannotBeFoundInHostLms() {
		// Arrange
		final var localPatronId = "673825";

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
		assertThat(results, containsInAnyOrder(
			failedCheck("PATRON_NOT_FOUND",
				"Patron \"%s\" is not recognised in \"%s\""
					.formatted(localPatronId, BORROWING_HOST_LMS_CODE))
		));
	}

	@Test
	void shouldFailWhenPatronHasBeenDeletedInHostLms() {
		// Arrange
		final var localPatronId = "352452";
		final var localPatronType = 15;

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localPatronId,
			Patron.builder()
				.id(Integer.parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode("home-library")
				.barcodes(List.of("647647746"))
				.names(List.of("Bob"))
				.deleted(true)
				.build());

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			BORROWING_HOST_LMS_CODE, localPatronType, localPatronType, "DCB", "UNDERGRAD");

		// Act
		final var command = PlacePatronRequestCommand.builder()
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localSystemCode(BORROWING_HOST_LMS_CODE)
				.localId(localPatronId)
				.build())
			.build();

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("PATRON_DELETED",
				"Patron \"%s\" from \"%s\" has been deleted"
					.formatted(localPatronId, BORROWING_HOST_LMS_CODE))
		));
	}

	@Test
	void shouldFailWhenLocalPatronTypeIsNotMappedToCanonicalPatronType() {
		// Arrange
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
		assertThat(results, containsInAnyOrder(
			failedCheck("PATRON_TYPE_NOT_MAPPED",
				"Local patron type \"%d\" from \"%s\" is not mapped to a DCB canonical patron type"
					.formatted(unmappedLocalPatronType, BORROWING_HOST_LMS_CODE))
		));
	}

	@Test
	void shouldFailWhenNoLocalPatronTypeIsDefined() {
		// Arrange
		final var localPatronId = "683945";

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
		assertThat(results, containsInAnyOrder(
			failedCheck("LOCAL_PATRON_TYPE_IS_NON_NUMERIC",
				"Local patron \"%s\" from \"%s\" has non-numeric patron type \"null\""
					.formatted(localPatronId, BORROWING_HOST_LMS_CODE))
		));
	}

	@Test
	void shouldFailWhenHostLmsIsNotRecognised() {
		// Act
		final var unknownHostLmsCode = "unknown-host-lms";

		final var command = PlacePatronRequestCommand.builder()
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localSystemCode(unknownHostLmsCode)
				.build())
			.build();

		final var results = check(command);

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("UNKNOWN_BORROWING_HOST_LMS",
				"\"%s\" is not a recognised Host LMS".formatted(unknownHostLmsCode))
		));
	}

	private List<CheckResult> check(PlacePatronRequestCommand command) {
		return singleValueFrom(check.check(command));
	}
}
