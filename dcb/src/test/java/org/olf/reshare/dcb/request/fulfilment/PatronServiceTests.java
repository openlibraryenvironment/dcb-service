package org.olf.reshare.dcb.request.fulfilment;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.core.model.Patron;
import org.olf.reshare.dcb.core.model.PatronIdentity;
import org.olf.reshare.dcb.request.fulfilment.PlacePatronRequestCommand.Citation;
import org.olf.reshare.dcb.request.fulfilment.PlacePatronRequestCommand.PickupLocation;
import org.olf.reshare.dcb.request.fulfilment.PlacePatronRequestCommand.Requestor;
import org.olf.reshare.dcb.storage.PatronIdentityRepository;
import org.olf.reshare.dcb.storage.PatronRepository;

import reactor.core.publisher.Mono;

public class PatronServiceTests {

	@Test
	@DisplayName("should return a Patron when given an existing patron identity")
	void shouldReturnPatronWhenPatronIdentityAlreadyExists() {
		// Arrange
		final var patronRepository = mock(PatronRepository.class);
		final var patronIdentityRepository = mock(PatronIdentityRepository.class);
		final var hostLmsService = mock(HostLmsService.class);

		final var patronService = new PatronService(patronRepository,
			patronIdentityRepository, hostLmsService);

		final var localSystemCode = "localSystemCode";
		final var localId = "localId";
		final var dataHostLms = new DataHostLms();
		final var command = new PlacePatronRequestCommand(
			new Citation(UUID.randomUUID()), new PickupLocation("code"),
			new Requestor(localId, localSystemCode));

		final var patronId = randomUUID();
		final var patronIdentityId = randomUUID();
		final var patron = new Patron(patronId, null, null, List.of());
		final var patronIdentity = new PatronIdentity(patronIdentityId, null, null, patron, dataHostLms, localId, true);

		when(hostLmsService.findByCode(any()))
			.thenAnswer(invocation -> Mono.just(dataHostLms));

		when(patronIdentityRepository.findOneByLocalIdAndHostLmsAndHomeIdentity(any(), any(), any()))
			.thenAnswer(invocation -> Mono.just(patronIdentity));

		when(patronRepository.findById(any()))
			.thenAnswer(invocation -> Mono.just(patron));

		// Act
		final var foundPatron = patronService.getOrCreatePatronForRequestor(command).block();

		// Assert
		assertAll("Patron validation",
			() -> assertThat("Expected a patron to be returned, but was null.", foundPatron, is(notNullValue())),
			() -> assertThat("Unexpected patron ID.", foundPatron.getId(), is(patronId)),
			() -> assertThat("Unexpected list of Patron Identities.", foundPatron.getPatronIdentities(), is(emptyList()))
		);

		verify(patronRepository, times(0)).save(any());
	}

	@Test
	@DisplayName("should return empty mono value when no patron is found")
	void shouldReturnEmptyMonoWhenPatronIdentityDoesNotExists() {
		// Arrange
		final var patronRepository = mock(PatronRepository.class);
		final var patronIdentityRepository = mock(PatronIdentityRepository.class);
		final var hostLmsService = mock(HostLmsService.class);

		final var patronService = new PatronService(patronRepository,
			patronIdentityRepository, hostLmsService);

		final var localSystemCode = "localSystemCode";
		final var localId = "localId";
		final var dataHostLms = new DataHostLms();
		final var requestor = new Requestor(localId, localSystemCode);

		when(hostLmsService.findByCode(any()))
			.thenAnswer(invocation -> Mono.just(dataHostLms));

		when(patronIdentityRepository.findOneByLocalIdAndHostLmsAndHomeIdentity(any(), any(), any()))
			.thenAnswer(invocation -> Mono.empty());

		// Act
		final var result = patronService.findPatronFor(requestor).block();

		// Assert

		// Due to the use of '.block()' the return value is changed to a null value from an empty mono
		assertThat("Null value expected.", result, is(nullValue()));
		verify(patronIdentityRepository).findOneByLocalIdAndHostLmsAndHomeIdentity(any(), any(), any());
		verifyNoMoreInteractions(patronIdentityRepository);
		verifyNoInteractions(patronRepository);
	}

	@Test
	@DisplayName("should return a new Patron when given a requestor")
	void shouldReturnNewPatronWhenPatronIdentityDoesNotExists() {
		// Arrange
		final var patronRepository = mock(PatronRepository.class);
		final var patronIdentityRepository = mock(PatronIdentityRepository.class);
		final var hostLmsService = mock(HostLmsService.class);

		final var patronService = new PatronService(patronRepository,
			patronIdentityRepository, hostLmsService);

		final var localSystemCode = "localSystemCode";
		final var localId = "localId";
		final var dataHostLms = new DataHostLms();

		final var command = new PlacePatronRequestCommand(
			new Citation(UUID.randomUUID()), new PickupLocation("code"),
			new Requestor(localId, localSystemCode));

		final var patronId = randomUUID();
		final var patronIdentityId = randomUUID();
		final var patron = new Patron(patronId, null, null, List.of());
		final var patronIdentity = new PatronIdentity(patronIdentityId, null, null, patron, dataHostLms, localId, true);

		when(patronRepository.save(any()))
			.thenAnswer(invocation -> Mono.just(patron));

		when(hostLmsService.findByCode(any()))
			.thenAnswer(invocation -> Mono.just(dataHostLms));

		when(patronIdentityRepository.save(any()))
			.thenAnswer(invocation -> Mono.just(patronIdentity));

		// Act
		final var foundPatron = patronService.getOrCreatePatronForRequestor(command).block();

		// Assert
		assertAll("Patron validation",
			() -> assertThat("Expected a patron to be returned, but was null.", foundPatron, is(notNullValue())),
			() -> assertThat("Unexpected patron ID.", foundPatron.getId(), is(patronId)),
			() -> assertThat("Unexpected list of Patron Identities.", foundPatron.getPatronIdentities(), is(emptyList()))
		);
	}
}
