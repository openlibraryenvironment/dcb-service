package org.olf.dcb.storage;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.storage.HostLmsRepository;
import org.olf.dcb.storage.PatronIdentityRepository;
import org.olf.dcb.storage.PatronRepository;
import org.olf.dcb.test.DcbTest;

import jakarta.inject.Inject;

@DcbTest
class PatronIdentityRepoTests {
	@Inject
	private PatronRepository patronRepository;
	@Inject
	private PatronIdentityRepository patronIdentityRepository;
	@Inject
	private HostLmsRepository hostLmsRepository;

	@Test
	@DisplayName("Should find patron identity by local ID, host LMS, and home identity")
	void shouldFindPatronIdentityByLocalIdAndHostLmsAndHomeIdentity() {
		// Arrange
		final var dataHostLmsId = randomUUID();
		final var dataHostLms = new DataHostLms(dataHostLmsId, "database-host",
			"Database Host", SierraLmsClient.class.getName(), Map.of());
		final var patronId = randomUUID();
		final var patronIdentityId = randomUUID();
		final var patron = new Patron(patronId, null, null, null, List.of());
		final var patronIdentity = createPatronIdentity(patronIdentityId, patron, dataHostLms, true);

		singleValueFrom(hostLmsRepository.save(dataHostLms));
		singleValueFrom(patronRepository.save(patron));
		singleValueFrom(patronIdentityRepository.save(patronIdentity));

		// Act
		final var foundPatronIdentity = findIdentity(dataHostLms, "localId");

		// Assert
		assertThat("PatronIdentity should not be null.", foundPatronIdentity, is(notNullValue()));
		assertThat("Local ID should match", foundPatronIdentity.getLocalId(), is("localId"));
		assertThat("Host LMS ID should match", foundPatronIdentity.getHostLms().getId(), is(dataHostLmsId));
		assertThat("Home Identity should be true", foundPatronIdentity.getHomeIdentity(), is(true));
	}

	@Test
	@DisplayName("Should not find match when home identity is false")
	void shouldNotFindMatchWhenHomeIdentityFalse() {
		// Arrange
		final var dataHostLmsId = randomUUID();
		final var dataHostLms = new DataHostLms(dataHostLmsId, "database-host",
			"Database Host", SierraLmsClient.class.getName(), Map.of());
		final var patronId = randomUUID();
		final var patronIdentityId = randomUUID();
		final var patron = new Patron(patronId, null, null, null, List.of());
		final var patronIdentity = createPatronIdentity(patronIdentityId, patron, dataHostLms, false);

		singleValueFrom(hostLmsRepository.save(dataHostLms));
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
		final var dataHostLmsId = randomUUID();
		final var dataHostLms = new DataHostLms(dataHostLmsId, "database-host",
			"Database Host", SierraLmsClient.class.getName(), Map.of());
		final var patronId = randomUUID();
		final var patronIdentityId = randomUUID();
		final var patron = new Patron(patronId, null, null, null, List.of());
		final var patronIdentity = createPatronIdentity(patronIdentityId, patron, dataHostLms, true);

		singleValueFrom(hostLmsRepository.save(dataHostLms));
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
		final var dataHostLmsId1 = randomUUID();
		final var dataHostLms1 = new DataHostLms(dataHostLmsId1, "database-host",
			"Database Host", SierraLmsClient.class.getName(), Map.of());
		final var dataHostLmsId2 = randomUUID();
		final var dataHostLms2 = new DataHostLms(dataHostLmsId2, "database-host",
			"Database Host", SierraLmsClient.class.getName(), Map.of());
		final var patronId = randomUUID();
		final var patronIdentityId = randomUUID();
		final var localId = "localId";
		final var patron = new Patron(patronId, null, null, null, List.of());
		final var patronIdentity = createPatronIdentity(patronIdentityId, patron, dataHostLms1, true);

		singleValueFrom(hostLmsRepository.save(dataHostLms1));
		singleValueFrom(patronRepository.save(patron));
		singleValueFrom(patronIdentityRepository.save(patronIdentity));

		// Act
		final var foundPatronIdentity = findIdentity(dataHostLms2, localId);

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
