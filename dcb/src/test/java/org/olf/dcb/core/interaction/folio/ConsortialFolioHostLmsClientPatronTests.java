package org.olf.dcb.core.interaction.folio;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.equalToObject;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasCanonicalPatronType;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalBarcodes;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalIds;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalNames;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasLocalPatronType;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasNoCanonicalPatronType;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasNoHomeLibraryCode;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasNoLocalBarcodes;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasNoLocalIds;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasNoLocalNames;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.hasNoLocalPatronType;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.isActive;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.isNotBlocked;
import static org.olf.dcb.test.matchers.interaction.PatronMatchers.isNotDeleted;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.MultipleVirtualPatronsFound;
import org.olf.dcb.core.interaction.VirtualPatronNotFound;
import org.olf.dcb.core.interaction.shared.NoPatronTypeMappingFoundException;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

@MockServerMicronautTest
class ConsortialFolioHostLmsClientPatronTests {
	private static final String HOST_LMS_CODE = "folio-lms-client-patron-tests";

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	private MockFolioFixture mockFolioFixture;
	private HostLmsClient client;

	@BeforeEach
	public void beforeEach(MockServerClient mockServerClient) {
		final var API_KEY = "eyJzIjoic2FsdCIsInQiOiJ0ZW5hbnQiLCJ1IjoidXNlciJ9";

		hostLmsFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();

		hostLmsFixture.createFolioHostLms(HOST_LMS_CODE, "https://fake-folio",
			API_KEY, "", "");

		mockFolioFixture = new MockFolioFixture(mockServerClient, "fake-folio", API_KEY);

		client = hostLmsFixture.createClient(HOST_LMS_CODE);
	}

	@Test
	void findVirtualPatronShouldFindOnlyUserByBarcode() {
		// Arrange
		final var barcode = "67375297";
		final var localId = randomUUID().toString();
		final var patronGroupName = "undergraduate";

		referenceValueMappingFixture.definePatronTypeMapping(HOST_LMS_CODE,
			patronGroupName, "DCB", "canonical-patron-type");

		final var patron = createPatron(randomUUID(), barcode);

		mockFolioFixture.mockGetUsersWithQuery("barcode", barcode,
			User.builder()
				.id(localId)
				.patronGroupName(patronGroupName)
				.barcode(barcode)
				.personal(User.PersonalDetails.builder()
					.firstName("first name")
					.middleName("middle name")
					.lastName("last name")
					.preferredFirstName("preferred first name")
					.build())
				.build());

		// Act
		final var foundPatron = singleValueFrom(client.findVirtualPatron(patron));

		// Assert
		assertThat(foundPatron, allOf(
			notNullValue(),
			hasLocalIds(localId),
			hasLocalPatronType(patronGroupName),
			hasCanonicalPatronType("canonical-patron-type"),
			hasLocalBarcodes(barcode),
			hasNoHomeLibraryCode(),
			hasLocalNames("first name", "middle name", "last name"),
			isActive(),
			isNotBlocked(),
			isNotDeleted()
		));
	}

	@Test
	void findVirtualPatronShouldReturnEmptyWhenNoUsersFoundForBarcode() {
		// Arrange
		final var barcode = "47683763";

		final var patron = createPatron(randomUUID(), barcode);

		mockFolioFixture.mockGetUsersWithQuery("barcode", barcode);

		// Act
		final var exception = assertThrows(
			VirtualPatronNotFound.class,
			() -> singleValueFrom(client.findVirtualPatron(patron)));

		// Assert
		assertThat(exception, hasMessage(
			"Virtual Patron Not Found"));
	}

	@Test
	void findVirtualPatronShouldFailWhenPatronTypeMappingIsMissing() {
		// Arrange
		final var barcode = "34746725";

		final var patron = createPatron(randomUUID(), barcode);

		mockFolioFixture.mockGetUsersWithQuery("barcode", barcode, User.builder()
			.patronGroupName("unknown")
			.barcode(barcode)
			.build());

		// Act
		final var exception = assertThrows(
			NoPatronTypeMappingFoundException.class,
			() -> singleValueFrom(client.findVirtualPatron(patron)));

		// Assert
		assertThat(exception, hasMessage(
			"Unable to map patron type \"unknown\" on Host LMS: \"folio-lms-client-patron-tests\" to canonical value"));
	}

