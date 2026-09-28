package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.IntMessageService;
import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.request.workflow.PatronRequestWorkflowService;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.BibRepository;
import org.olf.dcb.storage.PatronRequestAuditRepository;
import org.olf.dcb.storage.PatronRequestRepository;

import io.micronaut.context.BeanProvider;
import reactor.core.publisher.Mono;

/**
 * Walk-up refusals are failed checks the desk can act on, and the ones that need no library
 * system are made before any library system is called.
 */
class WalkUpRequestTests {
	private static final String ITEM_SYSTEM = "LENDER-SYSTEM";
	private static final String PICKUP_LOCATION = UUID.randomUUID().toString();

	private final UUID itemSystemId = UUID.randomUUID();
	private final DataAgency lender = DataAgency.builder().id(UUID.randomUUID()).code("lender").build();

	private HostLmsService hostLmsService;
	private HostLmsClient itemSystem;
	private AgencyRepository agencyRepository;
	private LocationService locationService;
	private BibRepository bibRepository;
	private PatronRequestPreflightChecksService preflightChecksService;
	private PatronRequestService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void beforeEach() {
		itemSystem = mock(HostLmsClient.class);
		when(itemSystem.getHostLms()).thenReturn(DataHostLms.builder().id(itemSystemId).code(ITEM_SYSTEM).build());

		hostLmsService = mock(HostLmsService.class);
		when(hostLmsService.getClientFor(ITEM_SYSTEM)).thenReturn(Mono.just(itemSystem));

		agencyRepository = mock(AgencyRepository.class);
		when(agencyRepository.findOneByCode("lender")).thenReturn(Mono.just(lender));
		when(agencyRepository.findHostLmsIdById(lender.getId())).thenReturn(Mono.just(itemSystemId));
		when(agencyRepository.findById(lender.getId())).thenReturn(Mono.just(lender));

		locationService = mock(LocationService.class);
		when(locationService.findById(PICKUP_LOCATION))
			.thenReturn(Mono.just(Location.builder().agency(lender).build()));

		bibRepository = mock(BibRepository.class);
		preflightChecksService = mock(PatronRequestPreflightChecksService.class);

		service = new PatronRequestService(mock(PatronRequestRepository.class),
			mock(PatronRequestWorkflowService.class), mock(PatronService.class),
			mock(FindOrCreatePatronService.class), preflightChecksService,
			mock(PatronRequestAuditRepository.class), mock(BeanProvider.class), hostLmsService,
			bibRepository, agencyRepository, locationService, new IntMessageService());
	}

	@Test
	void refusesAPatronOfTheLibraryHoldingTheItemBeforeAskingAnySystem() {
		assertThat(refusalCode(command("lender", PICKUP_LOCATION)), is("WALK_UP_SAME_LIBRARY"));

		verifyNoInteractions(hostLmsService);
	}

	@Test
	void refusesAnItemLibraryThatIsNotOnTheNamedSystem() {
		when(agencyRepository.findHostLmsIdById(lender.getId())).thenReturn(Mono.just(UUID.randomUUID()));

		assertThat(refusalCode(command("borrower", PICKUP_LOCATION)), is("WALK_UP_ITEM_AGENCY_MISMATCH"));

		verify(itemSystem, never()).getItemByBarcode(any());
	}

	@Test
	void refusesAPickupAwayFromTheItemsLibrary() {
		final var elsewhere = DataAgency.builder().id(UUID.randomUUID()).code("elsewhere").build();
		when(locationService.findById(PICKUP_LOCATION))
			.thenReturn(Mono.just(Location.builder().agency(elsewhere).build()));
		when(agencyRepository.findById(elsewhere.getId())).thenReturn(Mono.just(elsewhere));

		assertThat(refusalCode(command("borrower", PICKUP_LOCATION)), is("WALK_UP_PICKUP_ELSEWHERE"));

		verify(itemSystem, never()).getItemByBarcode(any());
	}

	@Test
	void refusesABarcodeTheSystemDoesNotKnow() {
		when(itemSystem.getItemByBarcode("item-barcode")).thenReturn(Mono.empty());

		assertThat(refusalCode(command("borrower", PICKUP_LOCATION)), is("ITEM_NOT_FOUND"));
	}

