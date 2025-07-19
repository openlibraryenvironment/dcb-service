package org.olf.dcb.request.fulfilment;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
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
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
@TestInstance(PER_CLASS)
@Property(name = "dcb.requests.preflight-checks.resolve-patron.enabled", value = "true")
class ResolvePatronPreflightCheckTests extends AbstractPreflightCheckTests {
	private static final String BORROWING_HOST_LMS_CODE = "borrowing-host-lms";

	@Inject
	private ResolvePatronPreflightCheck check;

	@Inject
	private SierraApiFixtureProvider sierraApiFixtureProvider;

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private AgencyFixture agencyFixture;
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
		agencyFixture.deleteAll();
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

		mapPatronToAgency(BORROWING_HOST_LMS_CODE, "home-library", "example-agency",
			true);

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
	void shouldPassWhenPatronMappedToDefaultAgency() {
		// Arrange
		final var localPatronId = "673633";
		final var localPatronType = 15;

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localPatronId,
			Patron.builder()
				.id(Integer.parseInt(localPatronId))
				.patronType(localPatronType)
				.barcodes(List.of("647647746"))
				.names(List.of("Bob"))
				.build());

		agencyFixture.defineAgency("default-agency-code", "Default Agency",
			hostLmsFixture.findByCode(BORROWING_HOST_LMS_CODE));

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
	void shouldFailWhenPatronIsAssociatedWithAgencyNotParticipatingInBorrowing() {
		// Arrange
		final var localPatronId = "562732";
		final var localPatronType = 15;

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localPatronId,
			Patron.builder()
				.id(Integer.parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode("home-library")
				.barcodes(List.of("27536633"))
				.names(List.of("Bob"))
				.build());

		final var agencyCode = "non-borrowing-agency";

		mapPatronToAgency(BORROWING_HOST_LMS_CODE, "home-library", agencyCode, false);

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
			failedCheck("PATRON_AGENCY_NOT_PARTICIPATING_IN_BORROWING",
				"Patron \"%s\" from \"%s\" is associated with agency \"%s\" which is not participating in borrowing"
					.formatted(localPatronId, BORROWING_HOST_LMS_CODE, agencyCode))
		));
	}

	@Test
	void shouldFailWhenPatronIsAssociatedWithAnAgencyWithNoParticipationInformation() {
		// Arrange
		final var localPatronId = "736453";
		final var localPatronType = 15;

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localPatronId,
			Patron.builder()
				.id(Integer.parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode("home-library")
				.barcodes(List.of("27536633"))
				.names(List.of("Bob"))
				.build());

		final var agencyCode = "non-borrowing-agency";

		mapPatronToAgency(BORROWING_HOST_LMS_CODE, "home-library", agencyCode, null);

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
			failedCheck("PATRON_AGENCY_NOT_PARTICIPATING_IN_BORROWING",
				"Patron \"%s\" from \"%s\" is associated with agency \"%s\" which is not participating in borrowing"
					.formatted(localPatronId, BORROWING_HOST_LMS_CODE, agencyCode))
		));
	}

	@Test
	void shouldFailWhenPatronIsNotAssociatedWithAgency() {
		// Arrange
		final var localPatronId = "8292567";
		final var localPatronType = 15;

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localPatronId,
			Patron.builder()
				.id(Integer.parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode("home-library")
				.barcodes(List.of("27536633"))
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
		assertThat(results, containsInAnyOrder(
			failedCheck("PATRON_NOT_ASSOCIATED_WITH_AGENCY",
				"Patron \"%s\" with home library code \"%s\" from \"%s\" is not associated with an agency"
					.formatted(localPatronId, "home-library", BORROWING_HOST_LMS_CODE))
		));
	}

	@Test
	void shouldFailWhenPatronIsAssociatedWithUnknownAgency() {
		// Arrange
		final var localPatronId = "725562";
		final var localPatronType = 15;

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localPatronId,
			Patron.builder()
				.id(Integer.parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode("home-library")
				.barcodes(List.of("27536633"))
				.names(List.of("Bob"))
				.build());

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			BORROWING_HOST_LMS_CODE, "home-library", "unknown-agency");

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
			failedCheck("PATRON_NOT_ASSOCIATED_WITH_AGENCY",
				"Patron \"%s\" with home library code \"%s\" from \"%s\" is not associated with an agency"
					.formatted(localPatronId, "home-library", BORROWING_HOST_LMS_CODE))
		));
	}

	@Test
	void shouldFailWhenPatronIsIneligible() {
		// Arrange
		final var localPatronId = "836492";
		final var localPatronType = 15;

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localPatronId,
			Patron.builder()
				.id(Integer.parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode("home-library")
				.barcodes(List.of("27536633"))
				.names(List.of("Bob"))
				.build());

		mapPatronToAgency(BORROWING_HOST_LMS_CODE, "home-library", "example-agency",
			true);

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

		mapPatronToAgency(BORROWING_HOST_LMS_CODE, "home-library", "example-agency",
			true);

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
		final var localPatronId = "984625";
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

		mapPatronToAgency(BORROWING_HOST_LMS_CODE, "home-library", "example-agency",
			true);

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
	void shouldFailWhenPatronHasNoBarcodes() {
		// Arrange
		final var localPatronId = "673635";
		final var localPatronType = 15;
		final var homeLibraryCode = "home-library";

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localPatronId,
			Patron.builder()
				.id(Integer.parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode(homeLibraryCode)
				.barcodes(emptyList())
				.names(List.of("Bob"))
				.build());

		mapPatronToAgency(BORROWING_HOST_LMS_CODE, homeLibraryCode, "example-agency",
			true);

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
			failedCheck("INVALID_PATRON_BARCODE",
				"Patron \"%s\" from \"%s\" has an invalid barcode: \"%s\""
					.formatted(localPatronId, BORROWING_HOST_LMS_CODE, ""))
		));
	}

	@Test
	void shouldFailWhenPatronHasEmptyBarcode() {
		// Arrange
		final var localPatronId = "673635";
		final var localPatronType = 15;
		final var homeLibraryCode = "home-library";

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localPatronId,
			Patron.builder()
				.id(Integer.parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode(homeLibraryCode)
				.barcodes(List.of(""))
				.names(List.of("Bob"))
				.build());

		mapPatronToAgency(BORROWING_HOST_LMS_CODE, homeLibraryCode, "example-agency",
			true);

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
			failedCheck("INVALID_PATRON_BARCODE",
				"Patron \"%s\" from \"%s\" has an invalid barcode: \"%s\""
					.formatted(localPatronId, BORROWING_HOST_LMS_CODE, ""))
		));
	}

	@Test
	void shouldFailEvenWhenPatronHasSecondNonEmptyBarcode() {
		// Arrange
		final var localPatronId = "673635";
		final var localPatronType = 15;
		final var homeLibraryCode = "home-library";

		sierraPatronsAPIFixture.getPatronByLocalIdSuccessResponse(localPatronId,
			Patron.builder()
				.id(Integer.parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode(homeLibraryCode)
				.barcodes(List.of("", "242673764"))
				.names(List.of("Bob"))
				.build());

		mapPatronToAgency(BORROWING_HOST_LMS_CODE, homeLibraryCode, "example-agency",
			true);

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
			failedCheck("INVALID_PATRON_BARCODE",
				"Patron \"%s\" from \"%s\" has an invalid barcode: \"%s\""
					.formatted(localPatronId, BORROWING_HOST_LMS_CODE, ""))
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

		mapPatronToAgency(BORROWING_HOST_LMS_CODE, "home-library", "example-agency",
			true);

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
			failedCheck("PATRON_NOT_FOUND",
				"Patron \"%s\" from \"%s\" has likely been deleted"
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

		mapPatronToAgency(BORROWING_HOST_LMS_CODE, "home-library", "example-agency",
			true);

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

		mapPatronToAgency(BORROWING_HOST_LMS_CODE, "home-library", "example-agency",
			true);

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

	private void mapPatronToAgency(String hostLmsCode, String locationCode,
		String agencyCode, Boolean isBorrowingAgency) {

		final var hostLms = hostLmsFixture.findByCode(hostLmsCode);

		agencyFixture.defineAgency(DataAgency.builder()
			.id(randomUUID())
			.code(agencyCode)
			.name("Example Agency")
			.isSupplyingAgency(true)
			.isBorrowingAgency(isBorrowingAgency)
			.hostLms(hostLms)
			.build());

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			hostLmsCode, locationCode, agencyCode);
	}

	private List<CheckResult> check(PlacePatronRequestCommand command) {
		return singleValueFrom(check.check(command));
	}
}
