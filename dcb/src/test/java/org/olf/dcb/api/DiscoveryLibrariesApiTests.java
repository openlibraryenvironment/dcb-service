package org.olf.dcb.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.api.DiscoveryLibrariesController.LibraryGeo;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Library;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.LibraryFixture;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;

/**
 * A library that is not enabled for borrowing cannot have a request placed
 * through it — ResolvePatronPreflightCheck rejects it — so discovery must not
 * offer it in the institution picker by default.
 *
 * The brand tests below (N-1.3) guard the join rather than the columns: the
 * directory reaches `library` from `agency` across an FK with no unique
 * constraint, and getting that wrong duplicates or drops entries in the login
 * picker rather than merely losing a logo.
 */
@DcbTest
@TestInstance(PER_CLASS)
class DiscoveryLibrariesApiTests {
	private static final String HOST_LMS_CODE = "discovery-libraries-host-lms";

	@Inject
	@Client("/")
	private HttpClient client;

	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private LibraryFixture libraryFixture;

	@BeforeAll
	void beforeAll() {
		hostLmsFixture.deleteAll();
		hostLmsFixture.createSierraHostLms(HOST_LMS_CODE);
	}

	@BeforeEach
	void beforeEach() {
		// Libraries first: library.agency_id references agency(id).
		libraryFixture.deleteAll();
		agencyFixture.deleteAll();
	}

	/**
	 * Leave no library row behind.
	 *
	 * Rows left here outlive the class, and the next class to call agencyFixture.deleteAll()
	 * dies on library_agency_id_fkey, depending on which test JUnit ran last.
	 */
	@AfterAll
	void afterAll() {
		libraryFixture.deleteAll();
		agencyFixture.deleteAll();
	}

	@Test
	void shouldOnlyReturnLibrariesEnabledForBorrowingByDefault() {
		// Arrange
		defineAgency("borrowing-agency", true);
		defineAgency("non-borrowing-agency", false);
		defineAgency("unknown-borrowing-agency", null);

		// Act
		final var libraries = listLibraries("/discovery/libraries");

		// Assert
		assertThat(libraries.stream().map(LibraryGeo::code).toList(),
			contains("borrowing-agency"));
	}

	@Test
	void shouldReturnEveryLibraryWhenIncludeAllIsRequested() {
		// Arrange
		defineAgency("borrowing-agency", true);
		defineAgency("non-borrowing-agency", false);
		defineAgency("unknown-borrowing-agency", null);

		// Act
		final var libraries = listLibraries("/discovery/libraries?includeAll=true");

		// Assert
		assertThat(libraries.stream().map(LibraryGeo::code).toList(),
			containsInAnyOrder("borrowing-agency", "non-borrowing-agency", "unknown-borrowing-agency"));
	}

	@Test
	void shouldReturnTheLibrarysBrandWhenItHasOne() {
		// Arrange
		final var agency = defineAgency("branded-agency", true);
		defineLibraryFor(agency, "https://example.com/branded.svg", "Branded Library", "kInt");

		// Act
		final var libraries = listLibraries("/discovery/libraries");

		// Assert
		assertThat(libraries, hasSize(1));

		final var library = libraries.get(0);
		assertThat(library.brandLogoUrl(), is("https://example.com/branded.svg"));
		assertThat(library.brandLogoAlt(), is("Branded Library"));
		assertThat(library.defaultThemeName(), is("kInt"));
	}

	/**
	 * The common case on every deployment that has not filled the fields in, and the
	 * one a plain INNER JOIN would silently break: an agency with no library row must
	 * stay in the directory, unbranded, or the login picker loses most of its entries
	 * the day the brand columns ship.
	 */
	@Test
	void shouldReturnAnAgencyWithNoLibraryRowUnbranded() {
		// Arrange
		defineAgency("agency-with-no-library", true);

		// Act
		final var libraries = listLibraries("/discovery/libraries");

		// Assert
		assertThat(libraries, hasSize(1));

		final var library = libraries.get(0);
		assertThat(library.code(), is("agency-with-no-library"));
		assertThat(library.name(), is("agency-with-no-library"));
		assertThat(library.brandLogoUrl(), is(nullValue()));
		assertThat(library.brandLogoAlt(), is(nullValue()));
		assertThat(library.defaultThemeName(), is(nullValue()));
	}

	@Test
	void shouldNameTheLibraryByItsFullNameRatherThanItsAgencyName() {
		// Arrange
		final var agency = defineAgency("WESTBROOK_UNIVERSITY_ALMA", true);
		libraryFixture.saveLibrary(libraryFor(agency)
			.fullName("Westbrook University Library")
			.build());

		// Act
		final var libraries = listLibraries("/discovery/libraries");

		// Assert
		assertThat(libraries.get(0).name(), is("Westbrook University Library"));
	}

