package org.olf.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.request.fulfilment.PatronService.PatronId;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;

import jakarta.inject.Inject;

@DcbTest
class PatronServiceTests {
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;

	@Inject
	private PatronService patronService;

	@BeforeEach
	void beforeEach() {
		patronFixture.deleteAllPatrons();
		hostLmsFixture.deleteAll();
	}

	@Test
	@DisplayName("should find existing patron when given a known ID")
	void shouldFindExistingPatronById() {
		// Arrange
		final var existingPatron = patronFixture.savePatron("home-library");

		final var LOCAL_SYSTEM_CODE = "local-system-code";
		final var LOCAL_ID = "local-identity";

		final var homeHostLms = hostLmsFixture.createSierraHostLms(LOCAL_SYSTEM_CODE);

		patronFixture.saveIdentity(existingPatron, homeHostLms, LOCAL_ID, true, "-", LOCAL_SYSTEM_CODE, null);

		// Act
		final var foundPatron = findPatron(new PatronId(existingPatron.getId()));

		// Assert
		assertThat("Expected a patron to be returned, but was null",
			foundPatron, is(notNullValue()));

		assertThat("Should have ID of existing patron",
				foundPatron.getId(), is(existingPatron.getId()));

		assertThat("Should have home library code",
			foundPatron.getHomeLibraryCode(), is("home-library"));

		assertThat("Should include only identity",
			foundPatron.getPatronIdentities(), hasSize(1));

		final var onlyIdentity = foundPatron.getPatronIdentities().get(0);

		assertThat("Should have local ID",
			onlyIdentity.getLocalId(), is("local-identity"));

		assertThat("Should have host LMS",
			onlyIdentity.getHostLms(), is(notNullValue()));

		assertThat("Should have local system code",
			onlyIdentity.getHostLms().getCode(), is("local-system-code"));

		assertThat("Should be home identity",
			onlyIdentity.getHomeIdentity(), is(true));
	}

	@Test
	@DisplayName("should not find patron when given an unknown ID")
	void shouldNotFindPatronWhenPatronDoesNotExist() {
		// Act
		final var result = findPatron(new PatronId(randomUUID()));

		// Assert
		assertThat("Should not return a patron (block converts empty mono to null)",
			result, is(nullValue()));
	}



	private Patron findPatron(PatronId patronId) {
		return singleValueFrom(patronService.findById(patronId));
	}
}
