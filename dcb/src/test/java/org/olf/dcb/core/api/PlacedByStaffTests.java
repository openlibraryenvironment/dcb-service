package org.olf.dcb.core.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.olf.dcb.request.fulfilment.PatronRequestService;
import org.olf.dcb.request.fulfilment.PlacePatronRequestCommand;
import org.olf.dcb.request.workflow.ManualCleanupService;
import org.olf.dcb.security.PatronRequestAccessGuard;
import org.olf.dcb.security.StaffClients;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.tracking.TrackingService;

import io.micronaut.security.authentication.Authentication;
import reactor.core.publisher.Mono;

/** No database: who placed a request is decided from the caller's token, never from the body. */
class PlacedByStaffTests {
	private PatronRequestService patronRequestService;
	private PatronRequestController controller;

	@BeforeEach
	void beforeEach() {
		patronRequestService = mock(PatronRequestService.class);
		when(patronRequestService.placePatronRequest(any())).thenReturn(Mono.empty());
		when(patronRequestService.placePatronRequestExpeditedCheckout(any())).thenReturn(Mono.empty());

		controller = new PatronRequestController(patronRequestService,
			mock(PatronRequestRepository.class), mock(PatronRequestAccessGuard.class),
			mock(ManualCleanupService.class), mock(TrackingService.class),
			new StaffClients(List.of("dcb-admin", " dcb-admin-for-libraries ")));
	}

	@Test
	void shouldMarkARequestPlacedThroughAStaffAppAsStaffs() {
		assertThat(placedByStaff(command(false), tokenFor("dcb-admin-for-libraries")), is(true));
	}

	@Test
	void shouldNotTrustTheBodyToSayStaffPlacedIt() {
		assertThat(placedByStaff(command(true), tokenFor("ebsco-locate")), is(false));
	}

	@Test
	void shouldNotMarkARequestFromATokenWithNoClientAsStaffs() {
		final var noClient = Authentication.build("service", List.of(ADMINISTRATOR), Map.of());

		assertThat(placedByStaff(command(false), noClient), is(false));
	}

	@Test
	void shouldLeaveWhoPlacedItUnknownWhenNoStaffClientIsConfigured() {
		final var unconfigured = new PatronRequestController(patronRequestService,
			mock(PatronRequestRepository.class), mock(PatronRequestAccessGuard.class),
			mock(ManualCleanupService.class), mock(TrackingService.class), new StaffClients(List.of()));

		unconfigured.placePatronRequest(command(true), tokenFor("dcb-admin")).block();

		final var placed = ArgumentCaptor.forClass(PlacePatronRequestCommand.class);
		verify(patronRequestService).placePatronRequest(placed.capture());

		assertThat(placed.getValue().getIsPlacedByStaff(), is(nullValue()));
	}

	@Test
	void shouldMarkAnExpeditedCheckoutByItsTokenToo() {
		controller.placePatronRequestExpeditedCheckout(command(false), tokenFor("dcb-admin")).block();

		final var placed = ArgumentCaptor.forClass(PlacePatronRequestCommand.class);
		verify(patronRequestService).placePatronRequestExpeditedCheckout(placed.capture());

		assertThat(placed.getValue().getIsPlacedByStaff(), is(true));
	}

	private boolean placedByStaff(PlacePatronRequestCommand command, Authentication caller) {
		controller.placePatronRequest(command, caller).block();

		final var placed = ArgumentCaptor.forClass(PlacePatronRequestCommand.class);
		verify(patronRequestService).placePatronRequest(placed.capture());

		return placed.getValue().getIsPlacedByStaff();
	}

	private static Authentication tokenFor(String clientId) {
		return Authentication.build("caller", List.of(ADMINISTRATOR), Map.of("azp", clientId));
	}

	private static PlacePatronRequestCommand command(boolean bodySaysStaff) {
		return PlacePatronRequestCommand.builder()
			.citation(PlacePatronRequestCommand.Citation.builder().bibClusterId(UUID.randomUUID()).build())
			.pickupLocation(PlacePatronRequestCommand.PickupLocation.builder().code("pickup").build())
			.requestor(PlacePatronRequestCommand.Requestor.builder().localId("1").localSystemCode("SYS").build())
			.isPlacedByStaff(bodySaysStaff)
			.build();
	}
}
