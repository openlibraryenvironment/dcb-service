package org.olf.reshare.dcb.request.fulfilment;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.olf.reshare.dcb.storage.PatronIdentityRepository;
import org.olf.reshare.dcb.storage.PatronRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class PatronServiceTests {
	@Test
	@DisplayName("should find existing patron when given a known identity")
	void shouldFindExistingPatronByIdentity() {
		// Arrange
		final var patronRepository = mock(PatronRepository.class);
		final var patronIdentityRepository = mock(PatronIdentityRepository.class);
		final var hostLmsService = mock(HostLmsService.class);

		final var patronService = new PatronService(patronRepository,
			patronIdentityRepository, hostLmsService);

		final var patronId = randomUUID();
		final var patron = createPatron(patronId, "home-library-code");

		final var homeHostLmsId = randomUUID();
		final var homeHostLms = createHostLms(homeHostLmsId, "localSystemCode");

		when(hostLmsService.findByCode(any()))
			.thenAnswer(invocation -> Mono.just(homeHostLms));

		// Host LMS returned with identity from repository only contains an ID
		final var homeIdentityId = randomUUID();
		final var homeIdentity = createIdentity(homeIdentityId, patron,
			createHostLmsWithIdOnly(homeHostLmsId), "localId", true);

		when(patronIdentityRepository.findOneByLocalIdAndHostLmsAndHomeIdentity(any(), any(), any()))
			.thenAnswer(invocation -> Mono.just(homeIdentity));

		when(patronRepository.findById(any()))
			.thenAnswer(invocation -> Mono.just(patron));

		final var additionalIdentityId = randomUUID();
		final var otherHostLmsId = randomUUID();

		final var additionalIdentity = createIdentity(additionalIdentityId, patron,
			createHostLmsWithIdOnly(otherHostLmsId), "additionalLocalId", false);

		when(patronIdentityRepository.findAllByPatron(any()))
			.thenAnswer(invocation ->
				Flux.fromIterable(List.of(homeIdentity, additionalIdentity)));

		when(hostLmsService.findById(eq(homeHostLmsId)))
			.thenAnswer(invocation -> Mono.just(homeHostLms));

		final var otherHostLms = createHostLms(homeHostLmsId, "otherLocalSystemCode");

		when(hostLmsService.findById(eq(otherHostLmsId)))
			.thenAnswer(invocation -> Mono.just(otherHostLms));

		// Act
		final var foundPatron = patronService
			.findPatronFor("localSystemCode", "localId").block();

		// Assert
		assertThat("Expected a patron to be returned, but was null",
			foundPatron, is(notNullValue()));

		assertThat("Unexpected patron ID", foundPatron.getId(), is(patronId));
		assertThat("Unexpected home library code",
			foundPatron.getHomeLibraryCode(), is("home-library-code"));

		assertThat("Should include only identity",
			foundPatron.getPatronIdentities(), hasSize(2));

		final var foundHomeIdentity = foundPatron.getPatronIdentities().get(0);

		assertThat("Should have local ID",
			foundHomeIdentity.getLocalId(), is("localId"));

		assertThat("Should have local system code",
			foundHomeIdentity.getHostLms().getCode(), is("localSystemCode"));

		assertThat("Should be home identity",
			foundHomeIdentity.getHomeIdentity(), is(true));

		final var foundAdditionalIdentity = foundPatron.getPatronIdentities().get(1);

		assertThat("Should have local ID",
			foundAdditionalIdentity.getLocalId(), is("additionalLocalId"));

		assertThat("Should have local system code",
			foundAdditionalIdentity.getHostLms().getCode(), is("otherLocalSystemCode"));

		assertThat("Should not be home identity",
			foundAdditionalIdentity.getHomeIdentity(), is(false));

		verify(hostLmsService).findByCode(any());

		verify(patronIdentityRepository)
			.findOneByLocalIdAndHostLmsAndHomeIdentity(any(), any(), any());

		verify(patronRepository).findById(any());

		verify(patronIdentityRepository).findAllByPatron(any());

		verify(hostLmsService).findById(eq(homeHostLmsId));
		verify(hostLmsService).findById(eq(otherHostLmsId));

		verifyNoMoreInteractions(hostLmsService, patronIdentityRepository, patronRepository);
	}

	@Test
	@DisplayName("should not find patron when given an unknown identity")
	void shouldNotFindPatronWhenPatronIdentityDoesNotExist() {
		// Arrange
		final var patronRepository = mock(PatronRepository.class);
		final var patronIdentityRepository = mock(PatronIdentityRepository.class);
		final var hostLmsService = mock(HostLmsService.class);

		final var patronService = new PatronService(patronRepository,
			patronIdentityRepository, hostLmsService);

		final var dataHostLms = createHostLms("localSystemCode");

		when(hostLmsService.findByCode(any()))
			.thenAnswer(invocation -> Mono.just(dataHostLms));

		when(patronIdentityRepository
			.findOneByLocalIdAndHostLmsAndHomeIdentity(any(), any(), any()))
				.thenAnswer(invocation -> Mono.empty());

		// Act
		final var result = patronService
			.findPatronFor("localSystemCode", "localId").block();

		// Assert
		assertThat("Should not return a patron (block converts empty mono to null)",
			result, is(nullValue()));

		verify(hostLmsService).findByCode(any());

		verify(patronIdentityRepository)
			.findOneByLocalIdAndHostLmsAndHomeIdentity(any(), any(), any());

		verifyNoMoreInteractions(hostLmsService, patronIdentityRepository, patronRepository);
	}

	@Test
	@DisplayName("should find existing patron when given a known ID")
	void shouldFindExistingPatronById() {
		// Arrange
		final var patronRepository = mock(PatronRepository.class);
		final var patronIdentityRepository = mock(PatronIdentityRepository.class);
		final var hostLmsService = mock(HostLmsService.class);

		final var patronService = new PatronService(patronRepository,
			patronIdentityRepository, hostLmsService);

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
		final var foundPatron = patronService.findById(patronId).block();

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
		final var patronRepository = mock(PatronRepository.class);
		final var patronIdentityRepository = mock(PatronIdentityRepository.class);
		final var hostLmsService = mock(HostLmsService.class);

		final var patronService = new PatronService(patronRepository,
			patronIdentityRepository, hostLmsService);

		when(patronRepository.findById(any()))
			.thenAnswer(invocation -> Mono.empty());

		// Act
		final var result = patronService.findById(randomUUID()).block();

		// Assert
		assertThat("Should not return a patron (block converts empty mono to null)",
			result, is(nullValue()));

		verify(patronRepository).findById(any());

		verifyNoMoreInteractions(hostLmsService, patronIdentityRepository, patronRepository);
	}

	@Test
	@DisplayName("should save newly created patron")
	void shouldSaveCreatedPatron() {
		// Arrange
		final var patronRepository = mock(PatronRepository.class);
		final var patronIdentityRepository = mock(PatronIdentityRepository.class);
		final var hostLmsService = mock(HostLmsService.class);

		final var patronService = new PatronService(patronRepository,
			patronIdentityRepository, hostLmsService);

		final var dataHostLms = createHostLms("localSystemCode");

		// Return the same patron as gets passed in
		when(patronRepository.save(any()))
			.thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

		when(hostLmsService.findByCode(any()))
			.thenAnswer(invocation -> Mono.just(dataHostLms));

		// Return the same patron as gets passed in
		when(patronIdentityRepository.save(any()))
			.thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

		// Act
		final var createdPatron = patronService
			.createPatron("localSystemCode", "localId", "home-library-code")
			.block();

		// Assert
		assertThat("Expected a patron to be returned, but was null",
			createdPatron, is(notNullValue()));

		assertThat("Should have a home library code",
			createdPatron.getHomeLibraryCode(), is("home-library-code"));

		assertThat("Should have an ID", createdPatron.getId(), is(notNullValue()));
		assertThat("Should include only identity",
			createdPatron.getPatronIdentities(), hasSize(1));

		final var onlyIdentity = createdPatron.getPatronIdentities().get(0);

		assertThat("Patron associated with an identity should not be null",
			onlyIdentity.getPatron(), is(notNullValue()));

		assertThat("Patron associated with an identity must be shallow, to avoid a circular loop",
			onlyIdentity.getPatron(), is(not(createdPatron)));

		assertThat("Shallow patron associated with an identity should not have any identities",
			onlyIdentity.getPatron().getPatronIdentities(), is(nullValue()));

		assertThat("Should have local ID",
			onlyIdentity.getLocalId(), is("localId"));

		assertThat("Should have local system code",
			onlyIdentity.getHostLms().getCode(), is("localSystemCode"));

		assertThat("Should be home identity",
			onlyIdentity.getHomeIdentity(), is(true));
	}

	private Patron createPatron(UUID id, String homeLibraryCode) {
		return new Patron(id, null, null, homeLibraryCode, List.of());
	}

	private Patron createPatronWithOnlyID(UUID id) {
		return createPatron(id, null);
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

		return new PatronIdentity(id, null, null,
			patron, hostLms, localId, homeIdentity);
	}
}
