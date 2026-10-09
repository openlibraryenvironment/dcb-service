package org.olf.dcb.request.fulfilment;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.olf.dcb.core.model.PatronRequest.Status.CONFIRMED;
import static org.olf.dcb.core.model.PatronRequest.Status.ERROR;
import static org.olf.dcb.core.model.PatronRequest.Status.LOANED;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.List;
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

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

@Property(name = "dcb.requests.preflight-checks.duplicate-requests.enabled", value = "true")
@Property(name = "dcb.requests.staff-client-ids", value = "dcb-admin,dcb-admin-for-libraries")
@DcbTest
class PreventDuplicateRequestsPreflightCheckTests extends AbstractPreflightCheckTests {
	private static final String SYSTEM = "duplicate-check-system";
	private static final String PATRON = "duplicate-check-patron";

	@Inject
	private PreventDuplicateRequestsPreFlightCheck check;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;
	@Inject
	private PatronFixture patronFixture;
	@Inject
	private HostLmsFixture hostLmsFixture;

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
	void shouldPassForTheSameVolumeOfTheTitle() {
		earlierRequest(CONFIRMED, request -> request.requestedVolumeDesignation("v1"));

		assertThat(check(volumeCommand("v1")), containsInAnyOrder(passedCheck()));
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

		final var chosenCopy = command(PATRON, title).toBuilder()
			.item(PlacePatronRequestCommand.Item.builder()
				.localId("chosen-copy")
				.localSystemCode("supplying-system")
				.agencyCode("supplying-agency")
				.build())
			.build();

		assertThat(check(chosenCopy), containsInAnyOrder(passedCheck()));
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
	void shouldPassEverythingWhenNoStaffClientIsConfigured() {
		final var repository = mock(PatronRequestRepository.class);

		final var unarmed = new PreventDuplicateRequestsPreFlightCheck(repository, new IntMessageService(),
			new StaffClients(List.of()));

		assertThat(singleValueFrom(unarmed.check(command(PATRON, title))), containsInAnyOrder(passedCheck()));

		verifyNoInteractions(repository);
	}

	@Test
	void shouldPassWhenThePatronsRequestsCannotBeRead() {
		final var repository = mock(PatronRequestRepository.class);
		when(repository.findActiveRequestForPatronAndTitle(any(), any(), any(), any(), any()))
			.thenReturn(Mono.error(new RuntimeException("database unavailable")));

		final var overUnreadableRequests = new PreventDuplicateRequestsPreFlightCheck(repository,
			new IntMessageService(), new StaffClients(List.of("dcb-admin")));

		assertThat(singleValueFrom(overUnreadableRequests.check(command(PATRON, title))),
			containsInAnyOrder(passedCheck()));
	}

	private List<CheckResult> check(PlacePatronRequestCommand command) {
		return singleValueFrom(check.check(command));
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

	private void earlierRequest(PatronRequest.Status status,
		UnaryOperator<PatronRequest.PatronRequestBuilder> details) {

		final var hostLms = hostLmsFixture.createSierraHostLms(SYSTEM);
		final var patron = patronFixture.savePatron("home-library");
		final var identity = patronFixture.saveIdentityAndReturn(patron, hostLms, PATRON, true, "-", SYSTEM, null);

		patronRequestsFixture.savePatronRequest(details.apply(PatronRequest.builder()
				.id(UUID.randomUUID())
				.patron(patron)
				.patronHostlmsCode(SYSTEM)
				.requestingIdentity(identity)
				.bibClusterId(title)
				.status(status))
			.build());
	}
}
