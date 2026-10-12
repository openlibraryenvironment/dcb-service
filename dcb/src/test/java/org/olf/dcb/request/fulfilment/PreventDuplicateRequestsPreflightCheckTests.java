package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.olf.dcb.core.model.PatronRequest.Status.CONFIRMED;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.LOANED;
import static org.olf.dcb.core.model.PatronRequest.Status.SUBMITTED_TO_DCB;
import static org.olf.dcb.security.RoleNames.ADMINISTRATOR;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.IntMessageService;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.security.StaffClients;
import org.olf.dcb.storage.PatronRequestRepository;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.PatronFixture;
import org.olf.dcb.test.PatronRequestsFixture;
import org.olf.dcb.test.SupplierRequestsFixture;

import io.micronaut.context.annotation.Property;
import io.micronaut.security.authentication.Authentication;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;

@Property(name = "dcb.requests.preflight-checks.duplicate-requests.enabled", value = "true")
@DcbTest
class PreventDuplicateRequestsPreflightCheckTests extends AbstractPreflightCheckTests {
	private static final String SYSTEM = "duplicate-check-system";
	private static final String PATRON = "duplicate-check-patron";
	private static final String SUPPLYING_SYSTEM = "duplicate-check-supplier";

	@Inject
	private PreventDuplicateRequestsPreFlightCheck check;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private StaffClients staffClients;
	@Inject
	private SupplierRequestsFixture supplierRequestsFixture;

	private final UUID title = UUID.randomUUID();

	@BeforeEach
	void beforeEach() {
		patronRequestsFixture.deleteAll();
		patronFixture.deleteAllPatrons();
		hostLmsFixture.deleteAll();
	}

