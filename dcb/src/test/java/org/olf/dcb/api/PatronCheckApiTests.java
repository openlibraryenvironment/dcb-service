package org.olf.dcb.api;

import static java.lang.Integer.parseInt;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.oneOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;
import static org.olf.dcb.security.RoleNames.CONSORTIUM_ADMIN;
import static org.olf.dcb.security.RoleNames.LIBRARY_ADMIN;
import static org.olf.dcb.security.RoleNames.LIBRARY_READ_ONLY;
import static org.olf.dcb.test.IdentifierGenerator.generateBarcode;
import static org.olf.dcb.test.IdentifierGenerator.generateNumericLocalIdAsString;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.sierra.SierraApiFixtureProvider;
import org.olf.dcb.core.interaction.sierra.SierraPatronsAPIFixture;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.security.TestStaticTokenValidator;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import services.k_int.interaction.sierra.SierraTestUtils;
import services.k_int.interaction.sierra.patrons.Block;
import services.k_int.interaction.sierra.patrons.SierraPatronRecord;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * Library staff checking why one of their own patrons would be refused.
 * Two libraries share one Sierra, so a patron of the other must look like no patron at all.
 */
@MockServerMicronautTest
@TestInstance(PER_CLASS)
class PatronCheckApiTests {
	private static final String HOST_LMS = "patron-check-sierra";
	private static final String BASE_URL = "https://patron-check-tests.com";
	private static final String OWN_AGENCY = "check-own";
	private static final String OTHER_AGENCY = "check-other";
	private static final String OWN_LIBRARY_LOCATION = "own-home";
	private static final String OTHER_LIBRARY_LOCATION = "other-home";
	private static final int PATRON_TYPE = 15;

	private static final String OWN_STAFF_TOKEN = "patron-check-own-staff";
	private static final String READ_ONLY_TOKEN = "patron-check-read-only";
	private static final String CONSORTIUM_TOKEN = "patron-check-consortium";
	private static final String ADMIN_TOKEN = "patron-check-admin";

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

	private SierraPatronsAPIFixture sierraPatrons;
	private DataHostLms hostLms;

	@BeforeAll
	void beforeAll(MockServerClient mockServerClient) {
		final var key = "patron-check-key";
		final var secret = "patron-check-secret";

		SierraTestUtils.mockFor(mockServerClient, BASE_URL)
			.setValidCredentials(key, secret, "patron-check-token", 3600);

		TestStaticTokenValidator.add(OWN_STAFF_TOKEN, "own-staff",
			List.of(LIBRARY_ADMIN), Map.of("code", OWN_AGENCY));
		TestStaticTokenValidator.add(READ_ONLY_TOKEN, "read-only-staff",
			List.of(LIBRARY_READ_ONLY), Map.of("code", OWN_AGENCY));
		TestStaticTokenValidator.add(CONSORTIUM_TOKEN, "consortium-staff",
			List.of(CONSORTIUM_ADMIN), Map.of("code", OWN_AGENCY));
		TestStaticTokenValidator.add(ADMIN_TOKEN, "admin", List.of(ADMINISTRATOR), Map.of());

		patronFixture.deleteAllPatrons();
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		hostLms = hostLmsFixture.createSierraHostLms(HOST_LMS, key, secret, BASE_URL, "item");
		sierraPatrons = sierraApiFixtureProvider.patrons(mockServerClient, null);
	}

