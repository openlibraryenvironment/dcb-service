package org.olf.reshare.dcb.request.fulfilment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.reshare.dcb.core.model.*;
import org.olf.reshare.dcb.request.resolution.SupplierRequestService;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.time.Instant.now;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.*;

class SupplyingAgencyServiceTests {
	@Mock
	HostLmsService hostLmsService;
	@Mock
	SupplierRequestService supplierRequestService;
	@Mock
	PatronService patronService;
	@Mock
	HostLmsClient hostLmsClient;
	@Mock
	PatronRequestService patronRequestService;
	@InjectMocks
	SupplyingAgencyService supplyingAgencyService;

	PatronRequest patronRequest;
	PatronRequest updatedPatronRequest;
	SupplierRequest supplierRequest;
	Patron patron;
	PatronIdentity patronIdentity;
	DataHostLms dataHostLms;

	@BeforeEach
	void setup() {
		MockitoAnnotations.initMocks(this);

		dataHostLms = new DataHostLms(randomUUID(), "homeHostLmsCode",
			"Fake Host LMS", SierraLmsClient.class.toString(), Map.of());

		final var patronId = randomUUID();
		final var patronIdentityId = randomUUID();
		patron = new Patron(patronId, null, null, "homeLibraryCode", List.of());
		patronIdentity = new PatronIdentity(patronIdentityId, null, null,
			patron, dataHostLms, "localId", true);

		patron.setPatronIdentities(List.of(patronIdentity));

		supplierRequest = new SupplierRequest(randomUUID(),
			patronRequest, "itemId", "supplierHostLmsCode", null, null);

		patronRequest = createPatronRequest(randomUUID(), RESOLVED, patron);
		updatedPatronRequest = createPatronRequest(randomUUID(), REQUEST_PLACED_AT_SUPPLYING_AGENCY, patron);
	}

	@DisplayName("patron is known to supplier and is also known to dcb")
	@Test
	void shouldReturnPlacedAtSupplyingAgencyWhenPatronIsKnownToSupplierAndToDCB() {
		// Arrange
		final var dataHostLms = new DataHostLms(randomUUID(), "supplierHostLmsCode",
			"Fake Host LMS", SierraLmsClient.class.toString(), Map.of());
		final var supplierPatronIdentity = new PatronIdentity(randomUUID(), null, null, patron, dataHostLms, "258740925", false);
		patron.setPatronIdentities(List.of(patronIdentity, supplierPatronIdentity));
		patronRequest.setPatron(patron);

		when(supplierRequestService.findAllSupplierRequestsFor(patronRequest))
			.thenAnswer(invocation ->  Mono.just(List.of(supplierRequest)));

		when(hostLmsService.getClientFor("supplierHostLmsCode"))
			.thenAnswer(invocation -> Mono.just(hostLmsClient));

		when(hostLmsClient.patronFind("localId@homeLibraryCode"))
			.thenAnswer(invocation -> Mono.just("258740925"));

		when(patronRequestService.updatePatronRequest(any()))
			.thenAnswer(invocation ->  Mono.just(updatedPatronRequest));

		// Act
		final var pr = supplyingAgencyService.placePatronRequestAtSupplyingAgency(patronRequest).block();

		// Assert
		assertThat("Status wasn't expected.", pr.getStatusCode(), is(REQUEST_PLACED_AT_SUPPLYING_AGENCY));
		verify(supplierRequestService).findAllSupplierRequestsFor(patronRequest);
		verify(hostLmsClient).patronFind(any());
		verify(hostLmsClient, times(0)).postPatron(any(), any());
		verify(patronService, times(0)).createPatronIdentity(any(), any(), any(), any());
	}

	@DisplayName("patron is known to supplier but is not known to dcb")
	@Test
	void shouldReturnPlacedAtSupplyingAgencyWhenPatronIsKnownToSupplierButNotToDCB() {
		// Arrange
		when(supplierRequestService.findAllSupplierRequestsFor(patronRequest))
			.thenAnswer(invocation ->  Mono.just(List.of(supplierRequest)));

		when(hostLmsService.getClientFor("supplierHostLmsCode"))
			.thenAnswer(invocation -> Mono.just(hostLmsClient));

		when(hostLmsClient.patronFind("localId@homeLibraryCode"))
			.thenAnswer(invocation -> Mono.just("258740925"));

		when(patronService.createPatronIdentity(any(), any(), any(), any()))
			.thenAnswer(invocation -> Mono.just(new PatronIdentity(randomUUID(), null, null, patron, dataHostLms, "258740925", false)));

		when(patronRequestService.updatePatronRequest(any()))
			.thenAnswer(invocation ->  Mono.just(updatedPatronRequest));

		// Act
		final var pr = supplyingAgencyService.placePatronRequestAtSupplyingAgency(patronRequest).block();

		// Assert
		assertThat("Status wasn't expected.", pr.getStatusCode(), is(REQUEST_PLACED_AT_SUPPLYING_AGENCY));
		verify(supplierRequestService).findAllSupplierRequestsFor(patronRequest);
		verify(hostLmsClient).patronFind(any());
		verify(hostLmsClient, times(0)).postPatron(any(), any());
		verify(patronService).createPatronIdentity(any(), any(), any(), any());
	}

