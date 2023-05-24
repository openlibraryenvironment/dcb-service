package org.olf.reshare.dcb.request.fulfilment;

import static java.time.Instant.now;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.REQUEST_PLACED_AT_SUPPLYING_AGENCY;
import static org.olf.reshare.dcb.request.fulfilment.PatronRequestStatusConstants.RESOLVED;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.reshare.dcb.core.model.DataHostLms;
import org.olf.reshare.dcb.core.model.Patron;
import org.olf.reshare.dcb.core.model.PatronIdentity;
import org.olf.reshare.dcb.core.model.PatronRequest;
import org.olf.reshare.dcb.core.model.SupplierRequest;
import org.olf.reshare.dcb.request.resolution.SupplierRequestService;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

@ExtendWith(MockitoExtension.class)
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
	PatronTypeService patronTypeService;

	@InjectMocks
	SupplyingAgencyService supplyingAgencyService;

	private PatronRequest patronRequest;
	private SupplierRequest supplierRequest;
	private Patron patron;
	private PatronIdentity supplierPatronIdentity;

	@BeforeEach
	void setUp() {
		// Home Identity
		final var dataHostLms = new DataHostLms(
			randomUUID(),
			"homeHostLmsCode",
			"Fake Host LMS",
			SierraLmsClient.class.toString(),
			Map.of()
		);

		final var patronIdentity = new PatronIdentity(
			randomUUID(),
			null,
			null,
			patron,
			dataHostLms,
			"localId",
			true
		);

		// Supplier Identity
		DataHostLms supplierDataHostLms = new DataHostLms(
			randomUUID(),
			"supplierHostLmsCode",
			"Fake Host LMS",
			SierraLmsClient.class.toString(),
			Map.of()
		);

		supplierPatronIdentity = new PatronIdentity(
			randomUUID(),
			null,
			null,
			patron,
			supplierDataHostLms,
			"258740925",
			false
		);

		// Create Patron and add Identities
		patron = new Patron(randomUUID(),
			null,
			null,
			"homeLibraryCode",
			List.of());
		patron.setPatronIdentities(List.of(patronIdentity, supplierPatronIdentity));

		// Create SupplierRequest and PatronRequest
		supplierRequest = new SupplierRequest(
			randomUUID(),
			patronRequest,
			"itemId",
			"itemBarcode",
			"itemLocationCode",
			"supplierHostLmsCode",
			null,
			null,
			null
		);

		patronRequest = createPatronRequest(
			randomUUID(),
			RESOLVED,
			patron
		);

		// Common mocks
		when(supplierRequestService.findSupplierRequestFor(any()))
			.thenAnswer(invocation -> Mono.just(supplierRequest));

		when(hostLmsService.getClientFor("supplierHostLmsCode"))
			.thenAnswer(invocation -> Mono.just(hostLmsClient));
	}

	@DisplayName("patron is known to supplier and places patron request")
	@Test
	void shouldReturnPlacedAtSupplyingAgencyWhenPatronIsKnownToSupplier() {
		// Arrange
		when(hostLmsClient.patronFind("localId@homeLibraryCode"))
			.thenAnswer(invocation -> Mono.just("258740925"));

		when(patronService.getUniqueIdStringFor(any()))
			.thenAnswer(invocation ->  "localId@homeLibraryCode" );

		when(patronService.checkForPatronIdentity(any(), any(), any()))
			.thenAnswer(invocation -> Mono.just( supplierPatronIdentity ));

		when(hostLmsClient.placeHoldRequest(any(), any(), any(), any()))
			.thenAnswer(invocation ->  Mono.just( Tuples.of("489365810", "0") ));

		final var placedSupplierRequest = createPlacedSupplierRequest();

		when(supplierRequestService.updateSupplierRequest(any()))
			.thenReturn(Mono.just(placedSupplierRequest));

		// Act
		final var pr = supplyingAgencyService.placePatronRequestAtSupplyingAgency(patronRequest).block();

		// Assert
		assertThat("Status wasn't expected.", pr.getStatusCode(), is(REQUEST_PLACED_AT_SUPPLYING_AGENCY));

		verify(hostLmsClient).patronFind(any());
		verify(hostLmsClient, times(0)).createPatron(any(), any());
	}

	@DisplayName("patron is not known to supplier and places patron request")
	@Test
	void shouldReturnPlacedAtSupplyingAgencyWhenPatronIsNotKnownToSupplier() {
		// Arrange
		when(hostLmsClient.patronFind("localId@homeLibraryCode"))
			.thenAnswer(invocation -> Mono.empty());

		when(patronTypeService.determinePatronType())
			.thenAnswer(invocation -> 210);

		when(hostLmsClient.createPatron("localId@homeLibraryCode", 210))
			.thenAnswer(invocation -> Mono.just("258740925"));

		when(patronService.getUniqueIdStringFor(any()))
			.thenAnswer(invocation ->  "localId@homeLibraryCode" );

		when(patronService.checkForPatronIdentity(any(), any(), any()))
			.thenAnswer(invocation -> Mono.just( supplierPatronIdentity ));

		when(hostLmsClient.placeHoldRequest(any(), any(), any(), any()))
			.thenAnswer(invocation ->  Mono.just( Tuples.of("489365810", "0") ));

		when(supplierRequestService.updateSupplierRequest(any()))
			.thenReturn(Mono.just(createPlacedSupplierRequest()));

		// Act
		final var pr = supplyingAgencyService.placePatronRequestAtSupplyingAgency(patronRequest).block();

		// Assert
		assertThat("Status wasn't expected.", pr.getStatusCode(), is(REQUEST_PLACED_AT_SUPPLYING_AGENCY));

		verify(hostLmsClient).patronFind(any());
		verify(hostLmsClient).createPatron(any(), any());
	}

	@DisplayName("request cannot be placed in supplying agency’s local system")
	@Test
	void placePatronRequestAtSupplyingAgencyWithErrorResponse() {
		// Arrange
		when(hostLmsClient.patronFind("localId@homeLibraryCode"))
			.thenAnswer(invocation -> Mono.empty());

		when(patronTypeService.determinePatronType())
			.thenAnswer(invocation -> 210);

		when(hostLmsClient.createPatron("localId@homeLibraryCode", 210))
			.thenAnswer(invocation -> Mono.just("258740925"));

		when(patronService.getUniqueIdStringFor(any()))
			.thenAnswer(invocation ->  "localId@homeLibraryCode" );

		when(patronService.checkForPatronIdentity(any(), any(), any()))
			.thenAnswer(invocation -> Mono.just( supplierPatronIdentity ));

		when(hostLmsClient.placeHoldRequest(anyString(), anyString(), anyString(), anyString()))
			.thenAnswer(invocation -> Mono.error(new RuntimeException("Sierra Error")));

		// Act
		final var exception = assertThrows(RuntimeException.class,
			() -> supplyingAgencyService.placePatronRequestAtSupplyingAgency(patronRequest).block());

		// Assert
		assertThat(exception.getMessage(), is("Sierra Error"));
		assertThat(exception.getLocalizedMessage(), is("Sierra Error"));
	}

	private static PatronRequest createPatronRequest(UUID id, String status, Patron patron) {
		return new PatronRequest(id, now(), now(),
			patron, randomUUID(), "pickupLocationCode",
			status, null, null);
	}

	private SupplierRequest createPlacedSupplierRequest() {
		return SupplierRequest.builder()
			.id(randomUUID())
			.patronRequest(patronRequest)
			.build();
	}
}
