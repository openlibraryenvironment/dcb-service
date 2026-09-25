package org.olf.dcb.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.authentication.ServerAuthentication;
import jakarta.inject.Inject;

/**
 * What a library-level caller is allowed to ask Insights for, end to end against the agency
 * table rather than a stub - the mapping from agency code to Host LMS code is the part that
 * was wrong.
 *
 * <p>The case that matters is the multi-code request. A group's Insights page asks for the
 * whole membership at once, and comparing that comma-separated value against the list of codes
 * the caller administers never matched, however many of them they owned.
 */
@DcbTest
class StatsScopeGuardTests {

	@Inject
	private StatsScopeGuard statsScopeGuard;

	@Inject
	private AgencyFixture agencyFixture;

	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeEach
	void beforeEach() {
		agencyFixture.deleteAll();
		hostLmsFixture.deleteAll();

		onboard("AG_A", "LIB_A");
		onboard("AG_B", "LIB_B");
		onboard("AG_C", "LIB_C");
	}

	private void onboard(String agencyCode, String hostLmsCode) {
		agencyFixture.defineAgency(agencyCode, agencyCode,
			hostLmsFixture.createDummyHostLms(hostLmsCode));
	}

	private static Authentication librarian(String... agencyCodes) {
		return new ServerAuthentication("librarian", List.of(RoleNames.LIBRARY_ADMIN),
			Map.of(AgencyClaims.CODE, List.of(agencyCodes)));
	}

	private static Authentication consortiumAdmin() {
		return new ServerAuthentication("admin", List.of(RoleNames.CONSORTIUM_ADMIN), Map.of());
	}

	@Test
	void aLibrarianMayAskForTheWholeSetTheyAdminister() {
		final var scope = singleValueFrom(
			statsScopeGuard.resolve(librarian("AG_A", "AG_B"), "LIB_A,LIB_B"));

		assertThat(scope.libraryCode(), equalTo("LIB_A,LIB_B"));
	}

	@Test
	void aLibrarianMayAskForOneOfTheirOwn() {
		final var scope = singleValueFrom(
			statsScopeGuard.resolve(librarian("AG_A", "AG_B"), "LIB_B"));

		assertThat(scope.libraryCode(), equalTo("LIB_B"));
	}

	@Test
	void aSetContainingOneLibraryTheyDoNotAdministerIsRefusedWhole() {
		// Not narrowed to the two they do own: narrowing silently answers a different
		// question, and the caller cannot tell which libraries the figures cover.
		final var refusal = assertThrows(HttpStatusException.class, () -> singleValueFrom(
			statsScopeGuard.resolve(librarian("AG_A", "AG_B"), "LIB_A,LIB_C")));

		assertThat(refusal.getStatus().getCode(), equalTo(403));
	}

	@Test
	void aLibrarianAskingForNothingGetsTheirOwnSet() {
		final var scope = singleValueFrom(
			statsScopeGuard.resolve(librarian("AG_A", "AG_B"), null));

		assertThat(scope.libraryCode(), equalTo("LIB_A,LIB_B"));
	}

	@Test
	void aTrailingCommaIsNotAnEmptyLicence() {
		// "LIB_A," splits to ["LIB_A", ""], and the empty element is nobody's code.
		assertThat(assertThrows(HttpStatusException.class, () -> singleValueFrom(
				statsScopeGuard.resolve(librarian("AG_A"), "LIB_A,"))),
			instanceOf(HttpStatusException.class));
	}

	@Test
	void aConsortiumAdministratorKeepsWhateverTheyAskedFor() {
		assertThat(singleValueFrom(statsScopeGuard.resolve(consortiumAdmin(), "LIB_A,LIB_C"))
			.libraryCode(), equalTo("LIB_A,LIB_C"));

		assertThat(singleValueFrom(statsScopeGuard.resolve(consortiumAdmin(), null))
			.libraryCode(), nullValue());
	}
}