	@Test
	void shouldFallBackToTheAgencyNameWhenTheLibraryFullNameIsBlank() {
		// Arrange
		final var agency = defineAgency("agency-with-blank-library-name", true);
		libraryFixture.saveLibrary(libraryFor(agency).fullName("  ").build());

		// Act
		final var libraries = listLibraries("/discovery/libraries");

		// Assert
		assertThat(libraries.get(0).name(), is("agency-with-blank-library-name"));
	}

	@Test
	void shouldOrderTheDirectoryByTheNameItReturns() {
		// Arrange
		libraryFixture.saveLibrary(libraryFor(defineAgency("a-agency", true))
			.fullName("Zeta Library")
			.build());
		libraryFixture.saveLibrary(libraryFor(defineAgency("b-agency", true))
			.fullName("Alpha Library")
			.build());

		// Act
		final var libraries = listLibraries("/discovery/libraries");

		// Assert
		assertThat(libraries.stream().map(LibraryGeo::code).toList(),
			contains("b-agency", "a-agency"));
	}

	/**
	 * library.agency_id carries no unique constraint, so nothing in the schema stops a
	 * second library row pointing at one agency. Under a plain LEFT JOIN that agency
	 * would appear TWICE in the directory — one library rendered as two institutions in
	 * the picker, and a duplicate entry in every consumer that caches this list. The
	 * LATERAL ... LIMIT 1 is what makes one agency one row, and this is the test that
	 * fails if somebody simplifies it away.
	 */
	@Test
	void shouldReturnOneEntryPerAgencyEvenWithSeveralLibraryRows() {
		// Arrange
		final var agency = defineAgency("agency-with-two-libraries", true);
		defineLibraryFor(agency, "https://example.com/one.svg", "One", "openRS");
		defineLibraryFor(agency, "https://example.com/two.svg", "Two", "kInt");

		// Act
		final var libraries = listLibraries("/discovery/libraries");

		// Assert
		assertThat(libraries.stream().map(LibraryGeo::code).toList(),
			contains("agency-with-two-libraries"));
	}

	/**
	 * The credential labels and the library's own site.
	 *
	 * A library whose members have a *borrower number* and a *password* must not be shown
	 * "Library card number" and "PIN" on the one screen where wrong words stop a sign-in.
	 */
	@Test
	void shouldReturnTheLibrarysOwnCredentialWordsAndSite() {
		// Arrange
		final var agency = defineAgency("labelled-agency", true);
		libraryFixture.saveLibrary(libraryFor(agency)
			.principalLabel("Borrower number")
			.secretLabel("Password")
			.patronWebsite("https://library.example.org")
			.supportUrl("https://library.example.org/help")
			.build());

		// Act
		final var libraries = listLibraries("/discovery/libraries");

		// Assert
		assertThat(libraries, hasSize(1));

		final var library = libraries.get(0);
		assertThat(library.principalLabel(), is("Borrower number"));
		assertThat(library.secretLabel(), is("Password"));
		assertThat(library.patronWebsite(), is("https://library.example.org"));

		// A DIFFERENT destination from the website: "your search is broken" and "when do
		// you open" are not the same question and rarely the same desk.
		assertThat(library.supportUrl(), is("https://library.example.org/help"));
	}

	/**
	 * Null is the common case and means "use your own default wording", never a blank
	 * label. A consumer that rendered these unconditionally would show an empty caption
	 * above the card field on most libraries in a consortium.
	 */
	@Test
	void shouldReturnNullCredentialWordsWhenTheLibraryHasNotSetThem() {
		// Arrange
		final var agency = defineAgency("unlabelled-agency", true);
		libraryFixture.saveLibrary(libraryFor(agency).build());

		// Act
		final var libraries = listLibraries("/discovery/libraries");

		// Assert
		final var library = libraries.get(0);
		assertThat(library.principalLabel(), is(nullValue()));
		assertThat(library.secretLabel(), is(nullValue()));
		assertThat(library.patronWebsite(), is(nullValue()));
		assertThat(library.supportUrl(), is(nullValue()));
	}

	/**
	 * The scheme decides WHICH sort key shelf browse walks, and the index carries
	 * one per scheme — so a discovery service that cannot read it cannot order the sequence
	 * at all, and guessing would walk the wrong one and look entirely ordinary doing it.
	 */
	@Test
	void shouldReturnTheClassificationSchemeTheLibraryShelvesBy() {
		// Arrange
		final var agency = defineAgency("classified-agency", true);
		libraryFixture.saveLibrary(libraryFor(agency)
			.classificationScheme("DEWEY")
			.build());

		// Act
		final var libraries = listLibraries("/discovery/libraries");

		// Assert
		assertThat(libraries.get(0).classificationScheme(), is("DEWEY"));
	}

