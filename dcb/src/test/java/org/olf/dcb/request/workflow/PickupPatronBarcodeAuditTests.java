package org.olf.dcb.request.workflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.request.fulfilment.PatronRequestAuditService;
import org.olf.dcb.request.fulfilment.PatronTypeService;

import reactor.core.publisher.Mono;

class PickupPatronBarcodeAuditTests {
	private static final String PICKUP = "PICKUP-SYSTEM";
	private static final String NO_BARCODE_AUDIT =
		"Pickup patron has no barcode : the pickup library will be unable to check out to them";

	private PatronRequestAuditService auditService;
	private HostLmsClient pickupClient;
	private PlacePatronRequestAtPickupAgencyStateTransition transition;

	@BeforeEach
	void beforeEach() {
		auditService = mock(PatronRequestAuditService.class);
		when(auditService.addAuditEntry(any(PatronRequest.class), any(String.class)))
			.thenReturn(Mono.empty());

		final var patronTypeService = mock(PatronTypeService.class);
		when(patronTypeService.determinePatronType(any(), any(), any(), any()))
			.thenReturn(Mono.just("15"));

		pickupClient = mock(HostLmsClient.class);
		when(pickupClient.createPatron(any())).thenReturn(Mono.just("pickup-patron-1"));

		transition = new PlacePatronRequestAtPickupAgencyStateTransition(auditService,
			null, null, null, patronTypeService, null);
	}

	@Test
	void shouldAuditAPickupPatronWhoHasNoBarcode() {
		final var identity = homeIdentity(null);
		final var patronRequest = requestFor(identity);

		transition.createPatronAtPickupAgency(patronRequest, pickupClient, identity, PICKUP).block();

		verify(auditService).addAuditEntry(patronRequest, NO_BARCODE_AUDIT);
	}

	@Test
	void shouldNotAuditAPickupPatronWhoHasABarcode() {
		final var identity = homeIdentity("[2100045]");
		final var patronRequest = requestFor(identity);

		transition.createPatronAtPickupAgency(patronRequest, pickupClient, identity, PICKUP).block();

		verify(auditService, never()).addAuditEntry(any(PatronRequest.class), eq(NO_BARCODE_AUDIT));
	}

	private static PatronIdentity homeIdentity(String localBarcode) {
		return PatronIdentity.builder()
			.id(UUID.randomUUID())
			.localId("home-patron-1")
			.localPtype("15")
			.localBarcode(localBarcode)
			.homeIdentity(true)
			.hostLms(DataHostLms.builder().code("HOME-SYSTEM").build())
			.resolvedAgency(DataAgency.builder().code("home-agency").build())
			.build();
	}

	private static PatronRequest requestFor(PatronIdentity identity) {
		return PatronRequest.builder()
			.id(UUID.randomUUID())
			.patron(Patron.builder()
				.id(UUID.randomUUID())
				.patronIdentities(List.of(identity))
				.build())
			.build();
	}
}