	@Test
	void findVirtualPatronShouldTolerateFindingUserWithMissingProperties() {
		// Arrange
		final var barcode = "7848675";

		final var patron = createPatron(randomUUID(), barcode);

		mockFolioFixture.mockGetUsersWithQuery("barcode", barcode, User.builder().build());

		// Act
		final var foundPatron = singleValueFrom(client.findVirtualPatron(patron));

		// Assert
		assertThat(foundPatron, allOf(
			notNullValue(),
			hasNoLocalIds(),
			hasNoLocalPatronType(),
			hasNoCanonicalPatronType(),
			hasNoLocalBarcodes(),
			hasNoHomeLibraryCode(),
			hasNoLocalNames(),
			isNotBlocked(),
			isActive()
		));
	}

	@Test
	void successfulPatronAuthentication() {
		// Arrange
		final var barcode = "4295753";
		final var patronGroupName = "undergraduate";

		referenceValueMappingFixture.definePatronTypeMapping(HOST_LMS_CODE,
			patronGroupName, "DCB", "canonical-patron-type");

		final var user = User.builder()
			.id("9c2e859d-e923-450d-85e3-b425cfa9f938")
			.patronGroupName(patronGroupName)
			.barcode(barcode)
			.personal(User.PersonalDetails.builder()
				.firstName("First")
				.lastName("Special Pin Test")
				.middleName("Middle")
				.build())
			.blocked(false)
			.build();

		mockFolioFixture.mockGetUsersWithQuery("barcode", barcode, user);

		mockFolioFixture.mockPatronPinVerify();

		// Act
		final var verifiedPatron = singleValueFrom(client.patronAuth("BASIC/BARCODE+PIN", barcode, "1234"));

		// Assert
		assertThat(verifiedPatron, allOf(
			notNullValue(),
			hasLocalIds("9c2e859d-e923-450d-85e3-b425cfa9f938"),
			hasLocalPatronType(patronGroupName),
			hasCanonicalPatronType("canonical-patron-type"),
			hasLocalBarcodes("4295753"),
			hasNoHomeLibraryCode(),
			hasLocalNames("First", "Middle", "Special Pin Test"),
			isNotBlocked()
		));
	}

	@Test
	void successfullyGetPatronByUsername() {
		// Arrange
		final var username = "special-pin-test";
		final var patronGroupName = "undergraduate";

		referenceValueMappingFixture.definePatronTypeMapping(HOST_LMS_CODE,
			patronGroupName, "DCB", "canonical-patron-type");

		mockFolioFixture.mockGetUsersWithQuery("barcode", username, User.builder()
			.id("9c2e859d-e923-450d-85e3-b425cfa9f938")
			.patronGroupName(patronGroupName)
			.barcode("2093487")
			.username(username)
			.personal(User.PersonalDetails.builder()
				.firstName("First")
				.lastName("Special Pin Test")
				.middleName("Middle")
				.build())
			.build());

		// Act
		final var fetchedPatron = singleValueFrom(client.getPatronByUsername(username));

		// Assert
		assertThat(fetchedPatron, allOf(
			notNullValue(),
			hasLocalIds("9c2e859d-e923-450d-85e3-b425cfa9f938"),
			hasLocalPatronType(patronGroupName),
			hasCanonicalPatronType("canonical-patron-type"),
			hasLocalBarcodes("2093487"),
			hasNoHomeLibraryCode(),
			hasLocalNames("First", "Middle", "Special Pin Test"),
			isNotBlocked()
		));
	}

