package org.olf.dcb.core.interaction.koha;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.olf.dcb.core.interaction.PreventRenewalCommand;
import org.olf.dcb.core.interaction.folio.MaterialTypeToItemTypeMappingService;
import org.olf.dcb.core.interaction.koha.dto.KohaCheckout;
import org.olf.dcb.core.interaction.koha.dto.KohaItem;
import org.olf.dcb.core.interaction.koha.dto.KohaRenewability;
import org.olf.dcb.core.model.HostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.svc.LocationToAgencyMappingService;
import org.olf.dcb.core.svc.ReferenceValueMappingService;
import org.zalando.problem.ThrowableProblem;

import reactor.core.publisher.Mono;

/**
 * Preventing renewal of a Koha virtual item once the owning library has a hold.
 * <p>
 * Koha decides renewability from circulation rules and system preferences DCB cannot see, so what
 * matters here is that DCB sends a change Koha will accept, and then believes Koha's answer about
 * whether the renewal is actually blocked, rather than its own.
 */
@TestInstance(PER_CLASS)
class KohaRenewalPreventionTests {
	private static final String ITEM_ID = "99";
	private static final String BIBLIO_ID = "42";
	private static final String CHECKOUT_ID = "7";

	private KohaApiClient apiClient;

	@BeforeEach
	void beforeEach() {
		apiClient = mock(KohaApiClient.class);
	}

	@Test
	void shouldMarkTheItemAsDeniedRenewalAndTellStaffWhy() {
		givenVirtualItem(virtualItem().internalNotes("Virtual item = created by DCB").build());
		givenTheLoanIsNotRenewable();

		final var update = whenRenewalIsPrevented();

		assertThat("The marker is what ItemsDeniedRenewal matches on",
			update.getCollectionCode(), is("DCB_NO_RENEW"));

		assertThat("Staff are told why the item cannot be renewed",
			update.getInternalNotes(),
			is("Virtual item = created by DCB | Hold placed by owning library. Do not renew."));
	}

	@Test
	void shouldSendOnlyTheFieldsBeingChanged() {
		// Koha has no item PATCH, so an update is a PUT of the whole item. Echoing back the item
		// as read overwrites anything changed in Koha since, and sends computed fields such as
		// effective_item_type_id that the update rejects because they are not item columns.
		givenVirtualItem(virtualItem()
			.itemId(99L)
			.effectiveItemTypeId("BK")
			.effectiveNotForLoanStatus(0)
			.homeLibraryId("BRANCH-NORTH")
			.build());

		givenTheLoanIsNotRenewable();

		final var update = whenRenewalIsPrevented();

		assertThat("Computed fields must not be echoed back",
			update.getEffectiveItemTypeId(), is(nullValue()));
		assertThat(update.getEffectiveNotForLoanStatus(), is(nullValue()));
		assertThat("Identifiers belong in the path, not the body",
			update.getItemId(), is(nullValue()));
		assertThat("Unchanged fields must not be rewritten from a stale read",
			update.getHomeLibraryId(), is(nullValue()));

		// Not for loan blocks checkout rather than renewal, and DCB reads it as MISSING, which
		// would strand the request in LOANED once the patron returned the item
		assertThat("Not for loan is not how renewal is prevented",
			update.getNotForLoanStatus(), is(nullValue()));
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

		when(apiClient.allowsRenewal(CHECKOUT_ID))
			.thenReturn(Mono.just(KohaRenewability.builder().build()));

		assertThrows(ThrowableProblem.class, this::preventRenewal);
	}

	@Test
	void shouldNotGrowTheNoteEachTimeRenewalPreventionRuns() {
		// Renewal prevention can fire more than once for the same request
		givenVirtualItem(virtualItem()
			.internalNotes("Virtual item = created by DCB | Hold placed by owning library. Do not renew.")
			.build());

		givenTheLoanIsNotRenewable();

		final var update = whenRenewalIsPrevented();

		assertThat(update.getInternalNotes(),
			is("Virtual item = created by DCB | Hold placed by owning library. Do not renew."));
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

		verify(apiClient, never()).updateItem(any(), any(), any());
	}

