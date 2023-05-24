package org.olf.reshare.dcb.request.fulfilment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PatronServiceTests {
	private final PatronRepository patronRepository = mock(PatronRepository.class);
	private final PatronIdentityRepository patronIdentityRepository = mock(PatronIdentityRepository.class);
	private final HostLmsService hostLmsService = mock(HostLmsService.class);

	private final PatronService patronService = new PatronService(patronRepository,
		patronIdentityRepository, hostLmsService);

	@Test
	@DisplayName("should find existing patron when given a known identity")
	void shouldFindExistingPatronByIdentity() {
		// Arrange
		final var patronId = randomUUID();

		final var homeHostLmsId = randomUUID();
		final var homeHostLms = createHostLms(homeHostLmsId, "localSystemCode");

		when(hostLmsService.findByCode("localSystemCode"))
			.thenReturn(Mono.just(homeHostLms));

		final var homeIdentityId = randomUUID();
		final var homeIdentity = createIdentity(homeIdentityId, createPatronWithOnlyId(patronId),
			createHostLmsWithIdOnly(homeHostLmsId), "localId", true);

		when(patronIdentityRepository
			.findOneByLocalIdAndHostLmsAndHomeIdentity("localId", homeHostLms, true))
			.thenReturn(Mono.just(homeIdentity));

		// Act
		final var foundPatronId = patronService
			.findPatronFor("localSystemCode", "localId").block();

		assertThat(foundPatronId, is(notNullValue()));
		assertThat(foundPatronId.getValue(), is(patronId));

		verify(hostLmsService).findByCode("localSystemCode");

		verify(patronIdentityRepository)
			.findOneByLocalIdAndHostLmsAndHomeIdentity("localId", homeHostLms, true);

		verifyNoMoreInteractions(hostLmsService, patronIdentityRepository, patronRepository);
	}

	@Test
	@DisplayName("should not find patron when given an unknown identity")
	void shouldNotFindPatronWhenPatronIdentityDoesNotExist() {
		// Arrange
		final var hostLms = createHostLms("localSystemCode");

		when(hostLmsService.findByCode("localSystemCode"))
			.thenReturn(Mono.just(hostLms));

		when(patronIdentityRepository
			.findOneByLocalIdAndHostLmsAndHomeIdentity("localId", hostLms, true))
			.thenReturn(Mono.empty());

		// Act
		final var result = patronService
			.findPatronFor("localSystemCode", "localId")
			.block();

		// Assert
		assertThat("Should not return a patron (block converts empty mono to null)",
			result, is(nullValue()));

		verify(hostLmsService).findByCode("localSystemCode");

		verify(patronIdentityRepository)
			.findOneByLocalIdAndHostLmsAndHomeIdentity("localId", hostLms, true);

		verifyNoMoreInteractions(hostLmsService, patronIdentityRepository, patronRepository);
	}

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

	@Test
	@DisplayName("should save newly created patron")
	void shouldSaveCreatedPatron() {
		// Arrange

		// Mock expectations cannot be specific
		// as the parameters are instantiated within the service
		when(patronRepository.save(any()))
			.thenAnswer(withFirstArgument());

		when(patronIdentityRepository.save(any()))
			.thenAnswer(withFirstArgument());

		final var hostLms = createHostLms("localSystemCode");

		when(hostLmsService.findByCode("localSystemCode"))
			.thenAnswer(invocation -> Mono.just(hostLms));

		// Act
		final var createdPatronId = patronService
			.createPatron("localSystemCode", "localId", "home-library-code")
			.block();

		assertThat("Returned patron ID should not be null",
			createdPatronId, is(notNullValue()));

		final var patronCaptor = ArgumentCaptor.forClass(Patron.class);

		verify(patronRepository).save(patronCaptor.capture());

		final var savedPatron = patronCaptor.getValue();

		assertThat("Expected a patron to be saved", savedPatron, is(notNullValue()));

		assertThat("Should have an ID", savedPatron.getId(), is(notNullValue()));

		assertThat("Should have a home library code",
			savedPatron.getHomeLibraryCode(), is("home-library-code"));

		assertThat("Should not include any identities",
			savedPatron.getPatronIdentities(), hasSize(0));

		assertThat("Returned patron ID should have same ID as saved patron",
			createdPatronId.getValue(), is(savedPatron.getId()));

		final var identityCaptor = ArgumentCaptor.forClass(PatronIdentity.class);

		verify(patronIdentityRepository).save(identityCaptor.capture());

		final var savedIdentity = identityCaptor.getValue();

		assertThat("Patron associated with an identity should not be null",
			savedIdentity.getPatron(), is(notNullValue()));

		assertThat("Patron associated with an identity must be shallow, to avoid a circular loop",
			savedIdentity.getPatron(), is(not(savedPatron)));

		assertThat("Shallow patron associated with an identity should not have any identities",
			savedIdentity.getPatron().getPatronIdentities(), is(nullValue()));

		assertThat("Should have local ID",
			savedIdentity.getLocalId(), is("localId"));

		assertThat("Should have local system code",
			savedIdentity.getHostLms().getCode(), is("localSystemCode"));

		assertThat("Should be home identity",
			savedIdentity.getHomeIdentity(), is(true));

		verify(hostLmsService).findByCode("localSystemCode");

		verifyNoMoreInteractions(hostLmsService, patronIdentityRepository, patronRepository);
	}

	private Patron createPatron(UUID id, String homeLibraryCode) {
		return new Patron(id, null, null, homeLibraryCode, List.of());
	}

	private Patron createPatronWithOnlyId(UUID id) {
		return Patron.builder()
			.id(id)
			.build();
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

	private static Answer<Object> withFirstArgument() {
		return invocation -> Mono.just(invocation.getArgument(0));
	}
}
