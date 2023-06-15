package org.olf.reshare.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.core.model.Patron;
import org.olf.reshare.dcb.core.model.PatronIdentity;
import org.olf.reshare.dcb.request.fulfilment.PatronService.PatronId;
import org.olf.reshare.dcb.storage.PatronIdentityRepository;
import org.olf.reshare.dcb.storage.PatronRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class PatronServiceTests {
	private final PatronRepository patronRepository = mock(PatronRepository.class);
	private final PatronIdentityRepository patronIdentityRepository = mock(PatronIdentityRepository.class);
	private final HostLmsService hostLmsService = mock(HostLmsService.class);

	private final PatronService patronService = new PatronService(patronRepository,
		patronIdentityRepository, hostLmsService);

	@Test
	@DisplayName("should find existing patron when given a known ID")
	void shouldFindExistingPatronById() {
		// Arrange
		final var patronId = randomUUID();
		final var patronIdentityId = randomUUID();
		final var patron = createPatron(patronId, "home-library-code");

		// Patron identity repository does not fetch host LMS as well
		final var hostLmsWithOnlyId = createHostLmsWithIdOnly(randomUUID());

		final var patronIdentity = createIdentity(patronIdentityId, patron,
			hostLmsWithOnlyId, "localId", true);

		when(patronRepository.findById(any()))
			.thenAnswer(invocation -> Mono.just(patron));

		when(patronIdentityRepository.findAllByPatron(any()))
			.thenAnswer(invocation -> Flux.fromIterable(List.of(patronIdentity)));

		final var foundHostLms = createHostLms("localSystemCode");

		when(hostLmsService.findById(any()))
			.thenAnswer(invocation -> Mono.just(foundHostLms));

		// Act
		final var foundPatron = patronService.findById(new PatronId(patronId)).block();

		// Assert
		assertThat("Expected a patron to be returned, but was null",
			foundPatron, is(notNullValue()));

		assertThat("Unexpected patron ID", foundPatron.getId(), is(patronId));
		assertThat("Unexpected home library code",
			foundPatron.getHomeLibraryCode(), is("home-library-code"));

		assertThat("Should include only identity",
			foundPatron.getPatronIdentities(), hasSize(1));

		final var onlyIdentity = foundPatron.getPatronIdentities().get(0);

		assertThat("Should have local ID",
			onlyIdentity.getLocalId(), is("localId"));

		assertThat("Should have local system code",
			onlyIdentity.getHostLms().getCode(), is("localSystemCode"));

		assertThat("Should be home identity",
			onlyIdentity.getHomeIdentity(), is(true));

		verify(patronRepository).findById(any());
		verify(patronIdentityRepository).findAllByPatron(any());
		verify(hostLmsService).findById(any());

		verifyNoMoreInteractions(hostLmsService, patronIdentityRepository, patronRepository);
	}

	@Test
	@DisplayName("should not find patron when given an unknown ID")
	void shouldNotFindPatronWhenPatronDoesNotExist() {
		// Arrange
		when(patronRepository.findById(any()))
			.thenAnswer(invocation -> Mono.empty());

		// Act
		final var result = patronService.findById(new PatronId(randomUUID())).block();

		// Assert
		assertThat("Should not return a patron (block converts empty mono to null)",
			result, is(nullValue()));

		verify(patronRepository).findById(any());

		verifyNoMoreInteractions(hostLmsService, patronIdentityRepository, patronRepository);
	}

	private Patron createPatron(UUID id, String homeLibraryCode) {
		return new Patron(id, null, null, homeLibraryCode, List.of());
	}

	private DataHostLms createHostLms(UUID id, String localSystemCode) {
		return new DataHostLms(id, localSystemCode, "Local System",
			SierraLmsClient.class.getName(), Map.of());
	}

	private DataHostLms createHostLms(String localSystemCode) {
		return createHostLms(randomUUID(), localSystemCode);
	}

	private DataHostLms createHostLmsWithIdOnly(UUID id) {
		return new DataHostLms(id, "", "", "", Map.of());
	}

	private static PatronIdentity createIdentity(UUID id,
		Patron patron, DataHostLms hostLms, String localId, boolean homeIdentity) {

		return PatronIdentity.builder()
			.id(id)
			.patron(patron)
			.hostLms(hostLms)
			.localId(localId)
			.homeIdentity(homeIdentity)
			.build();
	}
}
