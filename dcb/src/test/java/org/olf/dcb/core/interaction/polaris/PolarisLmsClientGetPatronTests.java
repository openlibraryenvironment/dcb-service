package org.olf.dcb.core.interaction.polaris;

import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasMessageForHostLms;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasNoRequestBody;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasRequestMethod;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasRequestUrl;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasResponseStatusCode;
import static org.olf.dcb.test.matchers.interaction.HttpResponseProblemMatchers.hasTextResponseBody;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasCanonicalPatronType;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasHomeLibraryCode;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalBarcodes;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalIds;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalPatronType;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.isActive;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.isBlocked;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.isNotBlocked;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.isNotDeleted;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.PatronData;
import org.olf.dcb.core.interaction.polaris.PAPIClient.PatronCirculationBlocksResult;
import org.olf.dcb.core.interaction.shared.NoPatronTypeMappingFoundException;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.TestResourceLoaderProvider;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
@TestInstance(PER_CLASS)
class PolarisLmsClientGetPatronTests {
	private static final String HOST_LMS_CODE = "polaris-get-patron";

	private static final String HOST = "polaris-hostlms-tests.com";
	private static final String BASE_URL = "https://" + HOST;

	@Inject
	private TestResourceLoaderProvider testResourceLoaderProvider;

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;

	private MockPolarisFixture mockPolarisFixture;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		final var key = "polaris-hostlms-test-key";
		final var secret = "polaris-hostlms-test-secret";
		final var domain = "TEST";

		hostLmsFixture.createPolarisHostLms(HOST_LMS_CODE, key,
			secret, BASE_URL, domain, key, secret);

