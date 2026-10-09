package org.olf.dcb.request.fulfilment;

import static java.lang.Integer.parseInt;
import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.FINALISED;
import static org.olf.dcb.core.model.PatronRequest.Status.LOANED;
import static org.olf.dcb.test.IdentifierGenerator.generateBarcode;
import static org.olf.dcb.test.IdentifierGenerator.generateNumericLocalIdAsString;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.patrons.Block;
import services.k_int.interaction.sierra.patrons.SierraPatronRecord;
import services.k_int.test.mockserver.MockServerMicronautTest;

@Slf4j
@MockServerMicronautTest
@TestInstance(PER_CLASS)
@Property(name = "dcb.requests.preflight-checks.resolve-patron.enabled", value = "true")
class ResolvePatronPreflightCheckTests extends AbstractPreflightCheckTests {
	private static final String BORROWING_HOST_LMS_CODE = "borrowing-host-lms";
	private static final String HOME_LIBRARY_CODE = "home-library";
	// Outside the generated range, and no substring of any count, limit or code in a description
	private static final String HOLD_LIMIT_PATRON_ID = "8675309";
	private static final String LOAN_LIMIT_PATRON_ID = "8675311";

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
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;

	private SierraPatronsAPIFixture sierraPatronsAPIFixture;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		final String BASE_URL = "https://resolve-patron-tests.com";
		final String KEY = "resolve-patron-key";
		final String SECRET = "resolve-patron-secret";

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(KEY, SECRET, "test-token", 3600);

		hostLmsFixture.deleteAll();

		hostLmsFixture.createSierraHostLms(BORROWING_HOST_LMS_CODE, KEY, SECRET, BASE_URL, "item");

