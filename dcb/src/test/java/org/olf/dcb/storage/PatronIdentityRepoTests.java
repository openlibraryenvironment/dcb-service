package org.olf.dcb.storage;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;

@DcbTest
class PatronIdentityRepoTests {
	@Inject
	private PatronRepository patronRepository;
	@Inject
	private PatronIdentityRepository patronIdentityRepository;

	@Inject
	private HostLmsFixture hostLmsFixture;

	@BeforeEach
	void beforeEach() {
		hostLmsFixture.deleteAll();
	}

	@Test
	@DisplayName("Should find patron identity by local ID, host LMS, and home identity")
	void shouldFindPatronIdentityByLocalIdAndHostLmsAndHomeIdentity() {
		// Arrange
		final var hostLms = hostLmsFixture.createSierraHostLms("some-host-lms");

		final var patronId = randomUUID();
		final var patronIdentityId = randomUUID();
		final var patron = new Patron(patronId, null, null, null, List.of());
		final var patronIdentity = createPatronIdentity(patronIdentityId, patron, hostLms, true);

		singleValueFrom(patronRepository.save(patron));
		singleValueFrom(patronIdentityRepository.save(patronIdentity));

		// Act
		final var foundPatronIdentity = findIdentity(hostLms, "localId");

		// Assert
		assertThat("PatronIdentity should not be null.", foundPatronIdentity, is(notNullValue()));
		assertThat("Local ID should match", foundPatronIdentity.getLocalId(), is("localId"));
		assertThat("Host LMS ID should match", foundPatronIdentity.getHostLms().getId(), is(hostLms.getId()));
		assertThat("Home Identity should be true", foundPatronIdentity.getHomeIdentity(), is(true));
	}

	@Test
	@DisplayName("Should not find match when home identity is false")
	void shouldNotFindMatchWhenHomeIdentityFalse() {
		// Arrange
		final var dataHostLms = hostLmsFixture.createSierraHostLms("some-host-lms");

		final var patronId = randomUUID();
		final var patronIdentityId = randomUUID();
		final var patron = new Patron(patronId, null, null, null, List.of());
		final var patronIdentity = createPatronIdentity(patronIdentityId, patron, dataHostLms, false);

		singleValueFrom(patronRepository.save(patron));
		singleValueFrom(patronIdentityRepository.save(patronIdentity));

		// Act
		final var foundPatronIdentity = findIdentity(dataHostLms, "localId");

		// Assert
		assertThat("Expected no patron identity to be found for the given criteria",
			foundPatronIdentity, is(nullValue()));
	}

	@Test
	@DisplayName("Should not find match when local ID is incorrect")
	void shouldNotFindMatchWhenLocalIdIncorrect() {
		// Arrange
		final var dataHostLms = hostLmsFixture.createSierraHostLms("some-host-lms");

		final var patronId = randomUUID();
		final var patronIdentityId = randomUUID();
		final var patron = new Patron(patronId, null, null, null, List.of());
		final var patronIdentity = createPatronIdentity(patronIdentityId, patron, dataHostLms, true);

		singleValueFrom(patronRepository.save(patron));
		singleValueFrom(patronIdentityRepository.save(patronIdentity));

		// Act
		final var foundPatronIdentity = findIdentity(dataHostLms, "NotLocalId");

		// Assert
		assertThat("Expected no patron identity to be found for the given criteria",
			foundPatronIdentity, is(nullValue()));
	}

	@Test
	@DisplayName("Should not find match when host LMS is incorrect")
	void shouldNotFindMatchWhenHostLmsIncorrect() {
		// Arrange
		final var firstHostLms = hostLmsFixture.createSierraHostLms("first-host-lms");
		final var secondHostLms = hostLmsFixture.createSierraHostLms("second-host-lms");

		final var patronId = randomUUID();
		final var patronIdentityId = randomUUID();
		final var localId = "localId";
		final var patron = new Patron(patronId, null, null, null, List.of());
		final var patronIdentity = createPatronIdentity(patronIdentityId, patron, firstHostLms, true);

		singleValueFrom(patronRepository.save(patron));
		singleValueFrom(patronIdentityRepository.save(patronIdentity));

		// Act
		final var foundPatronIdentity = findIdentity(secondHostLms, localId);

		// Assert
		assertThat("Expected no patron identity to be found for the given criteria",
			foundPatronIdentity, is(nullValue()));
	}

	private PatronIdentity findIdentity(DataHostLms hostLms, String localId) {
		return singleValueFrom(patronIdentityRepository
			.findOneByLocalIdAndHostLmsAndHomeIdentity(localId, hostLms, true));
	}

	private static PatronIdentity createPatronIdentity(UUID patronIdentityId,
		Patron patron, DataHostLms hostLms, boolean homeIdentity) {

		return PatronIdentity.builder()
			.id(patronIdentityId)
			.patron(patron)
			.hostLms(hostLms)
			.localId("localId")
			.homeIdentity(homeIdentity)
			.build();
	}
}
