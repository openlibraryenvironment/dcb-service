package org.olf.dcb.api;

import static java.time.ZoneOffset.UTC;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.security.RoleNames.CONSORTIUM_ADMIN;
import static org.olf.dcb.test.IdentifierGenerator.generateBarcode;
import static org.olf.dcb.test.IdentifierGenerator.generateNumericLocalId;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.polaris.MockPolarisFixture;
import org.olf.dcb.core.interaction.polaris.PAPIClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Event;
import org.olf.dcb.core.model.EventType;
import org.olf.dcb.core.model.InactiveSupplierRequest;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.core.model.PatronRequest.Status;
import org.olf.dcb.core.model.SupplierRequest;
import org.olf.dcb.request.fulfilment.VirtualPatronRemovalResult;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.EventLogFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.InactiveSupplierRequestsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.olf.dcb.test.SupplierRequestsFixture;
import org.olf.dcb.test.TestResourceLoaderProvider;

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
 * Consortium staff remove the virtual patrons DCB made for a borrower at other libraries: a Polaris
 * lender, which can only expire one, and a Sierra lender, which deletes. Both systems sit behind
 * MockServer, so each case is what staff send and what those systems answer.
 */
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class VirtualPatronRemovalApiTests {
	private static final String HOME_LMS = "vpr-home";
	private static final String HOME_AGENCY = "vpr-home-agency";
	private static final String POLARIS_LMS = "vpr-polaris";
	private static final String POLARIS_HOST = "vpr-polaris.example.com";
	private static final String POLARIS_AGENCY = "vpr-polaris-agency";
	private static final String SIERRA_LMS = "vpr-sierra";
	private static final String SIERRA_BASE_URL = "https://vpr-sierra.example.com";
	private static final String SIERRA_AGENCY = "vpr-sierra-agency";
	private static final String KEY = "vpr-key";
	private static final String SECRET = "vpr-secret";

	@Inject
	@Client("/")
	private HttpClient client;

	@Inject
	private TestResourceLoaderProvider testResourceLoaderProvider;
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
	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;
	@Inject
	private InactiveSupplierRequestsFixture inactiveSupplierRequestsFixture;
	@Inject
	private EventLogFixture eventLogFixture;

	private DataHostLms homeSystem;
	private DataHostLms polarisSystem;
	private DataHostLms sierraSystem;
	private DataAgency homeAgency;
	private DataAgency polarisAgency;
	private DataAgency sierraAgency;

	private MockPolarisFixture polaris;
	private SierraPatronsAPIFixture sierra;

	// The removal limit counts per member of staff, so each test acts as someone new
	private String staff;
	private String staffToken;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		patronFixture.deleteAllPatrons();
		eventLogFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();

		homeSystem = hostLmsFixture.createDummyHostLms(HOME_LMS);
		hostLmsFixture.createPolarisHostLms(POLARIS_LMS, KEY, SECRET, "https://" + POLARIS_HOST, "TEST", KEY, SECRET);
		polarisSystem = hostLmsFixture.findByCode(POLARIS_LMS);
		sierraSystem = hostLmsFixture.createSierraHostLms(SIERRA_LMS, KEY, SECRET, SIERRA_BASE_URL);

		homeAgency = agencyFixture.defineAgency(HOME_AGENCY, "Borrower's library", homeSystem);
		polarisAgency = agencyFixture.defineAgency(POLARIS_AGENCY, "Polaris lender", polarisSystem);
		sierraAgency = agencyFixture.defineAgency(SIERRA_AGENCY, "Sierra lender", sierraSystem);

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(POLARIS_LMS, 3, 3, "DCB", "UNDERGRADUATE");
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(SIERRA_LMS, 15, 15, "DCB", "UNDERGRADUATE");

		polaris = new MockPolarisFixture(POLARIS_HOST, mockServerClient, testResourceLoaderProvider);
		sierra = sierraApiFixtureProvider.patrons(mockServerClient);
	}

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		SierraTestUtils.mockFor(mockServerClient, SIERRA_BASE_URL).setValidCredentials(KEY, SECRET, "vpr-sierra-token", 3600);
		polaris.mockPapiStaffAuthentication();
		polaris.mockAppServicesStaffAuthentication();

		patronFixture.deleteAllPatrons();
		eventLogFixture.deleteAll();

		staff = "consortium-staff-" + randomUUID();
		staffToken = "vpr-token-" + randomUUID();
		TestStaticTokenValidator.add(staffToken, staff, List.of(CONSORTIUM_ADMIN), Map.of());
	}

	@AfterEach
	void afterEach() {
		TestStaticTokenValidator.invalidateToken(staffToken);
	}

	@Test
	void aPolarisVirtualPatronLeftBehindByABarcodeChangeIsExpiredWhenItIsTheOneDcbRecorded() {
		final var oldBarcode = generateBarcode();
		final var newBarcode = generateBarcode();
		final var borrower = borrower(newBarcode);
		final var oldVirtual = virtualAt(borrower, polarisSystem, polarisAgency, generateNumericLocalId(), oldBarcode);

		// Polaris's search for the borrower's current barcode finds the virtual patron made after the change
		polarisSearchFinds(newBarcode, generateNumericLocalId());
		polaris.mockGetPatronBarcode(polarisId(oldVirtual), "DCB-" + oldBarcode);
		polaris.mockUpdatePatronDates("DCB-" + oldBarcode, 0);

		final var result = removeOne(oldVirtual);

		assertThat(result.outcome(), is("EXPIRED"));
		assertThat(result.hostLmsCode(), is(POLARIS_LMS));
		polaris.verifyUpdatePatronDatesContains("DCB-" + oldBarcode, LocalDate.now(UTC).minusDays(1).toString());
		polaris.verifyNoUpdatePatronDates("DCB-" + newBarcode);
		assertThat(identityIdsOf(borrower), containsInAnyOrder(borrower.home().getId(), oldVirtual.getId()));
		assertOneRemovalEvent(oldVirtual, "EXPIRED");
	}

	@Test
	void aPolarisRecordWhoseBarcodeDcbDidNotRecordIsNotExpired() {
		final var newBarcode = generateBarcode();
		final var someoneElses = "DCB-" + generateBarcode();
		final var borrower = borrower(newBarcode);
		final var oldVirtual = virtualAt(borrower, polarisSystem, polarisAgency, generateNumericLocalId(), generateBarcode());

		polarisSearchFinds(newBarcode, generateNumericLocalId());
		polaris.mockGetPatronBarcode(polarisId(oldVirtual), someoneElses);
		polaris.mockUpdatePatronDates(someoneElses, 0);

		assertRefused(refusedOne(oldVirtual), HttpStatus.CONFLICT, "NOT_CONFIRMED_AT_LIBRARY");

		polaris.verifyNoUpdatePatronDates(someoneElses);
		assertThat(identityIdsOf(borrower), hasSize(2));
		assertThat(removalEvents(), is(empty()));
	}

	@Test
	void aPolarisVirtualPatronTheSearchNoLongerFindsIsLeftAloneWhenDcbRecordedNoBarcodeForIt() {
		final var newBarcode = generateBarcode();
		final var oldBarcode = generateBarcode();
		final var borrower = borrower(newBarcode);
		final var oldVirtual = virtualAt(borrower, polarisSystem, polarisAgency, generateNumericLocalId(), null);

		polarisSearchFinds(newBarcode, generateNumericLocalId());
		polaris.mockGetPatronBarcode(polarisId(oldVirtual), "DCB-" + oldBarcode);
		polaris.mockUpdatePatronDates("DCB-" + oldBarcode, 0);

		assertRefused(refusedOne(oldVirtual), HttpStatus.CONFLICT, "NOT_CONFIRMED_AT_LIBRARY");

		polaris.verifyNoUpdatePatronDates("DCB-" + oldBarcode);
		assertThat(identityIdsOf(borrower), hasSize(2));
	}

	@Test
	void aPolarisVirtualPatronPolarisNoLongerHasIsOnlyRemovedFromDcb() {
		final var newBarcode = generateBarcode();
		final var borrower = borrower(newBarcode);
		final var oldVirtual = virtualAt(borrower, polarisSystem, polarisAgency, generateNumericLocalId(), generateBarcode());

		polarisSearchFinds(newBarcode, generateNumericLocalId());
		polaris.mockGetPatronBarcodeNotFound(polarisId(oldVirtual));

		assertThat(removeOne(oldVirtual).outcome(), is("ALREADY_GONE"));

		assertThat(identityIdsOf(borrower), contains(borrower.home().getId()));
		assertOneRemovalEvent(oldVirtual, "ALREADY_GONE");
	}

	@Test
	void duplicateVirtualPatronsAtPolarisAreLeftForTheLibraryToResolve() {
		final var homeBarcode = generateBarcode();
		final var borrower = borrower(homeBarcode);
		final var virtual = virtualAt(borrower, polarisSystem, polarisAgency, generateNumericLocalId(), homeBarcode);

		polaris.mockPatronSearch(homeBarcode, List.of(
			searchRow(generateNumericLocalId(), "DCB-" + homeBarcode),
			searchRow(generateNumericLocalId(), "DCB-" + homeBarcode)));
		polaris.mockGetPatronBarcode(polarisId(virtual), "DCB-" + homeBarcode);
		polaris.mockUpdatePatronDates("DCB-" + homeBarcode, 0);

		assertRefused(refusedOne(virtual), HttpStatus.CONFLICT, "NOT_CONFIRMED_AT_LIBRARY");

		polaris.verifyNoUpdatePatronDates("DCB-" + homeBarcode);
		assertThat(identityIdsOf(borrower), hasSize(2));
		assertThat(removalEvents(), is(empty()));
	}

	@Test
	void aRealPolarisPatronAmongTheMatchesStopsTheRemoval() {
		final var homeBarcode = generateBarcode();
		final var borrower = borrower(homeBarcode);
		final var virtual = virtualAt(borrower, polarisSystem, polarisAgency, generateNumericLocalId(), homeBarcode);

		polaris.mockPatronSearch(homeBarcode, List.of(
			searchRow(polarisId(virtual), "DCB-" + homeBarcode),
			searchRow(generateNumericLocalId(), homeBarcode)));
		polaris.mockGetPatronBarcode(polarisId(virtual), "DCB-" + homeBarcode);
		polaris.mockUpdatePatronDates("DCB-" + homeBarcode, 0);

		assertRefused(refusedOne(virtual), HttpStatus.CONFLICT, "NOT_CONFIRMED_AT_LIBRARY");

		polaris.verifyNoUpdatePatronDates("DCB-" + homeBarcode);
	}

	@Test
	void theBorrowersOwnLibraryAccountIsNeverRemoved() {
		final var borrower = borrower(generateBarcode());

		assertRefused(refusedOne(borrower.home()), HttpStatus.CONFLICT, "NOT_A_VIRTUAL_PATRON");

		assertThat(identityIdsOf(borrower), contains(borrower.home().getId()));
		assertThat(removalEvents(), is(empty()));
	}

	@Test
	void aRecordThatIsSomeonesOwnAccountAtTheLenderIsNeverRemoved() {
		final var sharedSierraId = String.valueOf(generateNumericLocalId());
		final var borrower = borrower(generateBarcode());
		final var virtual = virtualAt(borrower, sierraSystem, sierraAgency, sharedSierraId);
		final var sierraPatron = patronFixture.savePatron("sierra-branch");
		identity(sierraPatron, sierraSystem, sharedSierraId, true, "[" + generateBarcode() + "]", sierraAgency);

		sierraSearchFinds(borrower, sharedSierraId);
		sierra.mockDeletePatron(sharedSierraId);

		assertRefused(refusedOne(virtual), HttpStatus.CONFLICT, "NOT_A_VIRTUAL_PATRON");

		sierra.verifyNoDeletePatronRequestMade(sharedSierraId);
		assertThat(patronFixture.findIdentities(sierraPatron), hasSize(1));
	}

	@ParameterizedTest
	@EnumSource(value = Status.class, names = {"LOANED", "ERROR", "RETURN_TRANSIT"})
	void aBorrowerWithARequestStillOpenKeepsEveryVirtualPatron(Status open) {
		final var sierraId = String.valueOf(generateNumericLocalId());
		final var borrower = borrower(generateBarcode());
		final var virtual = virtualAt(borrower, sierraSystem, sierraAgency, sierraId);
		request(borrower, Status.FINALISED);
		request(borrower, open);

		sierraSearchFinds(borrower, sierraId);
		sierra.mockDeletePatron(sierraId);

		assertRefused(refusedOne(virtual), HttpStatus.CONFLICT, "PATRON_HAS_ACTIVE_REQUESTS");
		assertRefused(refusedAll(borrower.patron()), HttpStatus.CONFLICT, "PATRON_HAS_ACTIVE_REQUESTS");

		sierra.verifyNoDeletePatronRequestMade(sierraId);
		assertThat(identityIdsOf(borrower), hasSize(2));
		assertThat(removalEvents(), is(empty()));
	}

	@Test
	void aSierraVirtualPatronIsDeletedAndTheBorrowersFinishedRequestsAreKept() {
		final var sierraId = String.valueOf(generateNumericLocalId());
		final var borrower = borrower(generateBarcode());
		final var virtual = virtualAt(borrower, sierraSystem, sierraAgency, sierraId);
		final var finalised = request(borrower, Status.FINALISED);
		final var archived = request(borrower, Status.ARCHIVED);
		final var supplied = suppliedBy(finalised, virtual);
		final var inactiveId = earlierSupplierOf(archived, virtual);

		final var otherBorrower = borrower(generateBarcode());
		final var othersVirtual = virtualAt(otherBorrower, sierraSystem, sierraAgency, String.valueOf(generateNumericLocalId()));
		final var othersSupplied = suppliedBy(request(otherBorrower, Status.FINALISED), othersVirtual);

		sierraSearchFinds(borrower, sierraId);
		sierra.mockDeletePatron(sierraId);

		final var result = removeOne(virtual);

		assertThat(result.outcome(), is("DELETED"));
		assertThat(result.hostLmsCode(), is(SIERRA_LMS));
		sierra.verifyDeletePatronRequestMade(sierraId);

		assertThat(identityIdsOf(borrower), contains(borrower.home().getId()));
		assertThat(supplierRequestsFixture.findById(supplied.getId()).getVirtualIdentity(), nullValue());
		assertThat(inactiveSupplierRequestsFixture.findAllFor(archived).getFirst().getId(), is(inactiveId));
		assertThat(inactiveSupplierRequestsFixture.findAllFor(archived).getFirst().getVirtualIdentity(), nullValue());

		assertThat(identityIdsOf(otherBorrower), hasSize(2));
		assertThat(supplierRequestsFixture.findById(othersSupplied.getId()).getVirtualIdentity(), notNullValue());

		assertOneRemovalEvent(virtual, "DELETED");
	}

	@Test
	void expiringAtASystemThatOnlyDeletesIsRefusedBeforeTheLibraryIsAsked() {
		final var sierraId = String.valueOf(generateNumericLocalId());
		final var borrower = borrower(generateBarcode());
		final var virtual = virtualAt(borrower, sierraSystem, sierraAgency, sierraId);

		sierraSearchFinds(borrower, sierraId);
		sierra.mockDeletePatron(sierraId);

		assertRefused(assertThrows(HttpClientResponseException.class,
			() -> removeOne(virtual.getId(), Map.of("mode", "EXPIRE"))), HttpStatus.CONFLICT, "NOT_SUPPORTED");

		sierra.verifyNoDeletePatronRequestMade(sierraId);
		assertThat(identityIdsOf(borrower), hasSize(2));
	}

	@Test
	void removingEveryVirtualPatronReportsEachLibraryAndLeavesTheBorrowersOwnAccount() {
		final var homeBarcode = generateBarcode();
		final var sierraId = String.valueOf(generateNumericLocalId());
		final var borrower = borrower(homeBarcode);
		final var atPolaris = virtualAt(borrower, polarisSystem, polarisAgency, generateNumericLocalId(), homeBarcode);
		final var atSierra = virtualAt(borrower, sierraSystem, sierraAgency, sierraId);

		polarisSearchFinds(homeBarcode, polarisId(atPolaris));
		polaris.mockGetPatronBarcode(polarisId(atPolaris), "DCB-" + homeBarcode);
		polaris.mockUpdatePatronDates("DCB-" + homeBarcode, 0);
		sierraSearchFinds(borrower, sierraId);
		sierra.mockDeletePatron(sierraId);

		final var results = removeAll(borrower.patron());

		assertThat(results.stream().map(result -> result.identityId() + " " + result.hostLmsCode() + " " + result.outcome()).toList(),
			containsInAnyOrder(
				atPolaris.getId() + " " + POLARIS_LMS + " EXPIRED",
				atSierra.getId() + " " + SIERRA_LMS + " DELETED"));

		polaris.verifyUpdatePatronDatesContains("DCB-" + homeBarcode, LocalDate.now(UTC).minusDays(1).toString());
		sierra.verifyDeletePatronRequestMade(sierraId);
		assertThat(identityIdsOf(borrower), containsInAnyOrder(borrower.home().getId(), atPolaris.getId()));
		assertThat(removalEvents(), hasSize(2));
	}

	private record Borrower(Patron patron, PatronIdentity home) {}

	private Borrower borrower(String homeBarcode) {
		final var patron = patronFixture.savePatron("home-branch");
		final var home = identity(patron, homeSystem, String.valueOf(generateNumericLocalId()), true,
			"[" + homeBarcode + "]", homeAgency);

		return new Borrower(patron, home);
	}

	private PatronIdentity virtualAt(Borrower borrower, DataHostLms system, DataAgency agency, Integer localId,
		String recordedBarcode) {

		return identity(borrower.patron(), system, String.valueOf(localId), false,
			recordedBarcode == null ? null : "[" + recordedBarcode + "]", agency);
	}

	private PatronIdentity virtualAt(Borrower borrower, DataHostLms system, DataAgency agency, String localId) {
		return identity(borrower.patron(), system, localId, false,
			borrower.home().getLocalBarcode(), agency);
	}

	private PatronIdentity identity(Patron patron, DataHostLms system, String localId, boolean homeIdentity,
		String localBarcode, DataAgency agency) {

		return patronFixture.saveIdentityAndReturn(PatronIdentity.builder()
			.id(randomUUID())
			.patron(patron)
			.hostLms(system)
			.localId(localId)
			.homeIdentity(homeIdentity)
			.localPtype("-")
			.localBarcode(localBarcode)
			.resolvedAgency(agency)
			.build());
	}

	private PatronRequest request(Borrower borrower, Status status) {
		return patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.patron(borrower.patron())
			.requestingIdentity(borrower.home())
			.patronHostlmsCode(HOME_LMS)
			.status(status)
			.build());
	}

	private SupplierRequest suppliedBy(PatronRequest request, PatronIdentity virtual) {
		return supplierRequestsFixture.saveSupplierRequest(SupplierRequest.builder()
			.id(randomUUID())
			.patronRequest(request)
			.hostLmsCode(virtual.getHostLms().getCode())
			.localItemId("item-" + randomUUID())
			.virtualIdentity(virtual)
			.isActive(false)
			.build());
	}

	private UUID earlierSupplierOf(PatronRequest request, PatronIdentity virtual) {
		final var id = randomUUID();

		inactiveSupplierRequestsFixture.save(InactiveSupplierRequest.builder()
			.id(id)
			.patronRequest(request)
			.hostLmsCode(virtual.getHostLms().getCode())
			.localItemId("item-" + randomUUID())
			.virtualIdentity(virtual)
			.build());

		return id;
	}

	private void polarisSearchFinds(String homeBarcode, Integer polarisPatronId) {
		final var barcode = "DCB-" + homeBarcode;

		polaris.mockPatronSearch(homeBarcode, barcode, polarisPatronId);
		polaris.mockGetPatronRawJson(polarisPatronId, """
			{"PatronID":%d,"PatronCodeID":3,"Barcode":"%s","OrganizationID":39}
			""".formatted(polarisPatronId, barcode));
		polaris.mockGetPatronBlocksSummary(polarisPatronId);
	}

	private static PAPIClient.PatronSearchRow searchRow(Integer polarisPatronId, String barcode) {
		return PAPIClient.PatronSearchRow.builder()
			.PatronID(polarisPatronId)
			.Barcode(barcode)
			.OrganizationID(18)
			.build();
	}

	private static Integer polarisId(PatronIdentity identity) {
		return Integer.valueOf(identity.getLocalId());
	}

	private void sierraSearchFinds(Borrower borrower, String sierraPatronId) {
		sierra.patronsQueryFoundResponse(borrower.home().getLocalId() + "@" + HOME_AGENCY, sierraPatronId);
		sierra.mockGetPatronById(sierraPatronId, SierraPatronRecord.builder()
			.id(Integer.parseInt(sierraPatronId))
			.patronType(15)
			.homeLibraryCode("vpr")
			.build());
	}

	private List<UUID> identityIdsOf(Borrower borrower) {
		return patronFixture.findIdentities(borrower.patron()).stream().map(PatronIdentity::getId).toList();
	}

	private List<Map<String, Object>> removalEvents() {
		return eventLogFixture.findAll().stream()
			.filter(event -> event.getType() == EventType.VIRTUAL_PATRON_REMOVED)
			.map(Event::getAdditionalData)
			.toList();
	}

	private void assertOneRemovalEvent(PatronIdentity identity, String outcome) {
		final var events = removalEvents();

		assertThat(events, hasSize(1));
		assertThat(events.getFirst(), hasEntry("identityId", identity.getId().toString()));
		assertThat(events.getFirst(), hasEntry("outcome", outcome));
		assertThat(events.getFirst(), hasEntry("staff", staff));
	}

	private VirtualPatronRemovalResult removeOne(PatronIdentity identity) {
		return removeOne(identity.getId(), Map.of());
	}

	private VirtualPatronRemovalResult removeOne(UUID identityId, Map<String, String> body) {
		return client.toBlocking().retrieve(
			HttpRequest.POST("/patrons/identities/" + identityId + "/virtual/retire", body).bearerAuth(staffToken),
			VirtualPatronRemovalResult.class);
	}

	private HttpClientResponseException refusedOne(PatronIdentity identity) {
		return assertThrows(HttpClientResponseException.class, () -> removeOne(identity));
	}

	private List<VirtualPatronRemovalResult> removeAll(Patron patron) {
		return client.toBlocking().retrieve(
			HttpRequest.POST("/patrons/" + patron.getId() + "/virtual-identities/retire", Map.of()).bearerAuth(staffToken),
			Argument.listOf(VirtualPatronRemovalResult.class));
	}

	private HttpClientResponseException refusedAll(Patron patron) {
		return assertThrows(HttpClientResponseException.class, () -> removeAll(patron));
	}

	static void assertRefused(HttpClientResponseException refusal, HttpStatus status, String code) {
		assertThat(refusal.getStatus(), is(status));
		assertThat(refusal.getResponse().getBody(Argument.mapOf(String.class, String.class)).orElseThrow(),
			hasEntry("code", code));
	}
}