	@Test
	void shouldPassWhenThePatronHasNoRequestForTheTitle() {
		assertThat(check(command(PATRON, title)), containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldFailWhenThePatronHasAnInFlightRequestForTheTitle() {
		earlierRequest(CONFIRMED, request -> request);

		assertThat(check(command(PATRON, title)), containsInAnyOrder(failedCheck("DUPLICATE_REQUEST_ATTEMPT")));
	}

	@Test
	void shouldFailWhenThePatronHasTheTitleOnLoan() {
		earlierRequest(LOANED, request -> request);

		assertThat(check(command(PATRON, title)), containsInAnyOrder(failedCheck("DUPLICATE_REQUEST_ATTEMPT")));
	}

	@Test
	void shouldPassWhenTheEarlierRequestFailed() {
		earlierRequest(ERROR, request -> request);

		assertThat(check(command(PATRON, title)), containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldPassWhenTheEarlierRequestIsNoLongerTracked() {
		earlierRequest(CONFIRMED, request -> request.isTooLong(true));

		assertThat(check(command(PATRON, title)), containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldPassForAnotherVolumeOfTheTitle() {
		earlierRequest(CONFIRMED, request -> request.requestedVolumeDesignation("v1"));

		assertThat(check(volumeCommand("v2")), containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldFailForTheSameVolumeOfTheTitle() {
		earlierRequest(CONFIRMED, request -> request.requestedVolumeDesignation("v1"));

		assertThat(check(volumeCommand("v1")), containsInAnyOrder(failedCheck("DUPLICATE_REQUEST_ATTEMPT")));
	}

	@Test
	void shouldTreatTheSameVolumeWrittenDifferentlyAsTheSame() {
		earlierRequest(CONFIRMED, request -> request.requestedVolumeDesignation("v.2"));

		assertThat(check(volumeCommand("Vol. 2")), containsInAnyOrder(failedCheck("DUPLICATE_REQUEST_ATTEMPT")));
	}

	@Test
	void shouldPassARequestForAVolumeWhenThePatronHasTheTitleWithoutOne() {
		earlierRequest(CONFIRMED, UnaryOperator.identity());

		assertThat(check(volumeCommand("v1")), containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldTreatABlankVolumeAsNoVolume() {
		earlierRequest(CONFIRMED, UnaryOperator.identity());

		assertThat(check(volumeCommand(" ")), containsInAnyOrder(failedCheck("DUPLICATE_REQUEST_ATTEMPT")));
	}

	@Test
	void shouldPassARequestForAChosenCopy() {
		earlierRequest(CONFIRMED, UnaryOperator.identity());

		assertThat(check(chosenCopyCommand("chosen-copy", "supplying-agency")), containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldRefuseTheSameCopyThePatronAlreadyHasComing() {
		supplying(earlierRequest(CONFIRMED, UnaryOperator.identity()), "copy-1", true);

		assertThat(check(chosenCopyCommand("copy-1", "supplying-agency")),
			containsInAnyOrder(failedCheck("DUPLICATE_REQUEST_ATTEMPT")));
	}

	@Test
	void shouldPassAnotherCopyOfATitleThePatronHasComing() {
		supplying(earlierRequest(CONFIRMED, UnaryOperator.identity()), "copy-1", true);

		assertThat(check(chosenCopyCommand("copy-2", "supplying-agency")), containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldRefuseAChosenCopyAnEarlierRequestHasChosenButNotYetResolved() {
		earlierRequest(SUBMITTED_TO_DCB, request -> request
			.isManuallySelectedItem(true)
			.localItemId("copy-1")
			.localItemHostlmsCode(SUPPLYING_SYSTEM));

		assertThat(check(chosenCopyCommand("copy-1", "supplying-agency")),
			containsInAnyOrder(failedCheck("DUPLICATE_REQUEST_ATTEMPT")));
	}

	@Test
	void shouldPassTheSameCopyWhenTheEarlierRequestForItFailed() {
		supplying(earlierRequest(ERROR, UnaryOperator.identity()), "copy-1", true);

		assertThat(check(chosenCopyCommand("copy-1", "supplying-agency")), containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldPassTheSameCopyOnceItIsNoLongerTheEarlierRequestsSupplier() {
		supplying(earlierRequest(CONFIRMED, UnaryOperator.identity()), "copy-1", false);

		assertThat(check(chosenCopyCommand("copy-1", "supplying-agency")), containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldCheckAnIncompleteChosenCopyAsARequestForTheTitle() {
		earlierRequest(CONFIRMED, UnaryOperator.identity());

		assertThat(check(chosenCopyCommand("copy-1", null)),
			containsInAnyOrder(failedCheck("DUPLICATE_REQUEST_ATTEMPT")));
	}

	@Test
	void shouldPassForAnotherPatron() {
		earlierRequest(CONFIRMED, request -> request);

		assertThat(check(command("another-patron", title)), containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldPassForAnotherTitle() {
		earlierRequest(CONFIRMED, request -> request);

		assertThat(check(command(PATRON, UUID.randomUUID())), containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldPassARequestPlacedByStaff() {
		earlierRequest(CONFIRMED, request -> request);

		final var placedByStaff = command(PATRON, title).toBuilder().isPlacedByStaff(true).build();

		assertThat(check(placedByStaff), containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldTreatBothAdminAppsAsStaffWithoutBeingConfigured() {
		assertThat(staffClients.issuedTo(tokenFor("dcb-admin")), is(true));
		assertThat(staffClients.issuedTo(tokenFor("dcb-admin-for-libraries")), is(true));
		assertThat(staffClients.issuedTo(tokenFor("ebsco-locate")), is(false));
	}

	@Test
	void shouldPassEverythingWhenNoStaffClientIsConfigured() {
		final var repository = mock(PatronRequestRepository.class);

		final var unarmed = new PreventDuplicateRequestsPreFlightCheck(repository, new IntMessageService(),
			new StaffClients(List.of()), null, null);

		assertThat(singleValueFrom(unarmed.check(command(PATRON, title))), containsInAnyOrder(passedCheck()));

		verifyNoInteractions(repository);
	}

	@Test
	void shouldPassWhenThePatronsRequestsCannotBeRead() {
		final var repository = mock(PatronRequestRepository.class);
		when(repository.findActiveRequestsForPatronAndTitle(any(), any(), any(), any(), any()))
			.thenReturn(Flux.error(new RuntimeException("database unavailable")));

		final var overUnreadableRequests = new PreventDuplicateRequestsPreFlightCheck(repository,
			new IntMessageService(), new StaffClients(List.of("dcb-admin")), null, null);

		assertThat(singleValueFrom(overUnreadableRequests.check(command(PATRON, title))),
			containsInAnyOrder(passedCheck()));
	}

	@Test
	void shouldRefuseAnActiveWindowThatIsNotPositive() {
		assertThrows(IllegalArgumentException.class, () -> new PreventDuplicateRequestsPreFlightCheck(
			mock(PatronRequestRepository.class), new IntMessageService(), new StaffClients(List.of("dcb-admin")),
			Duration.ZERO, null));
	}

	private List<CheckResult> check(PlacePatronRequestCommand command) {
		return singleValueFrom(check.check(command));
	}

	private static Authentication tokenFor(String clientId) {
		return Authentication.build("caller", List.of(ADMINISTRATOR), Map.of("azp", clientId));
	}

	private PlacePatronRequestCommand command(String patronLocalId, UUID bibClusterId) {
		return placeRequestCommand("pickup-location", SYSTEM, patronLocalId, bibClusterId);
	}

	private PlacePatronRequestCommand volumeCommand(String volumeDesignator) {
		return command(PATRON, title).toBuilder()
			.citation(PlacePatronRequestCommand.Citation.builder()
				.bibClusterId(title)
				.volumeDesignator(volumeDesignator)
				.build())
			.build();
	}

	private PlacePatronRequestCommand chosenCopyCommand(String localId, String agencyCode) {
		return command(PATRON, title).toBuilder()
			.item(PlacePatronRequestCommand.Item.builder()
				.localId(localId)
				.localSystemCode(SUPPLYING_SYSTEM)
				.agencyCode(agencyCode)
				.build())
			.build();
	}

	private void supplying(PatronRequest request, String localItemId, boolean isActive) {
		supplierRequestsFixture.saveSupplierRequest(UUID.randomUUID(), request, "supplier-bib", localItemId,
			"supplier-location", "supplier-barcode", SUPPLYING_SYSTEM, isActive, "supplier-location");
	}

	private PatronRequest earlierRequest(PatronRequest.Status status,
		UnaryOperator<PatronRequest.PatronRequestBuilder> details) {

		final var hostLms = hostLmsFixture.createSierraHostLms(SYSTEM);
		final var patron = patronFixture.savePatron("home-library");
		final var identity = patronFixture.saveIdentityAndReturn(patron, hostLms, PATRON, true, "-", SYSTEM, null);

		return patronRequestsFixture.savePatronRequest(details.apply(PatronRequest.builder()
				.id(UUID.randomUUID())
				.patron(patron)
				.patronHostlmsCode(SYSTEM)
				.requestingIdentity(identity)
				.bibClusterId(title)
				.status(status))
			.build());
	}
}