	@Test
	void successfullyGetPatronByLocalId() {
		// Arrange
		final var id = "9c2e859d-e923-450d-85e3-b425cfa9f938";
		final var patronGroupName = "undergraduate";

		referenceValueMappingFixture.definePatronTypeMapping(HOST_LMS_CODE,
			patronGroupName, "DCB", "canonical-patron-type");

		mockFolioFixture.mockGetUsersWithQuery("id", id, User.builder()
			.id("9c2e859d-e923-450d-85e3-b425cfa9f938")
			.patronGroupName(patronGroupName)
			.barcode("2093487")
			.personal(User.PersonalDetails.builder()
				.firstName("First")
				.lastName("Special Pin Test")
				.middleName("Middle")
				.build())
			.build());

		// Act
		final var fetchedPatron = singleValueFrom(client.getPatronByIdentifier(id));

		// Assert
		assertThat(fetchedPatron, allOf(
			notNullValue(),
			hasLocalIds("9c2e859d-e923-450d-85e3-b425cfa9f938"),
			hasLocalPatronType(patronGroupName),
			hasCanonicalPatronType("canonical-patron-type"),
			hasLocalBarcodes("2093487"),
			hasNoHomeLibraryCode(),
			hasLocalNames("First", "Middle", "Special Pin Test"),
			isNotBlocked()
		));
	}

	@Test
	void findVirtualPatronShouldFailWhenMultipleUsersFoundForBarcode() {
		// Arrange
		final var barcode = "6349673";

		final var patron = createPatron(randomUUID(), barcode);

		mockFolioFixture.mockGetUsersWithQuery("barcode", barcode,
			User.builder()
				.id(randomUUID().toString())
				.build(),
			User.builder()
				.id(randomUUID().toString())
				.build());

		// Act
		final var exception = assertThrows(MultipleVirtualPatronsFound.class, () ->
			singleValueFrom(client.findVirtualPatron(patron)));

		// Assert
		assertThat(exception, hasMessage(
			"Multiple Virtual Patrons Found: Multiple users found in Host LMS: \"folio-lms-client-patron-tests\" for query: \"barcode==\"6349673\"\""));
	}

	@Test
	void findVirtualPatronShouldFailWhenRequestingPatronHasNoBarcode() {
		// Arrange
		final var patronId = randomUUID();

		final var patron = createPatron(patronId, null);

		// Act
		final var exception = assertThrows(FailedToFindVirtualPatronException.class, () ->
			singleValueFrom(client.findVirtualPatron(patron)));

		// Assert
		assertThat(exception, hasMessage(
			"Cannot find virtual patron because requesting patron: \"" + patronId + "\" has no barcode"));
	}

	@Test
	void findVirtualPatronShouldFailWhenApiKeyIsInvalid(MockServerClient mockServerClient) {
		// Arrange
		final var barcode = "1227264";
		final var apiKey = "eyJzIjoidTBxaEZjWFd1YiIsInQiOiJpbnZhbGlkVGVuYW50IiwidSI6ImludmFsaWRVc2VyIn0=";

		final var mockInvalidFolioFixture = new MockFolioFixture(mockServerClient, "invalid-folio", apiKey);

		final var patron = createPatron(randomUUID(), barcode);

		hostLmsFixture.createFolioHostLms("INVALID-FOLIO", "https://invalid-folio",
			apiKey, "", "");

		mockInvalidFolioFixture.mockGetUsersWithQuery("barcode", barcode, response()
			.withStatusCode(401)
			.withBody(json(MockFolioFixture.ErrorResponse.builder()
				.code(401)
				.errorMessage("Cannot get system connection properties for user with name: invalidUser, for tenant: invalidTenant")
				.build())));

		// Act
		final var client = hostLmsFixture.createClient("INVALID-FOLIO");

		final var exception = assertThrows(InvalidApiKeyException.class, () ->
			singleValueFrom(client.findVirtualPatron(patron)));

		// Assert
		assertThat(exception, hasProperty("cause",
			instanceOf(HttpClientResponseException.class)));
	}