	@Test
	void refusesAnItemThatIsNotAvailable() {
		when(itemSystem.getItemByBarcode("item-barcode")).thenReturn(Mono.just(item(HostLmsItem.ITEM_LOANED, "bib-1")));

		assertThat(refusalCode(command("borrower", PICKUP_LOCATION)), is("ITEM_NOT_AVAILABLE"));
	}

	@Test
	void refusesAnItemWithNoBibliographicRecord() {
		when(itemSystem.getItemByBarcode("item-barcode")).thenReturn(Mono.just(item(HostLmsItem.ITEM_AVAILABLE, null)));

		assertThat(refusalCode(command("borrower", PICKUP_LOCATION)), is("ITEM_NOT_IN_SHARED_INDEX"));
	}

	@Test
	void refusesAnItemWhoseCatalogueEntryWasDeleted() {
		when(itemSystem.getItemByBarcode("item-barcode")).thenReturn(Mono.just(item(HostLmsItem.ITEM_AVAILABLE, "bib-1")));
		when(bibRepository.findBySourceSystemIdAndSourceRecordId(itemSystemId, "bib-1"))
			.thenReturn(Mono.just(BibRecord.builder().id(UUID.randomUUID())
				.contributesTo(ClusterRecord.builder().id(UUID.randomUUID()).isDeleted(true).build())
				.build()));

		assertThat(refusalCode(command("borrower", PICKUP_LOCATION)), is("CLUSTER_DELETED"));
	}

	@Test
	void placesAnExpeditedRequestWithoutTheBarcodeInItsDescription() {
		when(itemSystem.getItemByBarcode("item-barcode")).thenReturn(Mono.just(item(HostLmsItem.ITEM_AVAILABLE, "bib-1")));
		when(bibRepository.findBySourceSystemIdAndSourceRecordId(itemSystemId, "bib-1"))
			.thenReturn(Mono.just(BibRecord.builder().id(UUID.randomUUID())
				.contributesTo(ClusterRecord.builder().id(UUID.randomUUID()).build())
				.build()));
		when(preflightChecksService.check(any())).thenReturn(Mono.empty());

		service.placeWalkUpRequest(command("borrower", PICKUP_LOCATION)).block();

		final var placed = ArgumentCaptor.forClass(PlacePatronRequestCommand.class);
		verify(preflightChecksService).check(placed.capture());
		assertThat(placed.getValue().getIsExpeditedRequest(), is(true));
		assertThat(placed.getValue().getDescription(), not(containsString("item-barcode")));
	}

	@Test
	void anOrdinaryRequestIsNeverExpeditedWhateverTheCallerSays() {
		when(preflightChecksService.check(any())).thenReturn(Mono.empty());

		service.placePatronRequest(PlacePatronRequestCommand.builder()
			.citation(PlacePatronRequestCommand.Citation.builder().bibClusterId(UUID.randomUUID()).build())
			.pickupLocation(PlacePatronRequestCommand.PickupLocation.builder().code(PICKUP_LOCATION).build())
			.requestor(PlacePatronRequestCommand.Requestor.builder().localId("p").localSystemCode("s").agencyCode("a").build())
			.isExpeditedRequest(true)
			.build()).block();

		final var checked = ArgumentCaptor.forClass(PlacePatronRequestCommand.class);
		verify(preflightChecksService).check(checked.capture());
		assertThat(checked.getValue().getIsExpeditedRequest(), is(false));
	}

	private String refusalCode(WalkUpRequestCommand command) {
		final var refusal = assertThrows(PreflightCheckFailedException.class,
			() -> service.placeWalkUpRequest(command).block());

		return refusal.getFailedChecks().get(0).getCode();
	}

	private static HostLmsItem item(String status, String bibId) {
		return HostLmsItem.builder().localId("item-1").status(status).bibId(bibId).build();
	}

	private static WalkUpRequestCommand command(String patronAgency, String pickupLocation) {
		return WalkUpRequestCommand.builder()
			.itemHostLmsCode(ITEM_SYSTEM)
			.itemAgencyCode("lender")
			.itemBarcode("item-barcode")
			.pickupLocationCode(pickupLocation)
			.patronLocalId("patron-1")
			.patronAgencyCode(patronAgency)
			.patronHostLmsCode("BORROWER-SYSTEM")
			.build();
	}
}