	@Test
	void shouldReturnNullWhenTheLibraryHasNotSaidWhichSchemeItShelvesBy() {
		// The common case, and it must stay distinguishable from a scheme: shelf browse is
		// withheld rather than guessed.
		final var agency = defineAgency("unclassified-agency", true);
		libraryFixture.saveLibrary(libraryFor(agency).build());

		assertThat(listLibraries("/discovery/libraries").get(0).classificationScheme(),
			is(nullValue()));
	}

	private DataAgency defineAgency(String code, Boolean isBorrowingAgency) {
		return defineAgency(code, isBorrowingAgency, null);
	}

	private DataAgency defineAgency(String code, Boolean isBorrowingAgency, String authProfile) {
		return agencyFixture.defineAgency(DataAgency.builder()
			.id(randomUUID())
			.code(code)
			.name(code)
			.isSupplyingAgency(true)
			.isBorrowingAgency(isBorrowingAgency)
			.authProfile(authProfile)
			.hostLms(hostLmsFixture.findByCode(HOST_LMS_CODE))
			.build());
	}

	/**
	 * A discovery service offers sign-in with a library account only where the agency has
	 * an authProfile. Offering it elsewhere sends the patron to a check the Host LMS
	 * refuses, which reads to them as a wrong password.
	 */
	@Test
	void shouldSayWhetherALibraryAcceptsALibraryCard() {
		// Arrange
		defineAgency("card-agency", true, "BASIC/BARCODE+PASSWORD");
		defineAgency("sso-only-agency", true, null);
		defineAgency("saml-agency", true, "saml");

		// Act
		final var libraries = listLibraries("/discovery/libraries");

		// Assert
		assertThat(libraries.stream()
			.filter(library -> library.code().equals("card-agency"))
			.findFirst().orElseThrow().acceptsLibraryCard(), is(true));

		assertThat(libraries.stream()
			.filter(library -> library.code().equals("sso-only-agency"))
			.findFirst().orElseThrow().acceptsLibraryCard(), is(false));

		assertThat("a SAML library signs its patrons in at their institution", libraries.stream()
			.filter(library -> library.code().equals("saml-agency"))
			.findFirst().orElseThrow().acceptsLibraryCard(), is(false));

		// Asserted on the wire, not only through the typed record: a boolean component reads
		// false whether the field was sent or omitted
		final var body = client.toBlocking().retrieve(HttpRequest.GET("/discovery/libraries"));

		assertThat(body, containsString("\"acceptsLibraryCard\":true"));
		assertThat(body, containsString("\"acceptsLibraryCard\":false"));
	}

	@Test
	void shouldSayWhetherALibraryChecksAPinOrAPassword() {
		// Arrange
		defineAgency("pin-agency", true, "BASIC/BARCODE+PIN");
		defineAgency("password-agency", true, "BASIC/BARCODE+PASSWORD");
		defineAgency("surname-agency", true, "BASIC/UNIQUE-ID+NAME");
		defineAgency("no-card-agency", true, null);

		// Act
		final var libraries = listLibraries("/discovery/libraries");

		// Assert
		assertThat(secretKindOf(libraries, "pin-agency"), is("PIN"));
		assertThat(secretKindOf(libraries, "password-agency"), is("PASSWORD"));
		assertThat(secretKindOf(libraries, "surname-agency"), is("NAME"));
		assertThat(secretKindOf(libraries, "no-card-agency"), is(nullValue()));
	}

	private static String secretKindOf(List<LibraryGeo> libraries, String code) {
		return libraries.stream()
			.filter(library -> library.code().equals(code))
			.findFirst().orElseThrow().secretKind();
	}

	private void defineLibraryFor(DataAgency agency, String logoUrl, String logoAlt,
		String themeName) {

		libraryFixture.saveLibrary(libraryFor(agency)
			.brandLogoUrl(logoUrl)
			.brandLogoAlt(logoAlt)
			.defaultThemeName(themeName)
			.build());
	}

	private Library.LibraryBuilder libraryFor(DataAgency agency) {
		return Library.builder()
			.id(randomUUID())
			.agencyCode(agency.getCode())
			.agency(agency)
			.fullName(agency.getCode())
			.shortName(agency.getCode())
			.abbreviatedName(agency.getCode());
	}

	private List<LibraryGeo> listLibraries(String uri) {
		return client.toBlocking().retrieve(HttpRequest.GET(uri),
			Argument.listOf(LibraryGeo.class));
	}
}