	@Test
	void createVirtualPatronShouldAlwaysReturnNewId() {
		// Arrange
		final var virtualPatronToCreate = org.olf.dcb.core.interaction.Patron.builder()
			.build();

		// Act
		final var firstGeneratedPatronId = singleValueFrom(client.createPatron(virtualPatronToCreate));
		final var secondGeneratedPatronId = singleValueFrom(client.createPatron(virtualPatronToCreate));

		// Assert
		assertThat("First patron ID should not be null",
			firstGeneratedPatronId, is(notNullValue()));

		assertThat("Second patron ID should not be null",
			secondGeneratedPatronId, is(notNullValue()));

		assertThat("Generated patron IDs should be different",
			firstGeneratedPatronId, not(equalToObject(secondGeneratedPatronId)));
	}

	@Test
	void updateVirtualPatronShouldSucceedWhenUserFoundById() {
		// Arrange
		final var localId = UUID.randomUUID().toString();
		final var barcode = "6847672185";
		final var patronGroupName = "undergraduate";

		referenceValueMappingFixture.definePatronTypeMapping(HOST_LMS_CODE,
			patronGroupName, "DCB", "canonical-patron-type");

		mockFolioFixture.mockGetUsersWithQuery("id", localId,
			User.builder()
				.id(localId)
				.patronGroupName(patronGroupName)
				.barcode(barcode)
				.personal(User.PersonalDetails.builder()
					.firstName("first name")
					.middleName("middle name")
					.lastName("last name")
					.preferredFirstName("preferred first name")
					.build())
				.build());

		// Act
		final var updatedPatron = singleValueFrom(client.updatePatron(localId, ""));

		// Assert
		assertThat(updatedPatron, allOf(
			is(notNullValue()),
			hasLocalIds(localId),
			hasLocalPatronType(patronGroupName),
			hasCanonicalPatronType("canonical-patron-type"),
			hasLocalBarcodes(barcode),
			hasNoHomeLibraryCode(),
			hasLocalNames("first name", "middle name", "last name")
		));
	}

	@Test
	void updateVirtualPatronShouldFailWhenNoUserIsFound() {
		// Arrange
		final var localId = UUID.randomUUID().toString();
		final var patronGroup = randomUUID().toString();

		referenceValueMappingFixture.definePatronTypeMapping(HOST_LMS_CODE,
			patronGroup, "DCB", "canonical-patron-type");

		mockFolioFixture.mockGetUsersWithQuery("id", localId);

		// Act
		final var exception = assertThrows(FailedToFindVirtualPatronException.class,
			() -> singleValueFrom(client.updatePatron(localId, patronGroup)));

		// Assert
		assertThat(exception, hasMessage(
			"Cannot find virtual patron because no local record was found with ID: \"" + localId
				+ "\" in Host LMS: \"folio-lms-client-patron-tests\""));
	}

	@Test
	void updateVirtualPatronShouldFailWhenMultipleUsersFound() {
		// Arrange
		final var localId = UUID.randomUUID().toString();
		final var patronGroup = randomUUID().toString();

		referenceValueMappingFixture.definePatronTypeMapping(HOST_LMS_CODE,
			patronGroup, "DCB", "canonical-patron-type");

		mockFolioFixture.mockGetUsersWithQuery("id", localId,
			User.builder()
				.id(randomUUID().toString())
				.build(),
			User.builder()
				.id(randomUUID().toString())
				.build());

		// Act
		final var exception = assertThrows(MultipleUsersFoundException.class,
			() -> singleValueFrom(client.updatePatron(localId, patronGroup)));

		// Assert
		assertThat(exception, hasMessage(
			"Multiple users found in Host LMS: \"folio-lms-client-patron-tests\" for query: \"id==\"" + localId + "\"\""));
	}

	private static Patron createPatron(UUID id, String localBarcode) {
		return Patron.builder()
			.id(id)
			.patronIdentities(List.of(
				PatronIdentity.builder()
					.homeIdentity(true)
					.localBarcode(localBarcode)
					.build()
			))
			.build();
	}
}
