package org.olf.dcb.api;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import java.util.List;

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
		assertThat(library.brandLogoUrl(), is(nullValue()));
		assertThat(library.brandLogoAlt(), is(nullValue()));
		assertThat(library.defaultThemeName(), is(nullValue()));
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

	private DataAgency defineAgency(String code, Boolean isBorrowingAgency) {
		return agencyFixture.defineAgency(DataAgency.builder()
			.id(randomUUID())
			.code(code)
			.name(code)
			.isSupplyingAgency(true)
			.isBorrowingAgency(isBorrowingAgency)
			.hostLms(hostLmsFixture.findByCode(HOST_LMS_CODE))
			.build());
	}

	private void defineLibraryFor(DataAgency agency, String logoUrl, String logoAlt,
		String themeName) {

		libraryFixture.saveLibrary(Library.builder()
			.id(randomUUID())
			.agencyCode(agency.getCode())
			.agency(agency)
			.fullName(agency.getCode())
			.shortName(agency.getCode())
			.abbreviatedName(agency.getCode())
			.brandLogoUrl(logoUrl)
			.brandLogoAlt(logoAlt)
			.defaultThemeName(themeName)
			.build());
	}

	private List<LibraryGeo> listLibraries(String uri) {
		return client.toBlocking().retrieve(HttpRequest.GET(uri),
			Argument.listOf(LibraryGeo.class));
	}
}