		sierraPatronsAPIFixture = sierraApiFixtureProvider.patrons(mockServerClient, null);
	}

	@BeforeEach
	void beforeEach() {
		patronFixture.deleteAllPatrons();
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();
	}

	@AfterAll
	void afterAll() {
		patronFixture.deleteAllPatrons();
	}

	@Test
	void shouldPassWhenPatronCanBeFoundInHostLms() {
		// Arrange
		final var localPatronId = generateNumericLocalIdAsString();
		final var localPatronType = 15;

		sierraPatronsAPIFixture.mockGetPatronById(localPatronId,
			SierraPatronRecord.builder()
				.id(parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode(HOME_LIBRARY_CODE)
				.barcodes(List.of(generateBarcode()))
				.names(List.of("Bob"))
				.build());

		mapPatronToAgency(HOME_LIBRARY_CODE, "example-agency",
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
		final var localPatronId = generateNumericLocalIdAsString();
		final var localPatronType = 15;

		sierraPatronsAPIFixture.mockGetPatronById(localPatronId,
			SierraPatronRecord.builder()
				.id(parseInt(localPatronId))
				.patronType(localPatronType)
				.barcodes(List.of(generateBarcode()))
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
		final var localPatronId = generateNumericLocalIdAsString();
		final var localPatronType = 15;

		sierraPatronsAPIFixture.mockGetPatronById(localPatronId,
			SierraPatronRecord.builder()
				.id(parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode(HOME_LIBRARY_CODE)
				.barcodes(List.of(generateBarcode()))
				.names(List.of("Bob"))
				.build());

		final var agencyCode = "non-borrowing-agency";

		mapPatronToAgency(HOME_LIBRARY_CODE, agencyCode, false);

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
				"Patron from \"%s\" is associated with agency \"%s\" which is not participating in borrowing"
					.formatted(BORROWING_HOST_LMS_CODE, agencyCode))
		));
	}

	@Test
	void shouldFailWhenPatronIsAssociatedWithAnAgencyWithNoParticipationInformation() {
		// Arrange
		final var localPatronId = generateNumericLocalIdAsString();
		final var localPatronType = 15;

		sierraPatronsAPIFixture.mockGetPatronById(localPatronId,
			SierraPatronRecord.builder()
				.id(parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode(HOME_LIBRARY_CODE)
				.barcodes(List.of(generateBarcode()))
				.names(List.of("Bob"))
				.build());

		final var agencyCode = "non-borrowing-agency";

		mapPatronToAgency(HOME_LIBRARY_CODE, agencyCode, null);

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
				"Patron from \"%s\" is associated with agency \"%s\" which is not participating in borrowing"
					.formatted(BORROWING_HOST_LMS_CODE, agencyCode))
		));
	}

	@Test
	void shouldFailWhenPatronIsNotAssociatedWithAgency() {
		// Arrange
		final var localPatronId = generateNumericLocalIdAsString();
		final var localPatronType = 15;

		sierraPatronsAPIFixture.mockGetPatronById(localPatronId,
			SierraPatronRecord.builder()
				.id(parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode(HOME_LIBRARY_CODE)
				.barcodes(List.of(generateBarcode()))
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
				"Patron with home library code \"%s\" from \"%s\" is not associated with an agency"
					.formatted(HOME_LIBRARY_CODE, BORROWING_HOST_LMS_CODE))
		));
	}

	@Test
	void shouldFailWhenPatronIsAssociatedWithUnknownAgency() {
		// Arrange
		final var localPatronId = generateNumericLocalIdAsString();
		final var localPatronType = 15;

		sierraPatronsAPIFixture.mockGetPatronById(localPatronId,
			SierraPatronRecord.builder()
				.id(parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode(HOME_LIBRARY_CODE)
				.barcodes(List.of(generateBarcode()))
				.names(List.of("Bob"))
				.build());

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			BORROWING_HOST_LMS_CODE, HOME_LIBRARY_CODE, "unknown-agency");

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
				"Patron with home library code \"%s\" from \"%s\" is not associated with an agency"
					.formatted(HOME_LIBRARY_CODE, BORROWING_HOST_LMS_CODE))
		));
	}

	@Test
	void shouldFailWhenPatronIsIneligible() {
		// Arrange
		final var localPatronId = generateNumericLocalIdAsString();
		final var localPatronType = 15;

		sierraPatronsAPIFixture.mockGetPatronById(localPatronId,
			SierraPatronRecord.builder()
				.id(parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode(HOME_LIBRARY_CODE)
				.barcodes(List.of(generateBarcode()))
				.names(List.of("Bob"))
				.build());

		mapPatronToAgency(HOME_LIBRARY_CODE, "example-agency",
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
				"Patron from \"%s\" is of type \"%s\" which is \"%s\" for consortial borrowing"
					.formatted(BORROWING_HOST_LMS_CODE, localPatronType,
						notEligibleCanonicalPatronType))
		));
	}

	@Test
	void shouldFailWhenPatronHasReachedTheAgencyHoldLimit() {
		// Arrange
		final var localPatronId = HOLD_LIMIT_PATRON_ID;

		defineEligiblePatron(localPatronId, 15);
		mapPatronToAgency(BORROWING_HOST_LMS_CODE, HOME_LIBRARY_CODE, "example-agency", true, 25);

		sierraPatronsAPIFixture.mockGetHoldsForPatronReturningCount(localPatronId, 25);

		// Act
		final var results = checkPatronRequestFor(localPatronId);

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("PATRON_HOLD_LIMIT_REACHED",
				"25 holds reaches the limit of 25 for agency \"example-agency\" on \"%s\""
					.formatted(BORROWING_HOST_LMS_CODE))
		));

		assertThat(results, everyItem(descriptionNotContaining(localPatronId)));
	}

	@Test
	void shouldPassWhenPatronIsBelowTheAgencyHoldLimit() {
		// Arrange
		final var localPatronId = generateNumericLocalIdAsString();

		defineEligiblePatron(localPatronId, 15);
		mapPatronToAgency(BORROWING_HOST_LMS_CODE, HOME_LIBRARY_CODE, "example-agency", true, 25);

		sierraPatronsAPIFixture.mockGetHoldsForPatronReturningCount(localPatronId, 24);

		// Act
		final var results = checkPatronRequestFor(localPatronId);

		// Assert
		assertThat(results, containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldNotCheckHoldLimitWhenAgencyHasNotDeclaredOne() {
		// No Host LMS exposes its hold limit, so an agency that has not told us theirs
		// cannot be checked - the patron must not be blocked on a number we invented

		// Arrange
		final var localPatronId = generateNumericLocalIdAsString();

		defineEligiblePatron(localPatronId, 15);
		mapPatronToAgency(BORROWING_HOST_LMS_CODE, HOME_LIBRARY_CODE, "example-agency", true, null);

		sierraPatronsAPIFixture.mockGetHoldsForPatronReturningCount(localPatronId, 500);

		// Act
		final var results = checkPatronRequestFor(localPatronId);

		// Assert
		assertThat(results, containsInAnyOrder(passedCheck()));

		// The count is what costs a Host LMS round trip on every placement, so an
		// undeclared limit must skip the call, not fetch a count it cannot compare
		sierraPatronsAPIFixture.verifyNoHoldsForPatronRequestMade(localPatronId);
	}

	@Test
	void shouldPassWhenHoldCountCannotBeDetermined() {
		// An unknown count is not a count of zero, but it cannot demonstrate the patron
		// is over their limit either, so it must not block them

		// Arrange
		final var localPatronId = generateNumericLocalIdAsString();

		defineEligiblePatron(localPatronId, 15);
		mapPatronToAgency(BORROWING_HOST_LMS_CODE, HOME_LIBRARY_CODE, "example-agency", true, 25);

		sierraPatronsAPIFixture.patronHoldErrorResponse(localPatronId);

		// Act
		final var results = checkPatronRequestFor(localPatronId);

		// Assert
		assertThat(results, containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldFailWhenPatronHasReachedTheAgencyConsortialLoanLimit() {
		// Arrange
		final var localPatronId = LOAN_LIMIT_PATRON_ID;

		defineEligiblePatron(localPatronId, 15);
		mapPatronToAgencyWithLoanLimit("example-agency", 2);

		final var identity = defineHomeIdentity(localPatronId);
		saveRequest(identity, LOANED);
		saveRequest(identity, LOANED);

		// Act
		final var results = check(requestWithoutAgencyCode(localPatronId));

		// Assert
		assertThat(results, containsInAnyOrder(
			failedCheck("EXCEEDS_AGENCY_LIMIT",
				"2 active requests reaches the limit of 2 for agency \"example-agency\" on \"%s\""
					.formatted(BORROWING_HOST_LMS_CODE))
		));

		assertThat(results, everyItem(descriptionNotContaining(localPatronId)));

		assertThat(results, everyItem(hasProperty("userMessage",
			is("You have reached the number of active requests your library allows. "
				+ "Please wait for a current request to finish, or contact your library."))));
	}

	@Test
	void shouldPassWhenPatronIsBelowTheAgencyConsortialLoanLimit() {
		// Arrange
		final var localPatronId = generateNumericLocalIdAsString();

		defineEligiblePatron(localPatronId, 15);
		mapPatronToAgencyWithLoanLimit("example-agency", 2);

		final var identity = defineHomeIdentity(localPatronId);
		saveRequest(identity, LOANED);
		saveRequest(identity, FINALISED);

		// Act
		final var results = check(requestWithoutAgencyCode(localPatronId));

		// Assert
		assertThat(results, containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldNotCountRequestsInErrorTowardsTheAgencyConsortialLoanLimit() {
		// Arrange
		final var localPatronId = generateNumericLocalIdAsString();

		defineEligiblePatron(localPatronId, 15);
		mapPatronToAgencyWithLoanLimit("example-agency", 2);

		final var identity = defineHomeIdentity(localPatronId);
		saveRequest(identity, LOANED);
		saveRequest(identity, ERROR);

		// Act
		final var results = check(requestWithoutAgencyCode(localPatronId));

		// Assert
		assertThat(results, containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldApplyTheResolvedAgencysLoanLimitRatherThanTheAgencyTheRequestNames() {
		// Arrange
		final var localPatronId = generateNumericLocalIdAsString();

		defineEligiblePatron(localPatronId, 15);
		mapPatronToAgencyWithLoanLimit("example-agency", 2);

		agencyFixture.defineAgency(DataAgency.builder()
			.id(randomUUID())
			.code("unlimited-agency")
			.name("Unlimited Agency")
			.isBorrowingAgency(true)
			.hostLms(hostLmsFixture.findByCode(BORROWING_HOST_LMS_CODE))
			.build());

		final var identity = defineHomeIdentity(localPatronId);
		saveRequest(identity, LOANED);
		saveRequest(identity, LOANED);

		// Act
		final var results = check(PlacePatronRequestCommand.builder()
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localSystemCode(BORROWING_HOST_LMS_CODE)
				.localId(localPatronId)
				.agencyCode("unlimited-agency")
				.build())
			.build());

		// Assert
		assertThat(results, containsInAnyOrder(failedCheck("EXCEEDS_AGENCY_LIMIT")));
	}

	@Test
	void shouldFailWhenPatronIsBlocked() {
		// Arrange
		final var localPatronId = generateNumericLocalIdAsString();
		final var localPatronType = 15;

		sierraPatronsAPIFixture.mockGetPatronById(localPatronId,
			SierraPatronRecord.builder()
				.id(parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode(HOME_LIBRARY_CODE)
				.barcodes(List.of(generateBarcode()))
				.names(List.of("Bob"))
				.blockInfo(Block.builder()
					.code("blocked")
					.build())
				.build());

		mapPatronToAgency(HOME_LIBRARY_CODE, "example-agency",
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
				"Patron from \"%s\" has a local account block".formatted(BORROWING_HOST_LMS_CODE))
		));
	}

	@Test
	void shouldFailWhenPatronIsIneligibleAndBlocked() {
		// Arrange
		final var localPatronId = generateNumericLocalIdAsString();
		final var localPatronType = 15;

		sierraPatronsAPIFixture.mockGetPatronById(localPatronId,
			SierraPatronRecord.builder()
				.id(parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode(HOME_LIBRARY_CODE)
				.barcodes(List.of(generateBarcode()))
				.names(List.of("Bob"))
				.autoBlockInfo(Block.builder()
					.code("blocked")
					.build())
				.build());

		mapPatronToAgency(HOME_LIBRARY_CODE, "example-agency",
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
		final var localPatronId = generateNumericLocalIdAsString();
		final var localPatronType = 15;
		final var homeLibraryCode = HOME_LIBRARY_CODE;

		sierraPatronsAPIFixture.mockGetPatronById(localPatronId,
			SierraPatronRecord.builder()
				.id(parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode(homeLibraryCode)
				.barcodes(emptyList())
				.names(List.of("Bob"))
				.build());

		mapPatronToAgency(homeLibraryCode, "example-agency",
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
				"Patron from \"%s\" has an invalid barcode".formatted(BORROWING_HOST_LMS_CODE))
		));
	}

	@Test
	void shouldFailWhenPatronHasEmptyBarcode() {
		// Arrange
		final var localPatronId = generateNumericLocalIdAsString();
		final var localPatronType = 15;
		final var homeLibraryCode = HOME_LIBRARY_CODE;

		sierraPatronsAPIFixture.mockGetPatronById(localPatronId,
			SierraPatronRecord.builder()
				.id(parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode(homeLibraryCode)
				.barcodes(List.of(""))
				.names(List.of("Bob"))
				.build());

		mapPatronToAgency(homeLibraryCode, "example-agency", true);

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
				"Patron from \"%s\" has an invalid barcode".formatted(BORROWING_HOST_LMS_CODE))
		));
	}

	@Test
	void shouldFailEvenWhenPatronHasSecondNonEmptyBarcode() {
		// Arrange
		final var localPatronId = generateNumericLocalIdAsString();
		final var localPatronType = 15;
		final var homeLibraryCode = HOME_LIBRARY_CODE;

		sierraPatronsAPIFixture.mockGetPatronById(localPatronId,
			SierraPatronRecord.builder()
				.id(parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode(homeLibraryCode)
				.barcodes(List.of("", generateBarcode()))
				.names(List.of("Bob"))
				.build());

		mapPatronToAgency(homeLibraryCode, "example-agency", true);

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
				"Patron from \"%s\" has an invalid barcode".formatted(BORROWING_HOST_LMS_CODE))
		));
	}


	@Test
	void shouldFailWhenPatronCannotBeFoundInHostLms() {
		// Arrange
		final var localPatronId = generateNumericLocalIdAsString();

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
				"Patron is not recognised in \"%s\"".formatted(BORROWING_HOST_LMS_CODE))
		));
	}

	@Test
	void shouldFailWhenPatronHasBeenDeletedInHostLms() {
		// Arrange
		final var localPatronId = generateNumericLocalIdAsString();
		final var localPatronType = 15;

		sierraPatronsAPIFixture.mockGetPatronById(localPatronId,
			SierraPatronRecord.builder()
				.id(parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode(HOME_LIBRARY_CODE)
				.barcodes(List.of(generateBarcode()))
				.names(List.of("Bob"))
				.deleted(true)
				.build());

		mapPatronToAgency(HOME_LIBRARY_CODE, "example-agency",
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
				"Patron from \"%s\" has likely been deleted".formatted(BORROWING_HOST_LMS_CODE))
		));
	}

	@Test
	void shouldFailWhenLocalPatronTypeIsNotMappedToCanonicalPatronType() {
		// Arrange
		final var localPatronId = generateNumericLocalIdAsString();
		final var unmappedLocalPatronType = 15;

		sierraPatronsAPIFixture.mockGetPatronById(localPatronId,
			SierraPatronRecord.builder()
				.id(parseInt(localPatronId))
				.patronType(unmappedLocalPatronType)
				.homeLibraryCode(HOME_LIBRARY_CODE)
				.barcodes(List.of(generateBarcode()))
				.names(List.of("Bob"))
				.build());

		mapPatronToAgency(HOME_LIBRARY_CODE, "example-agency", true);

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
		final var localPatronId = generateNumericLocalIdAsString();

		sierraPatronsAPIFixture.mockGetPatronById(localPatronId, SierraPatronRecord.builder()
			.id(parseInt(localPatronId))
			.homeLibraryCode(HOME_LIBRARY_CODE)
			.barcodes(List.of(generateBarcode()))
			.names(List.of("Bob"))
			.build());

		mapPatronToAgency(HOME_LIBRARY_CODE, "example-agency", true);

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
				"Local patron from \"%s\" has non-numeric patron type \"null\""
					.formatted(BORROWING_HOST_LMS_CODE))
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

	private void mapPatronToAgency(String locationCode, String agencyCode, Boolean isBorrowingAgency) {
		mapPatronToAgency(BORROWING_HOST_LMS_CODE, locationCode, agencyCode, isBorrowingAgency, null);
	}

	private void mapPatronToAgency(String hostLmsCode, String locationCode,
		String agencyCode, Boolean isBorrowingAgency, Integer maxLocalHolds) {

		final var hostLms = hostLmsFixture.findByCode(hostLmsCode);

		agencyFixture.defineAgency(DataAgency.builder()
			.id(randomUUID())
			.code(agencyCode)
			.name("Example Agency")
			.isSupplyingAgency(true)
			.isBorrowingAgency(isBorrowingAgency)
			.maxLocalHolds(maxLocalHolds)
			.hostLms(hostLms)
			.build());

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			hostLmsCode, locationCode, agencyCode);
	}

	private void mapPatronToAgencyWithLoanLimit(String agencyCode, Integer maxConsortialLoans) {
		agencyFixture.defineAgency(DataAgency.builder()
			.id(randomUUID())
			.code(agencyCode)
			.name("Example Agency")
			.isSupplyingAgency(true)
			.isBorrowingAgency(true)
			.maxConsortialLoans(maxConsortialLoans)
			.hostLms(hostLmsFixture.findByCode(BORROWING_HOST_LMS_CODE))
			.build());

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			BORROWING_HOST_LMS_CODE, HOME_LIBRARY_CODE, agencyCode);
	}

	private PatronIdentity defineHomeIdentity(String localPatronId) {
		return patronFixture.definePatron(localPatronId, HOME_LIBRARY_CODE,
				hostLmsFixture.findByCode(BORROWING_HOST_LMS_CODE))
			.getPatronIdentities().get(0);
	}

	private void saveRequest(PatronIdentity requestingIdentity, PatronRequest.Status status) {
		patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patronHostlmsCode(BORROWING_HOST_LMS_CODE)
			.requestingIdentity(requestingIdentity)
			.status(status)
			.build());
	}

	// The shape EBSCO Locate sends: the agency code in homeLibraryCode, none in agencyCode
	private static PlacePatronRequestCommand requestWithoutAgencyCode(String localPatronId) {
		return PlacePatronRequestCommand.builder()
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localSystemCode(BORROWING_HOST_LMS_CODE)
				.localId(localPatronId)
				.homeLibraryCode("example-agency")
				.build())
			.build();
	}

	private void defineEligiblePatron(String localPatronId, int localPatronType) {
		sierraPatronsAPIFixture.mockGetPatronById(localPatronId,
			SierraPatronRecord.builder()
				.id(parseInt(localPatronId))
				.patronType(localPatronType)
				.homeLibraryCode(HOME_LIBRARY_CODE)
				.barcodes(List.of(generateBarcode()))
				.names(List.of("Bob"))
				.build());

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			BORROWING_HOST_LMS_CODE, localPatronType, localPatronType, "DCB", "UNDERGRAD");
	}

	private List<CheckResult> checkPatronRequestFor(String localPatronId) {
		return check(PlacePatronRequestCommand.builder()
			.requestor(PlacePatronRequestCommand.Requestor.builder()
				.localSystemCode(BORROWING_HOST_LMS_CODE)
				.localId(localPatronId)
				.build())
			.build());
	}

	private static Matcher<CheckResult> descriptionNotContaining(String text) {
		return hasProperty("failureDescription", not(containsString(text)));
	}

	private List<CheckResult> check(PlacePatronRequestCommand command) {
		return singleValueFrom(check.check(command));
	}
}