		mockPolarisFixture = new MockPolarisFixture(HOST, mockServerClient,
			testResourceLoaderProvider);
	}

	@BeforeEach
	void beforeEach() {
		mockPolarisFixture.mockPapiStaffAuthentication();
		mockPolarisFixture.mockAppServicesStaffAuthentication();

		referenceValueMappingFixture.deleteAll();
	}

	@Test
	void shouldBeAbleToFindPatronById() {
		// Arrange
		final var localId = "5736265";
		final var barcode = "963856255";
		final var organisationId = "39";
		final var patronCode = "3";

		mockPolarisFixture.mockGetPatron(localId,
			PatronData.builder()
				.patronID((parseInt(localId)))
				.patronCodeID(parseInt(patronCode))
				.barcode(barcode)
				.organizationID(parseInt(organisationId))
				.build());

		mockPolarisFixture.mockGetPatronCirculationBlocks(barcode, circulationBlock(true));

		final var canonicalPatronType = "UNDERGRADUATE";

		definePatronTypeMapping(patronCode, canonicalPatronType);

		// Act
		final var patron = getPatronByIdentifier(localId);

		// Assert
		assertThat(patron, allOf(
			notNullValue(),
			hasLocalIds(localId),
			hasLocalPatronType(patronCode),
			hasCanonicalPatronType(canonicalPatronType),
			hasLocalBarcodes(barcode),
			hasHomeLibraryCode(organisationId),
			isActive(),
			isNotBlocked(),
			isNotDeleted()
		));
	}

	@Test
	void shouldBeAbleToFindBlockedPatronById() {
		// Arrange
		final var localId = "673663";
		final var barcode = "71282756";
		final var organisationId = "39";
		final var patronCodeId = "3";

		mockPolarisFixture.mockGetPatron(localId,
			PatronData.builder()
				.patronID((parseInt(localId)))
				.patronCodeID(parseInt(patronCodeId))
				.barcode(barcode)
				.organizationID(parseInt(organisationId))
				.build());

		mockPolarisFixture.mockGetPatronCirculationBlocks(barcode, circulationBlock(false));

		definePatronTypeMapping(patronCodeId, "UNDERGRADUATE");

		// Act
		final var patron = getPatron(localId);

		// Assert
		assertThat(patron, allOf(
			notNullValue(),
			isBlocked()
		));
	}

	@Test
	void shouldDefaultToNotBlocked() {
		// Arrange
		final var localId = "8264569";
		final var barcode = "936525732";
		final var organisationId = "39";
		final var patronCodeId = "3";

		mockPolarisFixture.mockGetPatron(localId,
			PatronData.builder()
				.patronID((parseInt(localId)))
				.patronCodeID(parseInt(patronCodeId))
				.barcode(barcode)
				.organizationID(parseInt(organisationId))
				.build());

		mockPolarisFixture.mockGetPatronCirculationBlocks(barcode, circulationBlock(null));

		definePatronTypeMapping(patronCodeId, "UNDERGRADUATE");

		// Act
		final var patron = getPatron(localId);

		// Assert
		assertThat(patron, allOf(
			notNullValue(),
			isNotBlocked()
		));
	}

	@Test
	void shouldFailWhenCirculationBlocksEndpointReturnsError() {
		// Arrange
		final var localId = "2769363";
		final var barcode = "096285231";
		final var organisationId = "39";
		final var patronCodeId = "3";

		mockPolarisFixture.mockGetPatron(localId,
			PatronData.builder()
				.patronID((parseInt(localId)))
				.patronCodeID(parseInt(patronCodeId))
				.barcode(barcode)
				.organizationID(parseInt(organisationId))
				.build());

		mockPolarisFixture.mockGetPatronCirculationBlocks(barcode,
			PatronCirculationBlocksResult.builder()
				.papiErrorCode(-50)
				.errorMessage("Something went wrong")
				.build());

		definePatronTypeMapping(patronCodeId, "UNDERGRADUATE");

		// Act
		final var problem = assertThrows(CannotGetPatronBlocksProblem.class,
			() -> getPatron(localId));

		// Assert
		assertThat(problem, allOf(
			notNullValue(),
			hasMessage("Circulation blocks endpoint returned error code [%d] with message: %s"
				.formatted(-50, "Something went wrong"))
		));
	}

	@Test
	void shouldFailWhenLocalPatronTypeCannotBeMappedToCanonical() {
		// Arrange
		final var localId = "1255193";
		final var barcode = "48275635";
		final var organisationId = "39";
		final var patronCodeId = "3";

		mockPolarisFixture.mockGetPatron(localId,
			PatronData.builder()
				.patronID((parseInt(localId)))
				.patronCodeID(parseInt(patronCodeId))
				.barcode(barcode)
				.organizationID(parseInt(organisationId))
				.build());

		// Act
		final var problem = assertThrows(NoPatronTypeMappingFoundException.class,
			() -> getPatron(localId));

		// Assert
		assertThat(problem, allOf(
			notNullValue(),
			hasMessage("Unable to map patronType %s:%s To DCB context"
				.formatted(HOST_LMS_CODE, patronCodeId))
		));
	}

	@Test
	void shouldFailToFindPatronByIdWhenServerErrorResponseIsReturned() {
		// Arrange
		final var localId = "6483613";

		mockPolarisFixture.mockGetPatronServerErrorResponse(localId);

		// Act
		final var problem = assertThrows(ThrowableProblem.class, () -> getPatron(localId));

		// Assert
		assertThat(problem, allOf(
			notNullValue(),
			hasMessageForHostLms(HOST_LMS_CODE),
			hasResponseStatusCode(500),
			hasTextResponseBody("Something went wrong"),
			hasRequestMethod("GET"),
			hasNoRequestBody(),
			hasRequestUrl(patronUrl(localId))
		));
	}

	private Patron getPatron(String localId) {
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		return singleValueFrom(client.getPatronByLocalId(localId));
	}

	private Patron getPatronByIdentifier(String localId) {
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		return singleValueFrom(client.getPatronByIdentifier(localId));
	}

	private static String patronUrl(String patronId) {
		return "%s/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/%s"
			.formatted(BASE_URL, patronId);
	}

	private static PatronCirculationBlocksResult circulationBlock(Boolean canPatronCirculate) {
		return PatronCirculationBlocksResult.builder()
			.canPatronCirculate(canPatronCirculate)
			.build();
	}

	private void definePatronTypeMapping(String patronCode, String canonicalValue) {
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(HOST_LMS_CODE,
			parseLong(patronCode), parseLong(patronCode), "DCB", canonicalValue);
	}
}
