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
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.isNotBlocked;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.polaris.ApplicationServicesClient.PatronData;
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

	@Inject
	private TestResourceLoaderProvider testResourceLoaderProvider;

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;

	private MockServerClient mockServerClient;
	private MockPolarisFixture mockPolarisFixture;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		this.mockServerClient = mockServerClient;

		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		final var baseUrl = "https://polaris-hostlms-tests.com";
		final var key = "polaris-hostlms-test-key";
		final var secret = "polaris-hostlms-test-secret";
		final var domain = "TEST";

		hostLmsFixture.createPolarisHostLms(HOST_LMS_CODE, key,
			secret, baseUrl, domain, key, secret);

		mockPolarisFixture = new MockPolarisFixture("polaris-hostlms-tests.com",
			mockServerClient, testResourceLoaderProvider);
	}

	@BeforeEach
	void beforeEach() {
		mockServerClient.reset();

		mockPolarisFixture.mockPapiStaffAuthentication();
		mockPolarisFixture.mockAppServicesStaffAuthentication();

		referenceValueMappingFixture.deleteAll();
	}

	@Test
	void shouldBeAbleToFindPatronById() {
		// Arrange
		final var localId = "1255193";
		final var barcode = "0077777777";
		final var organisationId = "39";
		final var patronCodeId = "3";

		mockPolarisFixture.mockGetPatron(localId,
			PatronData.builder()
				.patronID((parseInt(localId)))
				.patronCodeID(parseInt(patronCodeId))
				.barcode(barcode)
				.organizationID(parseInt(organisationId))
				.build());

		final var canonicalPatronType = "UNDERGRADUATE";

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(HOST_LMS_CODE,
			parseLong(patronCodeId), parseLong(patronCodeId), "DCB", canonicalPatronType);

		// Act
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var response = singleValueFrom(client.getPatronByLocalId(localId));

		// Assert
		assertThat(response, allOf(
			notNullValue(),
			hasLocalIds(localId),
			hasLocalPatronType(patronCodeId),
			hasCanonicalPatronType(canonicalPatronType),
			hasLocalBarcodes(barcode),
			hasHomeLibraryCode(organisationId),
			isNotBlocked()
		));
	}

	@Test
	void shouldFailWhenLocalPatronTypeCannotBeMappedToCanonical() {
		// Arrange
		final var localId = "1255193";
		final var barcode = "0077777777";
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
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var problem = assertThrows(NoPatronTypeMappingFoundException.class,
			() -> singleValueFrom(client.getPatronByLocalId(localId)));

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
		final var client = hostLmsFixture.createClient(HOST_LMS_CODE);

		final var problem = assertThrows(ThrowableProblem.class,
			() -> singleValueFrom(client.getPatronByLocalId(localId)));

		// Assert
		assertThat(problem, allOf(
			notNullValue(),
			hasMessageForHostLms(HOST_LMS_CODE),
			hasResponseStatusCode(500),
			hasTextResponseBody("Something went wrong"),
			hasRequestMethod("GET"),
			hasNoRequestBody(),
			hasRequestUrl(
				"https://polaris-hostlms-tests.com/polaris.applicationservices/api/v1/eng/20/polaris/73/1/patrons/6483613")
		));
	}
}
