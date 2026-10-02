package org.olf.dcb.core.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.olf.dcb.security.RoleNames.CONSORTIUM_ADMIN;
import static org.olf.dcb.security.RoleNames.LIBRARY_ADMIN;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.request.fulfilment.PatronRequestService;
import org.olf.dcb.request.fulfilment.WalkUpRequestCommand;
import org.olf.dcb.request.workflow.ManualCleanupService;
import org.olf.dcb.security.PatronRequestAccessGuard;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.tracking.TrackingService;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.security.authentication.Authentication;
import reactor.core.publisher.Mono;

/**
 * A library-level caller places walk-ups only for items at a library in its own token: the
 * item, the patron and both library systems all arrive in the request body.
 */
class WalkUpCallerScopeTests {
	private PatronRequestService patronRequestService;
	private PatronRequestController controller;

	@BeforeEach
	void beforeEach() {
		patronRequestService = mock(PatronRequestService.class);

		controller = new PatronRequestController(patronRequestService,
			mock(PatronRequestRepository.class), mock(PatronRequestAccessGuard.class),
			mock(ManualCleanupService.class), mock(TrackingService.class));
	}

	@Test
	void refusesALibraryCallerAWalkUpForAnotherLibrarysItem() {
		final var caller = Authentication.build("desk", List.of(LIBRARY_ADMIN),
			Map.of("agencyCodes", List.of("library-a")));

		final var refusal = assertThrows(HttpStatusException.class,
			() -> controller.placeWalkUpRequest(command("library-b"), caller).block());

		assertThat(refusal.getStatus(), is(HttpStatus.FORBIDDEN));
		verifyNoInteractions(patronRequestService);
	}

	@Test
	void refusesALibraryCallerWhoseTokenNamesNoLibrary() {
		final var caller = Authentication.build("desk", List.of(LIBRARY_ADMIN), Map.of());

		assertThrows(HttpStatusException.class,
			() -> controller.placeWalkUpRequest(command("library-a"), caller).block());

		verifyNoInteractions(patronRequestService);
	}

	@Test
	void letsALibraryCallerPlaceAWalkUpForItsOwnItem() {
		when(patronRequestService.placeWalkUpRequest(any())).thenReturn(Mono.empty());

		final var caller = Authentication.build("desk", List.of(LIBRARY_ADMIN),
			Map.of("agencyCodes", List.of("library-a")));

		controller.placeWalkUpRequest(command("library-a"), caller).block();

		verify(patronRequestService).placeWalkUpRequest(any());
	}

	@Test
	void letsAConsortiumCallerPlaceAWalkUpAnywhere() {
		when(patronRequestService.placeWalkUpRequest(any())).thenReturn(Mono.empty());

		final var caller = Authentication.build("admin", List.of(CONSORTIUM_ADMIN), Map.of());

		controller.placeWalkUpRequest(command("library-b"), caller).block();

		verify(patronRequestService).placeWalkUpRequest(any());
	}

	private static WalkUpRequestCommand command(String itemAgency) {
		return WalkUpRequestCommand.builder()
			.itemHostLmsCode("ITEM-SYSTEM")
			.itemAgencyCode(itemAgency)
			.itemBarcode("item-barcode")
			.pickupLocationCode("pickup")
			.patronLocalId("patron-1")
			.patronAgencyCode("patron-library")
			.patronHostLmsCode("PATRON-SYSTEM")
			.build();
	}
}
