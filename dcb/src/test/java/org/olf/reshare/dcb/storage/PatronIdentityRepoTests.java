package org.olf.reshare.dcb.storage;

import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.core.model.Patron;
import org.olf.reshare.dcb.core.model.PatronIdentity;
import org.olf.reshare.dcb.test.DcbTest;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

@DcbTest
public class PatronIdentityRepoTests {

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
		final var localId = "localId";
		final var patron = new Patron(patronId, null, null, List.of());
		final var patronIdentity = new PatronIdentity(patronIdentityId, null, null, patron, dataHostLms, localId, true);

		Mono.from(hostLmsRepository.save(dataHostLms)).block();
		Mono.from(patronRepository.save(patron)).block();
		Mono.from(patronIdentityRepository.save(patronIdentity)).block();

		// Act
		final var foundPatronIdentity = Mono.from(patronIdentityRepository.findOneByLocalIdAndHostLmsAndHomeIdentity(localId, dataHostLms, true)).block();

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
		final var localId = "localId";
		final var patron = new Patron(patronId, null, null, List.of());
		final var patronIdentity = new PatronIdentity(patronIdentityId, null, null, patron, dataHostLms, localId, false);

		Mono.from(hostLmsRepository.save(dataHostLms)).block();
		Mono.from(patronRepository.save(patron)).block();
		Mono.from(patronIdentityRepository.save(patronIdentity)).block();

		// Act
		final var foundPatronIdentity = Mono.from(patronIdentityRepository.findOneByLocalIdAndHostLmsAndHomeIdentity(localId, dataHostLms, true)).block();

		// Assert
		assertThat("Expected no patron identity to be found for the given criteria", foundPatronIdentity, is(nullValue()));
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
		final var localId = "localId";
		final var patron = new Patron(patronId, null, null, List.of());
		final var patronIdentity = new PatronIdentity(patronIdentityId, null, null, patron, dataHostLms, localId, true);

		Mono.from(hostLmsRepository.save(dataHostLms)).block();
		Mono.from(patronRepository.save(patron)).block();
		Mono.from(patronIdentityRepository.save(patronIdentity)).block();

		// Act
		final var foundPatronIdentity = Mono.from(patronIdentityRepository.findOneByLocalIdAndHostLmsAndHomeIdentity("NotLocalId", dataHostLms, true)).block();

		// Assert
		assertThat("Expected no patron identity to be found for the given criteria", foundPatronIdentity, is(nullValue()));
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
		final var patron = new Patron(patronId, null, null, List.of());
		final var patronIdentity = new PatronIdentity(patronIdentityId, null, null, patron, dataHostLms1, localId, true);

		Mono.from(hostLmsRepository.save(dataHostLms1)).block();
		Mono.from(patronRepository.save(patron)).block();
		Mono.from(patronIdentityRepository.save(patronIdentity)).block();

		// Act
		final var foundPatronIdentity = Mono.from(patronIdentityRepository.findOneByLocalIdAndHostLmsAndHomeIdentity(localId, dataHostLms2, true)).block();

		// Assert
		assertThat("Expected no patron identity to be found for the given criteria", foundPatronIdentity, is(nullValue()));
	}
}
