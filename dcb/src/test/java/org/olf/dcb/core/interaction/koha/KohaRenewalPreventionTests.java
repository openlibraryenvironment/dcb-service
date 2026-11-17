package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.PreventRenewalCommand;
import org.olf.dcb.core.interaction.koha.dto.KohaCheckout;
import org.olf.dcb.core.interaction.koha.dto.KohaItem;
import org.olf.dcb.core.interaction.koha.dto.KohaRenewability;
import org.olf.dcb.test.HostLmsFixture;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * Preventing renewal of a Koha virtual item once the owning library has a hold.
 * <p>
 * Koha decides renewability from circulation rules and system preferences DCB cannot see, so what
 * matters here is that DCB sends a change Koha will accept, and then believes Koha's answer about
 * whether the renewal is actually blocked, rather than its own.
 */
@MockServerMicronautTest
class KohaRenewalPreventionTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-renewal";
	private static final String KOHA_HOST = "fake-koha-renewal";
	private static final String ITEM_ID = "99";
	private static final String BIBLIO_ID = "42";
	private static final String CHECKOUT_ID = "7";

	@Inject
	private HostLmsFixture hostLmsFixture;

	private MockKohaFixture koha;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		hostLmsFixture.deleteAll();

		koha = new MockKohaFixture(mockServerClient, KOHA_HOST);
	}

	@Test
	void shouldMarkTheItemAsDeniedRenewalAndTellStaffWhy() {
		givenVirtualItem(virtualItem().internalNotes("Virtual item = created by DCB").build());
		givenTheLoanIsNotRenewable();

		preventRenewal();

		final var update = koha.itemUpdateSent(BIBLIO_ID, ITEM_ID);

		assertThat("The marker is what ItemsDeniedRenewal matches on",
			update, hasEntry("collection_code", "DCB_NO_RENEW"));

		assertThat("Staff are told why the item cannot be renewed",
			update, hasEntry("internal_notes",
				"Virtual item = created by DCB | Hold placed by owning library. Do not renew."));
	}

	@Test
	void shouldSendOnlyTheFieldsBeingChanged() {
		// Koha has no item PATCH, so an update is a PUT of the whole item. Echoing back the item
		// as read overwrites anything changed in Koha since, and sends computed fields such as
		// effective_item_type_id that the update rejects because they are not item columns.
		givenVirtualItem(virtualItem()
			.effectiveItemTypeId("BK")
			.effectiveNotForLoanStatus(0)
			.homeLibraryId("BRANCH-NORTH")
			.build());

		givenTheLoanIsNotRenewable();

		preventRenewal();

		final var update = koha.itemUpdateSent(BIBLIO_ID, ITEM_ID);

		assertThat("Computed fields must not be echoed back", update, not(hasKey("effective_item_type_id")));
		assertThat(update, not(hasKey("effective_not_for_loan_status")));
		assertThat("Identifiers belong in the path, not the body", update, not(hasKey("item_id")));
		assertThat("Unchanged fields must not be rewritten from a stale read",
			update, not(hasKey("home_library_id")));

		// Not for loan blocks checkout rather than renewal, and DCB reads it as MISSING, which
		// would strand the request in LOANED once the patron returned the item
		assertThat("Not for loan is not how renewal is prevented", update, not(hasKey("not_for_loan_status")));
	}

	@Test
	void shouldFailWhenKohaStillAllowsTheLoanToBeRenewed() {
		// The marker only denies renewal if this Koha lists it in ItemsDeniedRenewal, which is
		// deployment state DCB cannot see. Reporting success without checking would leave the
		// owning library expecting a return that never comes.
		givenVirtualItem(virtualItem().build());
		givenTheLoanIsRenewable();

		final var problem = assertThrows(ThrowableProblem.class, this::preventRenewal);

		assertThat(problem.getMessage(), containsString("still allows this loan to be renewed"));

		assertThat("The message has to name the configuration the library is missing",
			problem.getMessage(), containsString("ItemsDeniedRenewal"));

		assertThat(problem.getMessage(), containsString("ccode: [DCB_NO_RENEW]"));
	}

	@Test
	void shouldTreatAnUnansweredRenewabilityCheckAsAFailure() {
		// Fail closed: a missing answer is not a denial
		givenVirtualItem(virtualItem().build());
		givenUpdateSucceeds();
		givenCheckout();
		koha.mockAllowsRenewal(CHECKOUT_ID, KohaRenewability.builder().build());

		assertThrows(ThrowableProblem.class, this::preventRenewal);
	}

	@Test
	void shouldNotGrowTheNoteEachTimeRenewalPreventionRuns() {
		// Renewal prevention can fire more than once for the same request
		givenVirtualItem(virtualItem()
			.internalNotes("Virtual item = created by DCB | Hold placed by owning library. Do not renew.")
			.build());

		givenTheLoanIsNotRenewable();

		preventRenewal();

		assertThat(koha.itemUpdateSent(BIBLIO_ID, ITEM_ID), hasEntry("internal_notes",
			"Virtual item = created by DCB | Hold placed by owning library. Do not renew."));
	}

	@Test
	void shouldRefuseToWriteToAnItemDcbDidNotCreate() {
		givenVirtualItem(KohaItem.builder()
			.itemId(99L)
			.biblioId(42L)
			.callnumber("823.912 JOY")
			.build());

		final var problem = assertThrows(ThrowableProblem.class, this::preventRenewal);

		assertThat(problem.getMessage(), containsString("item DCB did not create"));

		koha.verifyItemNeverUpdated(BIBLIO_ID, ITEM_ID);
	}

	@Test
	void shouldCompleteWhenTheItemIsNoLongerOnLoan() {
		// Nothing to renew, so nothing to prevent - but the marker is left in place
		givenVirtualItem(virtualItem().build());
		givenUpdateSucceeds();
		koha.mockCheckoutsForItem(ITEM_ID);

		preventRenewal();
	}

	@Test
	void shouldUseTheCollectionCodeThisKohaWasConfiguredWith() {
		// The code has to match the library's ItemsDeniedRenewal rule, whatever they chose
		givenVirtualItem(virtualItem().build());
		givenTheLoanIsNotRenewable();

		clientWith(Map.of("no-renew-collection-code", "NO_RENEW_ILL"))
			.preventRenewalOnLoan(PreventRenewalCommand.builder().itemId(ITEM_ID).build())
			.block();

		assertThat(koha.itemUpdateSent(BIBLIO_ID, ITEM_ID), hasEntry("collection_code", "NO_RENEW_ILL"));
	}

	@Test
	void shouldTellStaffInTheWordsThisLibraryConfigured() {
		givenVirtualItem(virtualItem().internalNotes("Créé par le réseau").build());
		givenTheLoanIsNotRenewable();

		clientWith(Map.of("do-not-renew-note", "Réservé par la bibliothèque. Ne pas renouveler."))
			.preventRenewalOnLoan(PreventRenewalCommand.builder().itemId(ITEM_ID).build())
			.block();

		assertThat(koha.itemUpdateSent(BIBLIO_ID, ITEM_ID), hasEntry("internal_notes",
			"Créé par le réseau | Réservé par la bibliothèque. Ne pas renouveler."));
	}

	private void preventRenewal() {
		clientWith(Map.of())
			.preventRenewalOnLoan(PreventRenewalCommand.builder().itemId(ITEM_ID).build())
			.block();
	}

	private static KohaItem.KohaItemBuilder virtualItem() {
		return KohaItem.builder()
			.itemId(99L)
			.biblioId(42L)
			.callnumber("DCB_VIRTUAL_COLLECTION");
	}

	private void givenVirtualItem(KohaItem kohaItem) {
		koha.mockItem(kohaItem);
	}

	private void givenUpdateSucceeds() {
		koha.mockUpdateItem(BIBLIO_ID, ITEM_ID, KohaItem.builder().itemId(99L).build());
	}

	private void givenCheckout() {
		koha.mockCheckoutsForItem(ITEM_ID, KohaCheckout.builder()
			.checkoutId(Long.valueOf(CHECKOUT_ID))
			.itemId(99L)
			.build());
	}

	private void givenTheLoanIsNotRenewable() {
		givenUpdateSucceeds();
		givenCheckout();
		koha.mockAllowsRenewal(CHECKOUT_ID, KohaRenewability.builder()
			.allowsRenewal(false)
			.error("item_denied_renewal")
			.build());
	}

	private void givenTheLoanIsRenewable() {
		givenUpdateSucceeds();
		givenCheckout();
		koha.mockAllowsRenewal(CHECKOUT_ID, KohaRenewability.builder()
			.allowsRenewal(true)
			.maxRenewals(3)
			.currentRenewals(0)
			.build());
	}

	private HostLmsClient clientWith(Map<String, Object> extraConfig) {
		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST, extraConfig);

		return hostLmsFixture.createClient(KOHA_HOST_LMS_CODE);
	}
}