	@Test
	void shouldCompleteWhenTheItemIsNoLongerOnLoan() {
		// Nothing to renew, so nothing to prevent - but the marker is left in place
		givenVirtualItem(virtualItem().build());

		givenUpdateSucceeds();

		when(apiClient.getCheckoutsForItem(ITEM_ID))
			.thenReturn(Mono.just(new KohaCheckout[0]));

		preventRenewal();
	}

	@Test
	void shouldUseTheCollectionCodeThisKohaWasConfiguredWith() {
		// The code has to match the library's ItemsDeniedRenewal rule, whatever they chose
		givenVirtualItem(virtualItem().build());
		givenTheLoanIsNotRenewable();

		final var captor = ArgumentCaptor.forClass(KohaItem.class);

		when(apiClient.updateItem(eq(BIBLIO_ID), eq(ITEM_ID), captor.capture()))
			.thenReturn(Mono.just(KohaItem.builder().itemId(99L).build()));

		clientWith(Map.of("no-renew-collection-code", "NO_RENEW_ILL"))
			.preventRenewalOnLoan(PreventRenewalCommand.builder().itemId(ITEM_ID).build())
			.block();

		assertThat(captor.getValue().getCollectionCode(), is("NO_RENEW_ILL"));
	}

	private KohaItem whenRenewalIsPrevented() {
		final var captor = ArgumentCaptor.forClass(KohaItem.class);

		when(apiClient.updateItem(eq(BIBLIO_ID), eq(ITEM_ID), captor.capture()))
			.thenReturn(Mono.just(KohaItem.builder().itemId(99L).build()));

		preventRenewal();

		return captor.getValue();
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
		when(apiClient.getItem(ITEM_ID)).thenReturn(Mono.just(kohaItem));
	}

	private void givenUpdateSucceeds() {
		when(apiClient.updateItem(eq(BIBLIO_ID), eq(ITEM_ID), any()))
			.thenReturn(Mono.just(KohaItem.builder().itemId(99L).build()));
	}

	private void givenCheckout() {
		when(apiClient.getCheckoutsForItem(ITEM_ID))
			.thenReturn(Mono.just(new KohaCheckout[] {
				KohaCheckout.builder()
					.checkoutId(Long.valueOf(CHECKOUT_ID))
					.itemId(99L)
					.build()
			}));
	}

	private void givenTheLoanIsNotRenewable() {
		givenCheckout();

		when(apiClient.allowsRenewal(CHECKOUT_ID))
			.thenReturn(Mono.just(KohaRenewability.builder()
				.allowsRenewal(false)
				.error("item_denied_renewal")
				.build()));
	}

	private void givenTheLoanIsRenewable() {
		givenUpdateSucceeds();
		givenCheckout();

		when(apiClient.allowsRenewal(CHECKOUT_ID))
			.thenReturn(Mono.just(KohaRenewability.builder()
				.allowsRenewal(true)
				.maxRenewals(3)
				.currentRenewals(0)
				.build()));
	}

	private KohaHostLmsClient clientWith(Map<String, Object> extraConfig) {
		final var clientConfig = new HashMap<String, Object>(Map.of(
			"api-url", "https://koha.example.com",
			"client_id", "any-id",
			"client_secret", "any-secret",
			"sharing-library-code", "DCB-SHARING",
			"virtual-item-library-code", "DCB-VIRTUAL"));

		clientConfig.putAll(extraConfig);

		final var hostLms = mock(HostLms.class);
		when(hostLms.getCode()).thenReturn("KOHA");
		when(hostLms.getClientConfig()).thenReturn(clientConfig);

		final var clientFactory = mock(KohaClientFactory.class);
		when(clientFactory.createClientFor(hostLms)).thenReturn(apiClient);

		final var locationToAgency = mock(LocationToAgencyMappingService.class);
		when(locationToAgency.enrichItemAgencyFromLocation(any(), any()))
			.thenAnswer(invocation -> Mono.just(invocation.<Item>getArgument(0)));

		final var materialTypeToItemType = mock(MaterialTypeToItemTypeMappingService.class);

		return new KohaHostLmsClient(hostLms, mock(ReferenceValueMappingService.class),
			clientFactory, materialTypeToItemType, locationToAgency);
	}
}
