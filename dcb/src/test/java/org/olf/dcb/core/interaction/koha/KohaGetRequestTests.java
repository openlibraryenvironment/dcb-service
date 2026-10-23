package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CANCELLED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_CONFIRMED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_MISSING;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_PLACED;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_READY;
import static org.olf.dcb.core.interaction.HostLmsRequest.HOLD_TRANSIT;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsRequest;
import org.olf.dcb.core.interaction.koha.dto.KohaHoldResponse;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import reactor.test.StepVerifier;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * How a Koha hold's state reaches the tracker.
 * <p>
 * Koha keeps a live hold in reserves and moves it to old_reserves the moment it is
 * filled or cancelled, after which the single-hold read answers 404. The tracker's
 * transitions fire on CONFIRMED, TRANSIT, READY, CANCELLED and MISSING, so each of
 * Koha's states, including the two it no longer lists, has to come back as one of them.
 */
@MockServerMicronautTest
class KohaGetRequestTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-host-lms";
	private static final String KOHA_HOST = "fake-koha";
	private static final String HOLD_ID = "77";

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockKohaFixture mockKohaFixture;
	private HostLmsClient client;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST);
		mockKohaFixture = new MockKohaFixture(mockServerClient, KOHA_HOST);
		client = hostLmsFixture.createClient(KOHA_HOST_LMS_CODE);
	}

	@Test
	void shouldReportATitleLevelHoldAsPlacedUntilKohaChoosesAnItem() {
		mockKohaFixture.mockHold(hold().itemId(null).status(null).build());

		final var request = getRequest();

		assertThat(request.getStatus(), is(HOLD_PLACED));
		assertThat(request.getRequestedItemId(), is(nullValue()));
	}

	@Test
	void shouldReportAnItemLevelHoldAsConfirmed() {
		// HandleSupplierRequestConfirmed waits for CONFIRMED or TRANSIT; a hold DCB placed on
		// one item has its item from the start, and must not sit at PLACED forever
		mockKohaFixture.mockHold(hold().status(null).build());

		final var request = getRequest();

		assertThat(request.getStatus(), is(HOLD_CONFIRMED));
		assertThat(request.getRequestedItemId(), is("55"));
		assertThat(request.getRawStatus(), is(nullValue()));
	}

	@Test
	void shouldReportAHoldInTransitToThePickupBranch() {
		mockKohaFixture.mockHold(hold().status("T").build());

		assertThat(getRequest().getStatus(), is(HOLD_TRANSIT));
	}

	@Test
	void shouldReportAHoldInProcessingAsStillInTransit() {
		// P is the step between arrival and the shelf: not yet collectable
		mockKohaFixture.mockHold(hold().status("P").build());

		assertThat(getRequest().getStatus(), is(HOLD_TRANSIT));
	}

	@Test
	void shouldReportAWaitingHoldAsReady() {
		mockKohaFixture.mockHold(hold().status("W").build());

		final var request = getRequest();

		assertThat(request.getStatus(), is(HOLD_READY));
		assertThat(request.getRawStatus(), is("W"));
	}

	@Test
	void shouldReportAFilledHoldAsMissing() {
		// Filled, so moved to old_reserves with found = F and no cancellation date
		mockKohaFixture.mockHoldNotFound(HOLD_ID);
		mockKohaFixture.mockOldHold(HOLD_ID, hold().status("F").build());

		final var request = getRequest();

		assertThat(request.getStatus(), is(HOLD_MISSING));
		assertThat(request.getRawStatus(), is("F"));
		assertThat(request.getRequestedItemId(), is("55"));
	}

	@Test
	void shouldReportACancelledHoldAsCancelled() {
		mockKohaFixture.mockHoldNotFound(HOLD_ID);
		mockKohaFixture.mockOldHold(HOLD_ID, hold()
			.status(null)
			.cancellationDate("2026-10-20")
			.cancellationReason("PATRON")
			.build());

		final var request = getRequest();

		assertThat(request.getStatus(), is(HOLD_CANCELLED));
		assertThat(request.getLocalId(), is(HOLD_ID));
	}

	@Test
	void shouldReportAHoldKohaHasNoRecordOfAsMissing() {
		mockKohaFixture.mockHoldNotFound(HOLD_ID);
		mockKohaFixture.mockOldHold(HOLD_ID);

		final var request = getRequest();

		assertThat(request.getStatus(), is(HOLD_MISSING));
		assertThat(request.getLocalId(), is(HOLD_ID));
	}

	@Test
	void shouldNotMistakeAKohaFailureForAMissingHold() {
		// A hold reported MISSING sends the workflow looking for another supplier, so an
		// outage must surface as an error, not as the hold having gone
		mockKohaFixture.mockHoldServerError(HOLD_ID);

		StepVerifier.create(client.getRequest(HostLmsRequest.builder().localId(HOLD_ID).build()))
			.expectError()
			.verify();

		mockKohaFixture.verifyOldHoldNeverRead(HOLD_ID);
	}

	private HostLmsRequest getRequest() {
		return client.getRequest(HostLmsRequest.builder()
				.localId(HOLD_ID)
				.build())
			.block();
	}

	private static KohaHoldResponse.KohaHoldResponseBuilder hold() {
		return KohaHoldResponse.builder()
			.holdId(Long.valueOf(HOLD_ID))
			.patronId(12L)
			.biblioId(4001L)
			.itemId(55L)
			.pickupLibraryId("DCB-SHARING")
			.itemLevel(true);
	}
}
