package org.olf.dcb.core.interaction.koha;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.olf.dcb.test.MockServerCommonResponses.okJson;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.HostLmsItem;
import org.olf.dcb.core.interaction.Patron;
import org.olf.dcb.core.interaction.koha.dto.KohaItem;
import org.olf.dcb.core.interaction.koha.dto.KohaPatron;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.PatronIdentity;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * How Koha records become DCB records.
 * <p>
 * On a shared Koha the branch is the only thing that tells one participating
 * library from another, so anything that loses it collapses every co-tenant onto
 * one agency.
 */
@MockServerMicronautTest
class KohaMappingTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-mapping";
	private static final String KOHA_HOST = "fake-koha-mapping";
	private static final String BIBLIO_ID = "42";

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private AgencyFixture agencyFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;

	private MockKohaFixture koha;
	private HostLmsClient client;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();
		agencyFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST);
		koha = new MockKohaFixture(mockServerClient, KOHA_HOST);
		client = hostLmsFixture.createClient(KOHA_HOST_LMS_CODE);
	}

	@Test
	void shouldMapThePatronsHomeLibrary() {
		// library_id was modelled and used when creating a patron, but never read back.
		// Every Koha patron therefore arrived with no home library code, fell through to
		// the default agency, and on a 60-library Koha every one of them belonged to
		// whichever library happened to be configured there.
		koha.mockPatron(patron().libraryId("BRANCH-NORTH").build());

		final var patron = client.getPatronByLocalId("1234").block();

		assertThat(patron.getLocalHomeLibraryCode(), is("BRANCH-NORTH"));
	}

	@Test
	void shouldReadAPatronWithNoFirstName() {
		koha.mockPatron(patron().firstname(null).surname("Lovelace").build());

		final var patron = client.getPatronByLocalId("1234").block();

		assertThat(patron.getLocalNames(), contains("Lovelace"));
	}

	@Test
	void shouldTolerateAPatronWithNoHomeLibrary() {
		koha.mockPatron(patron().build());

		final var patron = client.getPatronByLocalId("1234").block();

		assertThat(patron.getLocalHomeLibraryCode(), is(nullValue()));
		assertThat(patron.getLocalPatronType(), is("ADULT"));
	}

	@Test
	void shouldReportAnItemKohaHasCheckedOutAsLoaned() {
		final var onLoan = KohaItem.builder()
			.itemId(99L)
			.biblioId(42L)
			.externalId("ITEM-BARCODE")
			.checkedOutDate("2026-09-01T10:00:00Z")
			.build();

		koha.mockItemByBarcode(onLoan);
		koha.mockItem(onLoan);
		koha.mockItemCirculation(BIBLIO_ID, "99", onLoan);

		// Koha leaves every status field at "available" while an item is on loan
		assertThat(client.getItemByBarcode("ITEM-BARCODE").block().getStatus(), is(HostLmsItem.ITEM_LOANED));
		assertThat(client.getItem(HostLmsItem.builder().localId("99").build()).block().getStatus(),
			is(HostLmsItem.ITEM_LOANED));
	}

	@Test
	void shouldMapTheOwningBranchAsTheItemLocation() {
		// Koha's "location" is a shelving classifier that every branch shares, so using
		// it as the location made location-to-agency mapping impossible on a shared
		// server: either no branch could be identified, or all of them mapped to one.
		givenItem(item()
			.homeLibraryId("BRANCH-NORTH")
			.holdingLibraryId("BRANCH-SOUTH")
			.location("STACKS")
			.build());

		final var item = firstItem();

		assertThat("Location is the owning branch", item.getLocationCode(), is("BRANCH-NORTH"));
		assertThat("Koha's location is the shelving location",
			item.getShelvingLocation(), is("STACKS"));
	}

	@Test
	void shouldFallBackToTheHoldingBranchWhenThereIsNoHomeBranch() {
		givenItem(item()
			.holdingLibraryId("BRANCH-SOUTH")
			.location("STACKS")
			.build());

		assertThat(firstItem().getLocationCode(), is("BRANCH-SOUTH"));
	}

	@Test
	void shouldTolerateAnItemWithNoBranchAtAll() {
		// No branch means no agency, and live availability drops agency-less items. It
		// must not take the rest of the bib's items down with it.
		givenItem(item().location("STACKS").build());

		final var item = firstItem();

		assertThat(item.getLocation(), is(nullValue()));
		assertThat(item.getShelvingLocation(), is("STACKS"));
	}

	@Test
	void shouldTakeTheVolumeFromTheSerialEnumeration() {
		givenItem(item()
			.homeLibraryId("BRANCH-NORTH")
			.serialIssueNumber("Vol. 2")
			.build());

		final var item = firstItem();

		assertThat(item.getRawVolumeStatement(), is("Vol. 2"));
		assertThat(item.getParsedVolumeStatement(), is("v2"));
	}

	@Test
	void shouldCreateTheVirtualItemAtTheBorrowingBranch() {
		// virtual-item-library-code is one value on the Host LMS. On a Koha serving
		// sixty libraries that would land every library's incoming loans at whichever
		// branch was configured, where no patron could collect them.
		createVirtualItem("BRANCH-NORTH");

		koha.verifyCreateItem(BIBLIO_ID,
			"{\"home_library_id\": \"BRANCH-NORTH\", \"holding_library_id\": \"BRANCH-NORTH\"}");
	}

	@Test
	void shouldFallBackToTheConfiguredVirtualItemLibrary() {
		// A Host LMS serving one library has nothing per-request to go on
		createVirtualItem(null);

		koha.verifyCreateItem(BIBLIO_ID, "{\"home_library_id\": \"DCB-VIRTUAL\"}");
	}

	@Test
	void shouldCreateTheVirtualPatronAtTheSharingLibrary() {
		// A virtual patron stands for a borrower outside this Koha, which is what the
		// sharing library represents. It used to read default-agency-code, which names
		// one of the co-tenant libraries and bypassed the shared-system guard.
		koha.mockCreatePatron(KohaPatron.builder().patronId(77L).build());

		client.createPatron(Patron.builder()
			.uniqueIds(List.of("unique-id"))
			.localNames(List.of("Ada", "Lovelace"))
			.localPatronType("DCB")
			.localHomeLibraryCode("SOMEWHERE-ELSE")
			.build()).block();

		koha.verifyPatronCreated("{\"library_id\": \"DCB-SHARING\"}");
	}

	@Test
	void shouldGiveTheVirtualPatronTheUniqueIdAsUseridAndNoCard() {
		// Koha caps cardnumber at 32 characters and userid at 75
		final var uniqueId = "1234567890@" + "a".repeat(29);
		koha.mockCreatePatron(KohaPatron.builder().patronId(77L).build());

		client.createPatron(Patron.builder()
			.uniqueIds(List.of(uniqueId))
			.localNames(List.of("Ada", "Lovelace"))
			.localPatronType("DCB")
			.build()).block();

		final var sent = koha.patronCreationSent();
		assertThat(sent, hasEntry("userid", uniqueId));
		assertThat(sent, not(hasKey("cardnumber")));
	}

	@Test
	void shouldFindTheVirtualPatronByUserid() {
		final var uniqueId = "1234567890@" + "a".repeat(29);
		koha.mockPatronsByUserid(uniqueId, KohaPatron.builder()
			.patronId(77L)
			.userid(uniqueId)
			.firstname("Borrowing")
			.surname("Patron")
			.libraryId("DCB-SHARING")
			.categoryId("DCB")
			.build());

		final var found = client.findVirtualPatron(org.olf.dcb.core.model.Patron.builder()
			.id(randomUUID())
			.patronIdentities(List.of(PatronIdentity.builder()
				.localId("1234567890")
				.resolvedAgency(DataAgency.builder().code("a".repeat(29)).build())
				.homeIdentity(true)
				.build()))
			.build()).block();

		assertThat(found.getLocalId(), contains("77"));
	}

	@Test
	void shouldNotCallKohaOncePerHoldingAllAtOnce() {
		// getItems issues a getActiveHoldsForItem per item. Unbounded, a title held at
		// sixty branches of a shared Koha is sixty concurrent calls to one server, per
		// availability check, per user - and co-tenancy is what makes the holding count
		// large while the server count stays at one.
		final var holdings = 60;

		koha.mockItemsForBiblio(BIBLIO_ID, IntStream.range(0, holdings)
			.mapToObj(index -> KohaItem.builder()
				.itemId((long) index)
				.biblioId(42L)
				.externalId("barcode-" + index)
				.homeLibraryId("BRANCH-%02d".formatted(index))
				.build())
			.toArray(KohaItem[]::new));

		ActiveHoldsProbe.reset();
		koha.mockActiveHoldsAnsweredBy(ActiveHoldsProbe.class);

		final var items = client.getItems(BibRecord.builder().sourceRecordId(BIBLIO_ID).build()).block();

		assertThat("Every holding is still mapped", items, hasSize(holdings));
		assertThat("Every holding's holds were read", ActiveHoldsProbe.calls.get(), is(holdings));
		assertThat("The probe has to see reads overlap, or it has measured nothing",
			ActiveHoldsProbe.peakInFlight.get(), is(greaterThan(1)));
		assertThat("Concurrent calls to one Koha must stay bounded",
			ActiveHoldsProbe.peakInFlight.get(), is(lessThanOrEqualTo(4)));
	}

	/**
	 * Runs inside the mock server, one invocation per active-holds read, and holds each
	 * open long enough for concurrent reads to overlap.
	 */
	public static class ActiveHoldsProbe implements ExpectationResponseCallback {
		static final AtomicInteger calls = new AtomicInteger();
		static final AtomicInteger inFlight = new AtomicInteger();
		static final AtomicInteger peakInFlight = new AtomicInteger();

		static void reset() {
			calls.set(0);
			inFlight.set(0);
			peakInFlight.set(0);
		}

		@Override
		public HttpResponse handle(HttpRequest request) throws InterruptedException {
			calls.incrementAndGet();
			peakInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);

			try {
				Thread.sleep(20);
			} finally {
				inFlight.decrementAndGet();
			}

			return okJson(new Object[0]);
		}
	}

	private void createVirtualItem(String patronHomeLocation) {
		referenceValueMappingFixture.defineCanonicalToLocalItemTypeMapping(KOHA_HOST_LMS_CODE, "BKM", "BK");
		koha.mockCreateItem(BIBLIO_ID, KohaItem.builder().itemId(99L).build());

		client.createItem(CreateItemCommand.builder()
			.bibId(BIBLIO_ID)
			.barcode("6565750674")
			.canonicalItemType("BKM")
			.patronHomeLocation(patronHomeLocation)
			.build()).block();
	}

	private Item firstItem() {
		final var items = client.getItems(BibRecord.builder()
			.sourceRecordId(BIBLIO_ID)
			.build()).block();

		assertThat("Expected exactly one item", items.size(), is(1));

		return items.get(0);
	}

	private void givenItem(KohaItem kohaItem) {
		koha.mockItemsForBiblio(BIBLIO_ID, kohaItem);
		koha.mockNoActiveHolds();
	}

	private static KohaItem.KohaItemBuilder item() {
		return KohaItem.builder()
			.itemId(99L)
			.biblioId(42L)
			.externalId("6565750674");
	}

	private static KohaPatron.KohaPatronBuilder patron() {
		return KohaPatron.builder()
			.patronId(1234L)
			.cardnumber("6747664")
			.firstname("Ada")
			.surname("Lovelace")
			.categoryId("ADULT");
	}
}
