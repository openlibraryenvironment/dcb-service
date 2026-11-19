package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.MultipleVirtualPatronsFound;
import org.olf.dcb.core.interaction.VirtualPatronNotFound;
import org.olf.dcb.core.interaction.VirtualPatronRemoval;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Event;
import org.olf.dcb.core.model.Patron;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.request.fulfilment.PatronService.PatronId;
import org.olf.dcb.security.CallerScope;
import org.olf.dcb.storage.AgencyRepository;
import org.olf.dcb.storage.EventLogRepository;
import org.olf.dcb.storage.PatronIdentityRepository;
import org.olf.dcb.storage.PatronRequestRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class VirtualPatronRemovalServiceTests {
	private static final CallerScope CONSORTIUM = new CallerScope(true, List.of(), false);
	private static final String STAFF = "staff-subject";

	private final DataHostLms homeSystem = DataHostLms.builder().id(UUID.randomUUID()).code("HOME").build();
	private final DataHostLms lenderSystem = DataHostLms.builder().id(UUID.randomUUID()).code("LENDER").build();
	private final DataHostLms secondLenderSystem = DataHostLms.builder().id(UUID.randomUUID()).code("LENDER2").build();

	private PatronService patronService;
	private PatronIdentityRepository patronIdentityRepository;
	private PatronRequestRepository patronRequestRepository;
	private AgencyRepository agencyRepository;
	private HostLmsService hostLmsService;
	private VirtualIdentityRecords records;
	private EventLogRepository eventLogRepository;
	private HostLmsClient lender;
	private HostLmsClient secondLender;
	private VirtualPatronRemovalService service;

	private Patron patron;
	private PatronIdentity home;
	private PatronIdentity virtual;

	@BeforeEach
	void beforeEach() {
		patronService = mock(PatronService.class);
		patronIdentityRepository = mock(PatronIdentityRepository.class);
		patronRequestRepository = mock(PatronRequestRepository.class);
		agencyRepository = mock(AgencyRepository.class);
		hostLmsService = mock(HostLmsService.class);
		records = mock(VirtualIdentityRecords.class);
		eventLogRepository = mock(EventLogRepository.class);
		lender = mock(HostLmsClient.class);
		secondLender = mock(HostLmsClient.class);

		service = new VirtualPatronRemovalService(patronService, patronIdentityRepository, patronRequestRepository,
			agencyRepository, hostLmsService, records, eventLogRepository, new VirtualPatronRemovalLimiter(100));

		patron = Patron.builder().id(UUID.randomUUID()).patronIdentities(new ArrayList<>()).build();
		home = identity(homeSystem, "home-1", true);
		home.setResolvedAgency(DataAgency.builder().code("BORROWER_AG").hostLms(homeSystem).build());
		virtual = identity(lenderSystem, "virtual-1", false);
		patron.getPatronIdentities().addAll(List.of(home, virtual));

		when(patronService.getPatronIdentityById(virtual.getId())).thenReturn(Mono.just(virtual));
		when(patronService.getPatronIdentityById(home.getId())).thenReturn(Mono.just(home));
		when(patronService.findById(any(PatronId.class))).thenReturn(Mono.just(patron));
		when(patronIdentityRepository.existsByLocalIdAndHostLmsAndHomeIdentity(anyString(), any(), any()))
			.thenReturn(Mono.just(false));
		when(patronRequestRepository.countUnfinishedRequestsForPatron(patron.getId())).thenReturn(Mono.just(0L));
		when(hostLmsService.getClientFor("LENDER")).thenReturn(Mono.just(lender));
		when(hostLmsService.getClientFor("LENDER2")).thenReturn(Mono.just(secondLender));
		when(records.forget(any())).thenReturn(Mono.empty());
		when(eventLogRepository.save(any())).thenAnswer(call -> Mono.just((Event) call.getArgument(0)));

		lenderAnswers(lender, Set.of(VirtualPatronRemoval.DELETE), "virtual-1");
	}

	@Test
	void deletesWhereTheLibrarySystemCanAndForgetsDcbsRecordAfterwards() {
		StepVerifier.create(service.removeOne(virtual.getId(), null, CONSORTIUM, STAFF))
			.assertNext(result -> assertThat(result.outcome(), is("DELETED")))
			.verifyComplete();

		final var order = inOrder(lender, records);
		order.verify(lender).deletePatron("virtual-1");
		order.verify(records).forget(virtual.getId());
	}

	@Test
	void expiresWhereTheLibrarySystemCanOnlyExpireAndKeepsDcbsRecord() {
		lenderAnswers(lender, Set.of(VirtualPatronRemoval.EXPIRE), "virtual-1");

		StepVerifier.create(service.removeOne(virtual.getId(), null, CONSORTIUM, STAFF))
			.assertNext(result -> assertThat(result.outcome(), is("EXPIRED")))
			.verifyComplete();

		verify(lender).retireVirtualPatron(virtual);
		verify(records, never()).forget(any());
	}

	@Test
	void refusesAModeTheLibrarySystemDoesNotSupport() {
		expectRefusal(service.removeOne(virtual.getId(), VirtualPatronRemoval.EXPIRE, CONSORTIUM, STAFF), "NOT_SUPPORTED");
		verifyLenderUntouched();
	}

	@Test
	void refusesThePatronsOwnAccount() {
		expectRefusal(service.removeOne(home.getId(), null, CONSORTIUM, STAFF), "NOT_A_VIRTUAL_PATRON");
		verifyLenderUntouched();
	}

	@Test
	void refusesARecordThatIsSomeonesOwnAccountInThatSystem() {
		when(patronIdentityRepository.existsByLocalIdAndHostLmsAndHomeIdentity("virtual-1", lenderSystem, true))
			.thenReturn(Mono.just(true));

		expectRefusal(service.removeOne(virtual.getId(), null, CONSORTIUM, STAFF), "NOT_A_VIRTUAL_PATRON");
		verifyLenderUntouched();
	}

	@Test
	void refusesABorrowerWithAnyRequestNotYetFinalised() {
		when(patronRequestRepository.countUnfinishedRequestsForPatron(patron.getId())).thenReturn(Mono.just(1L));

		expectRefusal(service.removeOne(virtual.getId(), null, CONSORTIUM, STAFF), "PATRON_HAS_ACTIVE_REQUESTS");
		expectRefusal(service.removeAll(patron.getId(), CONSORTIUM, STAFF), "PATRON_HAS_ACTIVE_REQUESTS");
		verifyLenderUntouched();
	}

	@Test
	void refusesARecordTheLibrarySystemDoesNotNameAsDcbsVirtualPatron() {
		lenderAnswers(lender, Set.of(VirtualPatronRemoval.DELETE), "someone-else");

		expectRefusal(service.removeOne(virtual.getId(), null, CONSORTIUM, STAFF), "NOT_CONFIRMED_AT_LIBRARY");
		verify(lender, never()).deletePatron(any());
		verify(records, never()).forget(any());
	}

	@Test
	void refusesWhenTheLibrarySystemHasSeveralCandidates() {
		when(lender.findVirtualPatron(any())).thenReturn(Mono.error(MultipleVirtualPatronsFound.builder().build()));

		expectRefusal(service.removeOne(virtual.getId(), null, CONSORTIUM, STAFF), "NOT_CONFIRMED_AT_LIBRARY");
		verify(lender, never()).deletePatron(any());
	}

	@Test
	void onlyForgetsDcbsRecordWhenTheLibrarySystemNoLongerHasIt() {
		when(lender.findVirtualPatron(any())).thenReturn(Mono.error(VirtualPatronNotFound.builder().build()));

		StepVerifier.create(service.removeOne(virtual.getId(), null, CONSORTIUM, STAFF))
			.assertNext(result -> assertThat(result.outcome(), is("ALREADY_GONE")))
			.verifyComplete();

		verify(lender, never()).deletePatron(any());
		verify(records).forget(virtual.getId());
	}

	@Test
	void theBorrowersLibraryMayRemoveOneOrAll() {
		final var borrowersLibrary = new CallerScope(false, List.of("BORROWER_AG"), true);

		StepVerifier.create(service.removeOne(virtual.getId(), null, borrowersLibrary, STAFF))
			.assertNext(result -> assertThat(result.outcome(), is("DELETED")))
			.verifyComplete();
	}

	@Test
	void theLibraryWhoseSystemHoldsItMayRemoveThatOneButNotEveryOne() {
		final var lendingLibrary = new CallerScope(false, List.of("LENDER_AG"), true);
		when(agencyRepository.findByCodeIn(List.of("LENDER_AG")))
			.thenReturn(Flux.just(DataAgency.builder().code("LENDER_AG").hostLms(lenderSystem).build()));

		StepVerifier.create(service.removeOne(virtual.getId(), null, lendingLibrary, STAFF))
			.assertNext(result -> assertThat(result.outcome(), is("DELETED")))
			.verifyComplete();

		expectRefusal(service.removeAll(patron.getId(), lendingLibrary, STAFF), "FORBIDDEN");
	}

	@Test
	void anyOtherLibraryIsRefusedWithoutTouchingTheLibrarySystem() {
		final var otherLibrary = new CallerScope(false, List.of("OTHER_AG"), true);
		when(agencyRepository.findByCodeIn(List.of("OTHER_AG")))
			.thenReturn(Flux.just(DataAgency.builder().code("OTHER_AG").hostLms(secondLenderSystem).build()));

		expectRefusal(service.removeOne(virtual.getId(), null, otherLibrary, STAFF), "FORBIDDEN");
		verifyLenderUntouched();
	}

	@Test
	void removingEveryOneGoesLibraryByLibraryAndReportsEach() {
		final var second = identity(secondLenderSystem, "virtual-2", false);
		patron.getPatronIdentities().add(second);
		lenderAnswers(secondLender, Set.of(), "virtual-2");

		StepVerifier.create(service.removeAll(patron.getId(), CONSORTIUM, STAFF))
			.assertNext(results -> assertThat(results.stream().map(VirtualPatronRemovalResult::outcome).toList(),
				contains("DELETED", "NOT_SUPPORTED")))
			.verifyComplete();

		verify(lender).deletePatron("virtual-1");
		verify(secondLender, never()).deletePatron(any());
		verify(lender, never()).deletePatron("home-1");
	}

	private void lenderAnswers(HostLmsClient client, Set<VirtualPatronRemoval> removals, String virtualLocalId) {
		when(client.virtualPatronRemovals()).thenReturn(removals);
		when(client.findVirtualPatron(any())).thenReturn(Mono.just(
			org.olf.dcb.core.interaction.Patron.builder().localId(List.of(virtualLocalId)).build()));
		when(client.deletePatron(any())).thenReturn(Mono.just("OK"));
		when(client.retireVirtualPatron(any())).thenReturn(Mono.empty());
	}

	private PatronIdentity identity(DataHostLms system, String localId, boolean homeIdentity) {
		return PatronIdentity.builder()
			.id(UUID.randomUUID())
			.patron(patron)
			.hostLms(system)
			.localId(localId)
			.homeIdentity(homeIdentity)
			.build();
	}

	private void verifyLenderUntouched() {
		verify(lender, never()).deletePatron(any());
		verify(lender, never()).retireVirtualPatron(any());
		verify(records, never()).forget(any());
	}

	private static void expectRefusal(Mono<?> removal, String code) {
		StepVerifier.create(removal)
			.expectErrorSatisfies(error -> assertThat(((VirtualPatronRemovalRefused) error).getCode(), is(code)))
			.verify();
	}
}
