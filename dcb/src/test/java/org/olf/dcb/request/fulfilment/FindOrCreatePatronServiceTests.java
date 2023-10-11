package org.olf.dcb.request.fulfilment;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

@DcbTest
class FindOrCreatePatronServiceTests {
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;

	@Inject
	private FindOrCreatePatronService findOrCreatePatronService;

	@BeforeEach
	void beforeEach() {
		patronFixture.deleteAllPatrons();
		hostLmsFixture.deleteAll();
	}

	@Test
	@DisplayName("should create new patron when home identity cannot be found")
	void shouldCreateNewPatronWhenHomeIdentityCannotBeFound() {
		// Arrange
		final var LOCAL_SYSTEM_CODE = "local-system-code";
		final var LOCAL_ID = "local-identity";

		final var hostLmsId = randomUUID();

		hostLmsFixture.createHostLms(hostLmsId, LOCAL_SYSTEM_CODE);

		// Act
		findOrCreatePatronService
			.findOrCreatePatron(LOCAL_SYSTEM_CODE, LOCAL_ID, "home-library")
			.block();

		// Assert
		final var foundPatron = patronFixture.findPatron(LOCAL_SYSTEM_CODE, LOCAL_ID);

		assertThat("Should find newly created patron",
			foundPatron, is(notNullValue()));

		assertThat("Should have expected home library code",
			foundPatron.getHomeLibraryCode(), is("home-library"));

		final var identities  = patronFixture.findIdentities(foundPatron);

		assertThat("Should have one identity", identities, hasSize(1));

		final var onlyIdentity = identities.get(0);

		assertThat("Identity should have host LMS",
			onlyIdentity.getHostLms(), is(notNullValue()));

		assertThat("Identity should be for intended host LMS",
			onlyIdentity.getHostLms().getId(), is(hostLmsId));

		assertThat("Identity should have expected local ID",
			onlyIdentity.getLocalId(), is(LOCAL_ID));

		assertThat("Identity should be the home identity",
			onlyIdentity.getHomeIdentity(), is(true));
	}

	@Test
	@DisplayName("should find existing patron when home identity can be found")
	void shouldFindExistingPatronWhenHomeIdentityCanBeFound() {
		// Arrange
		final var existingPatron = patronFixture.savePatron("home-library");

		final var hostLmsId = randomUUID();

		final var LOCAL_SYSTEM_CODE = "local-system-code";
		final var LOCAL_ID = "local-identity";

		final var homeHostLms = hostLmsFixture.createHostLms(hostLmsId, LOCAL_SYSTEM_CODE);

		patronFixture.saveIdentity(existingPatron, homeHostLms, LOCAL_ID, true, "-", "local-system-code", null);

		// Act
		findOrCreatePatronService
			.findOrCreatePatron(LOCAL_SYSTEM_CODE, LOCAL_ID, "different-library")
			.block();

		// Assert
		final var allPatrons = patronFixture.findAll();

		assertThat("Should only be one patron", allPatrons, hasSize(1));

		final var foundPatron = allPatrons.get(0);

		assertThat("Should find existing patron",
			foundPatron, is(notNullValue()));

		assertThat("Should not update existing home library code",
			foundPatron.getHomeLibraryCode(), is("home-library"));

		final var identities  = patronFixture.findIdentities(foundPatron);

		assertThat("Should have one identity", identities, hasSize(1));

		final var onlyIdentity = identities.get(0);

		assertThat("Identity should have host LMS",
			onlyIdentity.getHostLms(), is(notNullValue()));

		assertThat("Identity should be for intended host LMS",
			onlyIdentity.getHostLms().getId(), is(hostLmsId));

		assertThat("Identity should have expected local ID",
			onlyIdentity.getLocalId(), is(LOCAL_ID));

		assertThat("Identity should be the home identity",
			onlyIdentity.getHomeIdentity(), is(true));
	}
}
