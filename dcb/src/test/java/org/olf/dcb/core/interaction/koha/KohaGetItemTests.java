package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_AVAILABLE;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_LOANED;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_MISSING;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_ON_HOLDSHELF;
import static org.olf.dcb.core.interaction.HostLmsItem.ITEM_TRANSIT;
import static org.olf.dcb.core.interaction.HostLmsItem.LIBRARY_USE_ONLY;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.koha.dto.KohaCheckout;
import org.olf.dcb.core.interaction.koha.dto.KohaHoldResponse;
import org.olf.dcb.core.interaction.koha.dto.KohaItem;
import org.olf.dcb.core.interaction.koha.dto.KohaTransfer;
import org.olf.dcb.test.HostLmsFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * How a Koha item's state reaches the tracker.
 * <p>
 * Koha has no item status column: lost and withdrawn are flags on the item, and on loan,
 * in transit and on the hold shelf exist only as a checkout, a transfer and a waiting hold.
 * The tracker's transitions fire on LOANED, TRANSIT, HOLDSHELF and AVAILABLE, so each
 * has to be read from the object that carries it.
 */
@MockServerMicronautTest
class KohaGetItemTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-host-lms";
	private static final String KOHA_HOST = "fake-koha";
	private static final String BIBLIO_ID = "4001";
	private static final String ITEM_ID = "55";

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
	void shouldReportAnItemOnTheShelfAsAvailable() {
		mockKohaFixture.mockItemCirculation(BIBLIO_ID, ITEM_ID, item().build());

		final var item = getItem();

		assertThat(item.getStatus(), is(ITEM_AVAILABLE));
		assertThat(item.getBarcode(), is("item-barcode"));
		assertThat(item.getBibId(), is(BIBLIO_ID));
		assertThat(item.getDueDate(), is(nullValue()));

		mockKohaFixture.verifyItemCirculationRead(BIBLIO_ID, ITEM_ID);
	}

	@Test
	void shouldReportAnItemOnLoanWithItsDueDateAndRenewals() {
		// SupplierRenewalTransition fires when the borrower's renewal count rises, and the
		// count lives on the checkout since Koha 23.11, not on the item
		mockKohaFixture.mockItemCirculation(BIBLIO_ID, ITEM_ID, item()
			.checkedOutDate("2026-10-01")
			.checkout(KohaCheckout.builder()
				.checkoutId(7L)
				.dueDate("2026-10-29T23:59:00Z")
				.renewalsCount(2)
				.build())
			.build());

		final var item = getItem();

		assertThat(item.getStatus(), is(ITEM_LOANED));
		assertThat(item.getDueDate(), is(Instant.parse("2026-10-29T23:59:00Z")));
		assertThat(item.getRenewalCount(), is(2));
	}

	@Test
	void shouldNotCountKohasLifetimeHoldTallyAsAHold() {
		// holds_count is how many times the item has ever been held, which the virtual
		// patron's own hold is part of; counting it stopped renewals on every Koha loan
		mockKohaFixture.mockItemCirculation(BIBLIO_ID, ITEM_ID, item()
			.holdCount(3)
			.checkedOutDate("2026-10-01")
			.checkout(KohaCheckout.builder().checkoutId(7L).dueDate("2026-10-29T23:59:00Z").build())
			.build());

		assertThat(getItem().getHoldCount(), is(0));
	}

	@Test
	void shouldCountAFirstHoldOnALentItemAsSomeoneWaiting() {
		mockKohaFixture.mockItemCirculation(BIBLIO_ID, ITEM_ID, item()
			.holdCount(3)
			.checkedOutDate("2026-10-01")
			.checkout(KohaCheckout.builder().checkoutId(7L).dueDate("2026-10-29T23:59:00Z").build())
			.firstHold(KohaHoldResponse.builder().holdId(602L).patronId(13L).build())
			.build());

		assertThat(getItem().getHoldCount(), is(1));
	}

	@Test
	void shouldReportAnItemSentToAnotherBranchAsInTransit() {
		mockKohaFixture.mockItemCirculation(BIBLIO_ID, ITEM_ID, item()
			.transfer(KohaTransfer.builder()
				.fromLibraryId("BRANCH-N")
				.toLibraryId("DCB-SHARING")
				.dateSent("2026-10-20T09:00:00Z")
				.build())
			.build());

		assertThat(getItem().getStatus(), is(ITEM_TRANSIT));
	}

	@Test
	void shouldNotReportAnArrivedTransferAsInTransit() {
		mockKohaFixture.mockItemCirculation(BIBLIO_ID, ITEM_ID, item()
			.transfer(KohaTransfer.builder()
				.dateSent("2026-10-20T09:00:00Z")
				.dateArrived("2026-10-21T09:00:00Z")
				.build())
			.build());

		assertThat(getItem().getStatus(), is(ITEM_AVAILABLE));
	}

	@Test
	void shouldNotReportARequestedButUnsentTransferAsInTransit() {
		mockKohaFixture.mockItemCirculation(BIBLIO_ID, ITEM_ID, item()
			.transfer(KohaTransfer.builder().toLibraryId("DCB-SHARING").build())
			.build());

		assertThat(getItem().getStatus(), is(ITEM_AVAILABLE));
	}

	@Test
	void shouldReportAnItemWithAWaitingHoldAsOnTheHoldShelf() {
		// HandleBorrowerItemOnHoldShelf waits for this: staff checked the virtual item in
		// at the pickup branch and Koha trapped the patron's hold
		mockKohaFixture.mockItemCirculation(BIBLIO_ID, ITEM_ID, item()
			.firstHold(KohaHoldResponse.builder().holdId(77L).status("W").build())
			.build());

		assertThat(getItem().getStatus(), is(ITEM_ON_HOLDSHELF));
	}

	@Test
	void shouldReportALostItemAsMissing() {
		mockKohaFixture.mockItemCirculation(BIBLIO_ID, ITEM_ID, item().lostStatus(1).build());

		assertThat(getItem().getStatus(), is(ITEM_MISSING));
	}

	@Test
	void shouldReportAWithdrawnItemAsMissing() {
		mockKohaFixture.mockItemCirculation(BIBLIO_ID, ITEM_ID, item().withdrawn(1).build());

		assertThat(getItem().getStatus(), is(ITEM_MISSING));
	}

	@Test
	void shouldReportANotForLoanItemAsLibraryUseOnlyRatherThanMissing() {
		// MISSING would strand a request in LOANED once the patron returned the item, as
		// the renewal-prevention path found
		mockKohaFixture.mockItemCirculation(BIBLIO_ID, ITEM_ID, item()
			.effectiveNotForLoanStatus(1)
			.build());

		assertThat(getItem().getStatus(), is(LIBRARY_USE_ONLY));
	}

	@Test
	void shouldReportAnItemKohaNoLongerHasAsMissing() {
		mockKohaFixture.mockItemCirculation(BIBLIO_ID, ITEM_ID);

		final var item = getItem();

		assertThat(item.getStatus(), is(ITEM_MISSING));
		assertThat(item.getLocalId(), is(ITEM_ID));
	}

	@Test
	void shouldFindTheBiblioWhenTheCallerDoesNotKnowIt() {
		mockKohaFixture.mockItem(item().build());
		mockKohaFixture.mockItemCirculation(BIBLIO_ID, ITEM_ID, item()
			.checkout(KohaCheckout.builder().dueDate("2026-10-29T23:59:00Z").build())
			.build());

		final var item = client.getItem(HostLmsItem.builder().localId(ITEM_ID).build()).block();

		assertThat(item.getStatus(), is(ITEM_LOANED));
		mockKohaFixture.verifyItemCirculationRead(BIBLIO_ID, ITEM_ID);
	}

	@Test
	void shouldReadADueDateCarryingAnOffset() {
		// Koha renders dates in the library's zone; the instant is what the tracker compares
		mockKohaFixture.mockItemCirculation(BIBLIO_ID, ITEM_ID, item()
			.checkout(KohaCheckout.builder().dueDate("2026-10-29T23:59:00+01:00").build())
			.build());

		final var item = getItem();

		assertThat(item.getStatus(), is(ITEM_LOANED));
		assertThat(item.getDueDate(), is(Instant.parse("2026-10-29T22:59:00Z")));
	}

	private HostLmsItem getItem() {
		return client.getItem(HostLmsItem.builder()
				.localId(ITEM_ID)
				.bibId(BIBLIO_ID)
				.build())
			.block();
	}

	private static KohaItem.KohaItemBuilder item() {
		return KohaItem.builder()
			.itemId(Long.valueOf(ITEM_ID))
			.biblioId(Long.valueOf(BIBLIO_ID))
			.externalId("item-barcode")
			.homeLibraryId("BRANCH-N")
			.holdingLibraryId("BRANCH-N")
			.notForLoanStatus(0)
			.effectiveNotForLoanStatus(0)
			.lostStatus(0)
			.withdrawn(0);
	}
}
