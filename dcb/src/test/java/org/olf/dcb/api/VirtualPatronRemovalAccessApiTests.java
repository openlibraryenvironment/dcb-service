package org.olf.dcb.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.api.VirtualPatronRemovalApiTests.assertRefused;
import static org.olf.dcb.security.RoleNames.CONSORTIUM_ADMIN;
import static org.olf.dcb.security.RoleNames.LIBRARY_ADMIN;
import static org.olf.dcb.test.IdentifierGenerator.generateNumericLocalId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.request.fulfilment.VirtualPatronRemovalResult;
import org.olf.dcb.security.AgencyClaims;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.EventLogFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.patrons.SierraPatronRecord;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * Who may remove a borrower's virtual patrons, and how many one member of staff may remove in a
 * minute. The lender is a Sierra behind MockServer, so "nothing sent" is a delete it never received.
 */
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class VirtualPatronRemovalAccessApiTests {
	private static final String HOME_LMS = "vpr-access-home";
	private static final String HOME_AGENCY = "vpr-access-home-agency";
	private static final String SIERRA_LMS = "vpr-access-sierra";
	private static final String SIERRA_BASE_URL = "https://vpr-access-sierra.example.com";
	private static final String SIERRA_AGENCY = "vpr-access-sierra-agency";
	private static final String OTHER_LMS = "vpr-access-other";
	private static final String OTHER_AGENCY = "vpr-access-other-agency";
	private static final String KEY = "vpr-access-key";
	private static final String SECRET = "vpr-access-secret";

	@Inject
	@Client("/")
	private HttpClient client;

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
	private EventLogFixture eventLogFixture;

	private DataHostLms homeSystem;
	private DataHostLms sierraSystem;
	private DataAgency homeAgency;
	private DataAgency sierraAgency;

	private SierraPatronsAPIFixture sierra;

	private final List<String> tokens = new ArrayList<>();

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		patronFixture.deleteAllPatrons();
		eventLogFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();

		homeSystem = hostLmsFixture.createDummyHostLms(HOME_LMS);
		sierraSystem = hostLmsFixture.createSierraHostLms(SIERRA_LMS, KEY, SECRET, SIERRA_BASE_URL);

		homeAgency = agencyFixture.defineAgency(HOME_AGENCY, "Borrower's library", homeSystem);
		sierraAgency = agencyFixture.defineAgency(SIERRA_AGENCY, "Sierra lender", sierraSystem);
		agencyFixture.defineAgency(OTHER_AGENCY, "Another library", hostLmsFixture.createDummyHostLms(OTHER_LMS));

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(SIERRA_LMS, 15, 15, "DCB", "UNDERGRADUATE");

		sierra = sierraApiFixtureProvider.patrons(mockServerClient);
	}

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		SierraTestUtils.mockFor(mockServerClient, SIERRA_BASE_URL)
			.setValidCredentials(KEY, SECRET, "vpr-access-sierra-token", 3600);

		patronFixture.deleteAllPatrons();
		eventLogFixture.deleteAll();
	}

	@AfterEach
	void afterEach() {
		tokens.forEach(TestStaticTokenValidator::invalidateToken);
		tokens.clear();
	}

	@Test
	void theBorrowersLibraryRemovesOneVirtualPatron() {
		final var borrower = borrowerWithSierraVirtualPatron();

		assertThat(removeOne(borrower.virtual(), libraryStaff(HOME_AGENCY)).outcome(), is("DELETED"));

		sierra.verifyDeletePatronRequestMade(borrower.virtual().getLocalId());
	}

	@Test
	void theBorrowersLibraryRemovesEveryVirtualPatron() {
		final var borrower = borrowerWithSierraVirtualPatron();

		final var results = removeAll(borrower.patron(), libraryStaff(HOME_AGENCY));

		assertThat(results.stream().map(VirtualPatronRemovalResult::outcome).toList(), contains("DELETED"));
		assertThat(identityIdsOf(borrower), contains(borrower.home().getId()));
	}

	@Test
	void theLibraryWhoseSystemHoldsTheVirtualPatronRemovesThatOne() {
		final var borrower = borrowerWithSierraVirtualPatron();

		assertThat(removeOne(borrower.virtual(), libraryStaff(SIERRA_AGENCY)).outcome(), is("DELETED"));

		sierra.verifyDeletePatronRequestMade(borrower.virtual().getLocalId());
	}

	@Test
	void theLibraryWhoseSystemHoldsTheVirtualPatronCannotRemoveEveryOneTheBorrowerHas() {
		final var borrower = borrowerWithSierraVirtualPatron();

		assertRefused(assertThrows(HttpClientResponseException.class,
			() -> removeAll(borrower.patron(), libraryStaff(SIERRA_AGENCY))), HttpStatus.FORBIDDEN, "FORBIDDEN");

		sierra.verifyNoDeletePatronRequestMade(borrower.virtual().getLocalId());
		assertThat(identityIdsOf(borrower), hasSize(2));
	}

	@Test
	void anyOtherLibraryIsRefusedAndNothingIsSentToTheLender() {
		final var borrower = borrowerWithSierraVirtualPatron();
		final var otherLibrary = libraryStaff(OTHER_AGENCY);

		assertRefused(assertThrows(HttpClientResponseException.class,
			() -> removeOne(borrower.virtual(), otherLibrary)), HttpStatus.FORBIDDEN, "FORBIDDEN");
		assertRefused(assertThrows(HttpClientResponseException.class,
			() -> removeAll(borrower.patron(), otherLibrary)), HttpStatus.FORBIDDEN, "FORBIDDEN");

		sierra.verifyNoDeletePatronRequestMade(borrower.virtual().getLocalId());
		assertThat(identityIdsOf(borrower), hasSize(2));
	}

	@Test
	void aLibraryCannotTellAnUnknownIdFromOneItMayNotTouch() {
		final var library = libraryStaff(HOME_AGENCY);
		final var consortium = consortiumStaff();

		assertRefused(assertThrows(HttpClientResponseException.class,
			() -> removeOne(randomUUID(), library)), HttpStatus.FORBIDDEN, "FORBIDDEN");
		assertRefused(assertThrows(HttpClientResponseException.class,
			() -> removeAll(randomUUID(), library)), HttpStatus.FORBIDDEN, "FORBIDDEN");
		assertRefused(assertThrows(HttpClientResponseException.class,
			() -> removeOne(randomUUID(), consortium)), HttpStatus.NOT_FOUND, "NOT_FOUND");
	}

	@Test
	void oneMemberOfStaffRemovesTenVirtualPatronsInAMinuteAndIsToldToWaitBeforeTheEleventh() {
		final var staff = consortiumStaff();

		for (int removal = 0; removal < 10; removal++) {
			assertThat(removeOne(borrowerWithSierraVirtualPatron().virtual(), staff).outcome(), is("DELETED"));
		}

		final var eleventh = borrowerWithSierraVirtualPatron();
		final var refusal = assertThrows(HttpClientResponseException.class, () -> removeOne(eleventh.virtual(), staff));

		assertRefused(refusal, HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REMOVALS");
		assertThat(refusal.getResponse().getHeaders().get("Retry-After"), is("60"));
		sierra.verifyNoDeletePatronRequestMade(eleventh.virtual().getLocalId());

		assertThat(removeOne(eleventh.virtual(), consortiumStaff()).outcome(), is("DELETED"));
	}

	private record Borrower(Patron patron, PatronIdentity home, PatronIdentity virtual) {}

	private Borrower borrowerWithSierraVirtualPatron() {
		final var sierraId = String.valueOf(generateNumericLocalId());
		final var patron = patronFixture.savePatron("home-branch");
		final var home = identity(patron, homeSystem, String.valueOf(generateNumericLocalId()), true, homeAgency);
		final var virtual = identity(patron, sierraSystem, sierraId, false, sierraAgency);

		sierra.patronsQueryFoundResponse(home.getLocalId() + "@" + HOME_AGENCY, sierraId);
		sierra.mockGetPatronById(sierraId, SierraPatronRecord.builder()
			.id(Integer.parseInt(sierraId))
			.patronType(15)
			.homeLibraryCode("vpr")
			.build());
		sierra.mockDeletePatron(sierraId);

		return new Borrower(patron, home, virtual);
	}

	private PatronIdentity identity(Patron patron, DataHostLms system, String localId, boolean homeIdentity,
		DataAgency agency) {

		return patronFixture.saveIdentityAndReturn(PatronIdentity.builder()
			.id(randomUUID())
			.patron(patron)
			.hostLms(system)
			.localId(localId)
			.homeIdentity(homeIdentity)
			.localPtype("-")
			.localBarcode("[" + localId + "]")
			.resolvedAgency(agency)
			.build());
	}

	private List<UUID> identityIdsOf(Borrower borrower) {
		return patronFixture.findIdentities(borrower.patron()).stream().map(PatronIdentity::getId).toList();
	}

	private String libraryStaff(String agencyCode) {
		return token(List.of(LIBRARY_ADMIN), Map.of(AgencyClaims.CODE, List.of(agencyCode)));
	}

	private String consortiumStaff() {
		return token(List.of(CONSORTIUM_ADMIN), Map.of());
	}

	// The removal limit counts per member of staff, so every token is someone new
	private String token(List<String> roles, Map<String, Object> claims) {
		final var token = "vpr-access-token-" + randomUUID();

		TestStaticTokenValidator.add(token, "staff-" + randomUUID(), roles, claims);
		tokens.add(token);

		return token;
	}

	private VirtualPatronRemovalResult removeOne(PatronIdentity identity, String token) {
		return removeOne(identity.getId(), token);
	}

	private VirtualPatronRemovalResult removeOne(UUID identityId, String token) {
		return client.toBlocking().retrieve(
			HttpRequest.POST("/patrons/identities/" + identityId + "/virtual/retire", Map.of()).bearerAuth(token),
			VirtualPatronRemovalResult.class);
	}

	private List<VirtualPatronRemovalResult> removeAll(Patron patron, String token) {
		return removeAll(patron.getId(), token);
	}

	private List<VirtualPatronRemovalResult> removeAll(UUID patronId, String token) {
		return client.toBlocking().retrieve(
			HttpRequest.POST("/patrons/" + patronId + "/virtual-identities/retire", Map.of()).bearerAuth(token),
			Argument.listOf(VirtualPatronRemovalResult.class));
	}
}