	@BeforeEach
	void beforeEach() {
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();

		defineAgency(OWN_AGENCY, OWN_LIBRARY_LOCATION);
		defineAgency(OTHER_AGENCY, OTHER_LIBRARY_LOCATION);

		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			HOST_LMS, PATRON_TYPE, PATRON_TYPE, "DCB", "UNDERGRAD");
	}

	@AfterAll
	void afterAll() {
		referenceValueMappingFixture.deleteAll();
		agencyFixture.deleteAll();
	}

	@Test
	void anEligiblePatronOfTheCallersLibraryIsEligibleAndNothingAboutThemIsReturned() {
		final var barcode = generateBarcode();

		patronAtSierra(barcode, patron(OWN_LIBRARY_LOCATION, barcode).build());

		final var body = check(barcode);

		assertThat(body.get("outcome"), is("ELIGIBLE"));
		assertThat((List<?>) body.get("reasons"), is(empty()));
		assertThat(body.toString(), not(containsString(barcode)));
		assertThat(body.toString(), not(containsString("Bob")));
	}

	@Test
	void aBlockedPatronIsRefusedAsBlocked() {
		final var barcode = generateBarcode();

		patronAtSierra(barcode, patron(OWN_LIBRARY_LOCATION, barcode)
			.blockInfo(Block.builder().code("blocked").build())
			.build());

		final var body = check(barcode);

		assertThat(body.get("outcome"), is("REFUSED"));
		assertThat((List<?>) body.get("reasons"), contains("BLOCKED"));
	}

	@Test
	void anExpiredPatronIsRefusedAsExpired() {
		final var barcode = generateBarcode();

		patronAtSierra(barcode, patron(OWN_LIBRARY_LOCATION, barcode)
			.expirationDate("2001-01-01")
			.build());

		// Sierra also reports an expired record as inactive; staff are told both
		final List<String> reasons = ((List<?>) check(barcode).get("reasons")).stream()
			.map(String::valueOf)
			.toList();
		assertThat(reasons, hasItem("EXPIRED"));
	}

	@Test
	void aPatronOfAnotherLibraryOnTheSameSystemIsNotFound() {
		final var barcode = generateBarcode();

		patronAtSierra(barcode, patron(OTHER_LIBRARY_LOCATION, barcode)
			.blockInfo(Block.builder().code("blocked").build())
			.build());

		final var body = check(barcode);

		assertThat(body.get("outcome"), is("REFUSED"));
		assertThat((List<?>) body.get("reasons"), contains("NOT_FOUND"));
	}

	@Test
	void anUnmappedHomeLibraryOnASharedSystemIsNotFoundRatherThanExplained() {
		final var barcode = generateBarcode();

		patronAtSierra(barcode, patron("nobody-maps-this", barcode).build());

		assertThat((List<?>) check(barcode).get("reasons"), contains("NOT_FOUND"));
	}

	@Test
	void anUnmappedHomeLibraryOnTheCallersOwnSystemIsExplained() {
		agencyFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();
		defineAgency(OWN_AGENCY, OWN_LIBRARY_LOCATION);
		referenceValueMappingFixture.defineNumericPatronTypeRangeMapping(
			HOST_LMS, PATRON_TYPE, PATRON_TYPE, "DCB", "UNDERGRAD");

		final var barcode = generateBarcode();

		patronAtSierra(barcode, patron("nobody-maps-this", barcode).build());

		assertThat((List<?>) check(barcode).get("reasons"), contains("NO_HOME_LIBRARY_MAPPING"));
	}

	@Test
	void aPatronTheSystemDoesNotKnowIsNotFound() {
		final var barcode = generateBarcode();

		sierraPatrons.patronNotFoundResponse("u", barcode);
		sierraPatrons.patronNotFoundResponse("b", barcode);

		assertThat((List<?>) check(barcode).get("reasons"), contains("NOT_FOUND"));
	}

	@Test
	void anotherLibraryCannotBeNamedInTheBody() {
		final var failure = assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(request(
				Map.of("agencyCode", OTHER_AGENCY, "identifier", generateBarcode()), OWN_STAFF_TOKEN)));

		assertThat(failure.getStatus(), is(HttpStatus.FORBIDDEN));
	}

	@Test
	void readOnlyStaffCannotCheckPatrons() {
		final var failure = assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(request(
				Map.of("identifier", generateBarcode()), READ_ONLY_TOKEN)));

		// dcb-service refuses a role it does not allow as unauthenticated, as BrandAssetApiTests accepts
		assertThat(failure.getStatus(), is(oneOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)));
	}

	@Test
	void consortiumStaffCanCheckAPatronOfAnotherLibrary() {
		final var barcode = generateBarcode();

		patronAtSierra(barcode, patron(OTHER_LIBRARY_LOCATION, barcode)
			.blockInfo(Block.builder().code("blocked").build())
			.build());

		final var body = check(barcode, OTHER_AGENCY, CONSORTIUM_TOKEN);

		assertThat(body.get("outcome"), is("REFUSED"));
		assertThat((List<?>) body.get("reasons"), contains("BLOCKED"));
	}

	@Test
	void anAdministratorCanCheckAPatronOfAnyLibraryOnTheSystemAsked() {
		final var barcode = generateBarcode();

		patronAtSierra(barcode, patron(OTHER_LIBRARY_LOCATION, barcode)
			.blockInfo(Block.builder().code("blocked").build())
			.build());

		assertThat((List<?>) check(barcode, OWN_AGENCY, ADMIN_TOKEN).get("reasons"), contains("BLOCKED"));
	}

	@Test
	void consortiumStaffAreToldAnUnmappedHomeLibraryOnASharedSystem() {
		final var barcode = generateBarcode();

		patronAtSierra(barcode, patron("nobody-maps-this", barcode).build());

		assertThat((List<?>) check(barcode, OWN_AGENCY, CONSORTIUM_TOKEN).get("reasons"),
			contains("NO_HOME_LIBRARY_MAPPING"));
	}

	@Test
	void consortiumStaffMustSayWhichLibraryToAsk() {
		final var failure = assertThrows(HttpClientResponseException.class,
			() -> client.toBlocking().exchange(request(
				Map.of("identifier", generateBarcode()), CONSORTIUM_TOKEN)));

		assertThat(failure.getStatus(), is(HttpStatus.BAD_REQUEST));
	}

	@Test
	void theIdentifierAppearsInNoLogLineAtAnyLevel() {
		final var barcode = generateBarcode();

		patronAtSierra(barcode, patron(OWN_LIBRARY_LOCATION, barcode)
			.blockInfo(Block.builder().code("blocked").build())
			.build());

		// DCB's own code at every level. Micronaut is captured at the level it runs at: its HTTP
		// client prints request URLs, which carry a Sierra patron search, only when raised past the
		// WARN that logback.xml pins
		final var ours = List.of(
			(Logger) LoggerFactory.getLogger("org.olf.dcb"),
			(Logger) LoggerFactory.getLogger("services.k_int"));
		final var framework = (Logger) LoggerFactory.getLogger("io.micronaut");
		final var captured = new ListAppender<ILoggingEvent>();
		captured.start();

		final var previousLevels = ours.stream().map(Logger::getLevel).toList();

		try {
			ours.forEach(logger -> {
				logger.setLevel(Level.TRACE);
				logger.addAppender(captured);
			});
			framework.addAppender(captured);

			check(barcode);
		}
		finally {
			framework.detachAppender(captured);
			for (int index = 0; index < ours.size(); index++) {
				ours.get(index).detachAppender(captured);
				ours.get(index).setLevel(previousLevels.get(index));
			}
		}

		final var leaking = captured.list.stream()
			.map(ILoggingEvent::getFormattedMessage)
			.filter(message -> message != null && message.contains(barcode))
			.toList();

		assertThat(captured.list, is(not(empty())));
		assertThat(leaking, is(empty()));
	}

	private void defineAgency(String code, String homeLocation) {
		agencyFixture.defineAgency(DataAgency.builder()
			.id(randomUUID())
			.code(code)
			.name(code)
			.isSupplyingAgency(true)
			.isBorrowingAgency(true)
			.hostLms(hostLms)
			.build());

		referenceValueMappingFixture.defineLocationToAgencyMapping(HOST_LMS, homeLocation, code);
	}

	private void patronAtSierra(String barcode, SierraPatronRecord patron) {
		sierraPatrons.patronNotFoundResponse("u", barcode);
		sierraPatrons.patronFoundResponse("b", barcode, patron);
	}

	private static SierraPatronRecord.SierraPatronRecordBuilder patron(String homeLibrary, String barcode) {
		final var localId = generateNumericLocalIdAsString();

		return SierraPatronRecord.builder()
			.id(parseInt(localId))
			.patronType(PATRON_TYPE)
			.homeLibraryCode(homeLibrary)
			.barcodes(List.of(barcode))
			.names(List.of("Bob"));
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> check(String identifier) {
		return client.toBlocking().retrieve(
			request(Map.of("identifier", identifier), OWN_STAFF_TOKEN), Map.class);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> check(String identifier, String agencyCode, String token) {
		return client.toBlocking().retrieve(
			request(Map.of("identifier", identifier, "agencyCode", agencyCode), token), Map.class);
	}

	private static HttpRequest<?> request(Map<String, String> body, String token) {
		return HttpRequest.POST("/patrons/eligibility-checks", body).bearerAuth(token);
	}
}