	@DisplayName("patron is not known to supplier but is known to dcb")
	@Test
	void shouldReturnPlacedAtSupplyingAgencyWhenPatronIsNotKnownToSupplierButIsToDCB() {
		// Arrange
		final var dataHostLms = new DataHostLms(randomUUID(), "supplierHostLmsCode",
			"Fake Host LMS", SierraLmsClient.class.toString(), Map.of());
		final var supplierPatronIdentity = new PatronIdentity(randomUUID(), null, null, patron, dataHostLms, "258740925", false);
		patron.setPatronIdentities(List.of(patronIdentity, supplierPatronIdentity));
		patronRequest.setPatron(patron);

		when(supplierRequestService.findAllSupplierRequestsFor(patronRequest))
			.thenAnswer(invocation ->  Mono.just(List.of(supplierRequest)));

		when(hostLmsService.getClientFor("supplierHostLmsCode"))
			.thenAnswer(invocation -> Mono.just(hostLmsClient));

		when(hostLmsClient.patronFind("localId@homeLibraryCode"))
			.thenAnswer(invocation -> Mono.empty());

		when(hostLmsClient.postPatron("localId@homeLibraryCode", 100))
			.thenAnswer(invocation -> Mono.just("258740925"));

		when(patronRequestService.updatePatronRequest(any()))
			.thenAnswer(invocation ->  Mono.just(updatedPatronRequest));

		// Act
		final var pr = supplyingAgencyService.placePatronRequestAtSupplyingAgency(patronRequest).block();

		// Assert
		assertThat("Status wasn't expected.", pr.getStatusCode(), is(REQUEST_PLACED_AT_SUPPLYING_AGENCY));
		verify(supplierRequestService).findAllSupplierRequestsFor(patronRequest);
		verify(hostLmsClient).patronFind(any());
		verify(hostLmsClient).postPatron(any(), any());
		verify(patronService, times(0)).createPatronIdentity(any(), any(), any(), any());
	}

	@DisplayName("patron is not known to supplier and also not known to dcb")
	@Test
	void shouldReturnPlacedAtSupplyingAgencyWhenPatronIsNotKnownToSupplierAndNotToDCB() {
		// Arrange
		when(supplierRequestService.findAllSupplierRequestsFor(patronRequest))
			.thenAnswer(invocation ->  Mono.just(List.of(supplierRequest)));

		when(hostLmsService.getClientFor("supplierHostLmsCode"))
			.thenAnswer(invocation -> Mono.just(hostLmsClient));

		when(hostLmsClient.patronFind("localId@homeLibraryCode"))
			.thenAnswer(invocation -> Mono.empty());

		when(hostLmsClient.postPatron("localId@homeLibraryCode", 100))
			.thenAnswer(invocation -> Mono.just("258740925"));

		when(patronService.createPatronIdentity(any(), any(), any(), any()))
			.thenAnswer(invocation -> Mono.just(new PatronIdentity(randomUUID(), null, null, patron, dataHostLms, "258740925", false)));

		when(patronRequestService.updatePatronRequest(any()))
			.thenAnswer(invocation ->  Mono.just(updatedPatronRequest));

		// Act
		final var pr = supplyingAgencyService.placePatronRequestAtSupplyingAgency(patronRequest).block();

		// Assert
		assertThat("Status wasn't expected.", pr.getStatusCode(), is(REQUEST_PLACED_AT_SUPPLYING_AGENCY));
		verify(supplierRequestService).findAllSupplierRequestsFor(patronRequest);
		verify(hostLmsClient).patronFind(any());
		verify(hostLmsClient).postPatron(any(), any());
		verify(patronService).createPatronIdentity(any(), any(), any(), any());
		verifyNoMoreInteractions(supplierRequestService);
	}

	private static PatronRequest createPatronRequest(UUID id, String status, Patron patron) {
		return new PatronRequest(id, now(), now(),
			patron, randomUUID(), "pickupLocationCode",
			status, null, null);
	}
}
