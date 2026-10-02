package org.olf.dcb.core.interaction.koha;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ItemMatchers.hasAgencyCode;
import static org.olf.dcb.test.matchers.ItemMatchers.hasCanonicalItemType;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalItemTypeCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.olf.dcb.core.interaction.CreateItemCommand;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.interaction.PlaceHoldRequestParameters;
import org.olf.dcb.core.interaction.koha.dto.KohaHoldResponse;
import org.olf.dcb.core.interaction.koha.dto.KohaItem;
import org.olf.dcb.core.interaction.shared.NoPatronTypeMappingFoundException;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.HostLmsFixture;
import org.olf.dcb.test.ReferenceValueMappingFixture;
import org.zalando.problem.ThrowableProblem;

import jakarta.inject.Inject;
import services.k_int.test.mockserver.MockServerMicronautTest;

/**
 * The configuration a Koha library sets up in DCB Admin before it can lend or borrow:
 * item type mappings, patron type mappings, location mappings, and pickup locations.
 * <p>
 * Unlike {@link KohaMappingTests}, which mocks the mapping services to isolate what the
 * Koha field mapper produces, this exercises the real reference value mapping lookups
 * against the database. That is the part a library's own configuration lands in, so it
 * is the part worth proving: the mappings are keyed by category, context and value, and
 * a Koha client that asks with the wrong key finds nothing and fails a request with no
 * indication that the mapping the library carefully entered was simply never consulted.
 */
@MockServerMicronautTest
class KohaMappingAndPickupLocationTests {
	private static final String KOHA_HOST_LMS_CODE = "koha-host-lms";
	private static final String KOHA_HOST = "fake-koha";

	private static final String BIBLIO_ID = "4001";

	@Inject
	private HostLmsFixture hostLmsFixture;
	@Inject
	private ReferenceValueMappingFixture referenceValueMappingFixture;
	@Inject
	private AgencyFixture agencyFixture;

	private MockKohaFixture mockKohaFixture;
	private HostLmsClient client;

	@BeforeEach
	void beforeEach(MockServerClient mockServerClient) {
		mockServerClient.reset();

		agencyFixture.deleteAll();
		referenceValueMappingFixture.deleteAll();
		hostLmsFixture.deleteAll();

		hostLmsFixture.createKohaHostLms(KOHA_HOST_LMS_CODE, "https://" + KOHA_HOST);

		mockKohaFixture = new MockKohaFixture(mockServerClient, KOHA_HOST);

		client = hostLmsFixture.createClient(KOHA_HOST_LMS_CODE);
	}

	/**
	 * Item types, Koha to DCB. The mapping is keyed on the Koha itemtype code, and
	 * effective_item_type_id is what Koha resolves for the item, so that is the value
	 * the library's mapping has to be entered against.
	 */
	@Test
	void shouldMapAKohaItemTypeToACanonicalItemType() {
		// Arrange
		referenceValueMappingFixture.defineLocalToCanonicalItemTypeMapping(
			KOHA_HOST_LMS_CODE, "BK", "CIRC");

		mockKohaFixture.mockNoActiveHolds();
		mockKohaFixture.mockItemsForBiblio(BIBLIO_ID, KohaItem.builder()
			.itemId(1L)
			.biblioId(Long.valueOf(BIBLIO_ID))
			.externalId("item-barcode")
			.effectiveItemTypeId("BK")
			.homeLibraryId("BRANCH-N")
			.build());

		// Act
		final var items = singleValueFrom(client.getItems(bibRecord()));

		// Assert
		assertThat(items, contains(allOf(
			hasLocalItemTypeCode("BK"),
			hasCanonicalItemType("CIRC")
		)));
	}

	/**
	 * Item types, DCB to Koha. A virtual item cannot be created without one, so an
	 * unmapped canonical type has to say which mapping is missing rather than fail
	 * somewhere inside Koha.
	 */
	@Test
	void shouldMapACanonicalItemTypeToAKohaItemTypeWhenCreatingAVirtualItem() {
		// Arrange
		referenceValueMappingFixture.defineCanonicalToLocalItemTypeMapping(
			KOHA_HOST_LMS_CODE, "CIRC", "DCB_VIRTUAL");

		mockKohaFixture.mockCreateItem(BIBLIO_ID, KohaItem.builder()
			.itemId(77L)
			.biblioId(Long.valueOf(BIBLIO_ID))
			.externalId("virtual-barcode")
			.itemTypeId("DCB_VIRTUAL")
			.build());

		// Act
		final var item = singleValueFrom(client.createItem(CreateItemCommand.builder()
			.bibId(BIBLIO_ID)
			.barcode("virtual-barcode")
			.canonicalItemType("CIRC")
			.patronHomeLocation("BRANCH-S")
			.build()));

		// Assert
		assertThat("The item Koha created comes back", item.getLocalId(), is("77"));

		mockKohaFixture.verifyCreateItem(BIBLIO_ID, "{\"item_type_id\": \"DCB_VIRTUAL\"}");
	}

	@Test
	void shouldFailToCreateAVirtualItemWhenTheItemTypeIsNotMapped() {
		final var problem = assertThrows(ThrowableProblem.class,
			() -> singleValueFrom(client.createItem(CreateItemCommand.builder()
				.bibId(BIBLIO_ID)
				.barcode("virtual-barcode")
				.canonicalItemType("CIRC")
				.patronHomeLocation("BRANCH-S")
				.build())));

		assertThat("The operator is told which direction of mapping is absent",
			problem.getTitle(),
			is("Unable to find item type mapping from DCB to " + KOHA_HOST_LMS_CODE));
	}

	/**
	 * Patron types, Koha to DCB. Koha patron categories are short codes, not numbers,
	 * so these are reference value mappings - a Koha library that follows Sierra's
	 * numeric range guidance ends up with mappings nothing consults.
	 */
	@Test
	void shouldMapAKohaPatronCategoryToACanonicalPatronType() {
		referenceValueMappingFixture.definePatronTypeMapping(
			KOHA_HOST_LMS_CODE, "AD", "DCB", "ADULT");

		assertThat(singleValueFrom(client.findCanonicalPatronType("AD", "1234")),
			is("ADULT"));
	}

	@Test
	void shouldMapACanonicalPatronTypeToAKohaPatronCategory() {
		referenceValueMappingFixture.definePatronTypeMapping(
			"DCB", "ADULT", KOHA_HOST_LMS_CODE, "AD");

		assertThat(singleValueFrom(client.findLocalPatronType("ADULT")), is("AD"));
	}

	@Test
	void shouldFailWhenAKohaPatronCategoryIsNotMapped() {
		final var error = assertThrows(NoPatronTypeMappingFoundException.class,
			() -> singleValueFrom(client.findCanonicalPatronType("AD", "1234")));

		assertThat(error.getMessage(), allOf(
			containsString("Unable to map patron type \"AD\""),
			containsString(KOHA_HOST_LMS_CODE)));
	}

	/**
	 * Locations to agencies. The Koha value is the <em>branch</em> - home_library_id -
	 * and not Koha's "location", which is a shelving classifier every branch on a
	 * shared server shares. Mapping the shelving location instead produces an item
	 * that either resolves to the wrong library or to none.
	 */
	@Test
	void shouldMapAKohaBranchToAnAgency() {
		// Arrange
		agencyFixture.defineAgency("north-agency", "North Agency",
			hostLmsFixture.findByCode(KOHA_HOST_LMS_CODE));

		referenceValueMappingFixture.defineLocationToAgencyMapping(
			KOHA_HOST_LMS_CODE, "BRANCH-N", "north-agency");

		mockKohaFixture.mockNoActiveHolds();
		mockKohaFixture.mockItemsForBiblio(BIBLIO_ID, KohaItem.builder()
			.itemId(1L)
			.biblioId(Long.valueOf(BIBLIO_ID))
			.externalId("item-barcode")
			.homeLibraryId("BRANCH-N")
			// The shelving location, which must not be what the mapping is keyed on
			.location("STACKS")
			.build());

		// Act
		final var items = singleValueFrom(client.getItems(bibRecord()));

		// Assert
		assertThat(items, contains(hasAgencyCode("north-agency")));
	}

	@Test
	void shouldLeaveAnItemWithoutAnAgencyWhenItsBranchIsNotMapped() {
		mockKohaFixture.mockNoActiveHolds();
		mockKohaFixture.mockItemsForBiblio(BIBLIO_ID, KohaItem.builder()
			.itemId(1L)
			.biblioId(Long.valueOf(BIBLIO_ID))
			.externalId("item-barcode")
			.homeLibraryId("BRANCH-UNMAPPED")
			.build());

		final var items = singleValueFrom(client.getItems(bibRecord()));

		assertThat("An unmapped branch has to leave the item unresolved rather than "
				+ "attribute it to some other library",
			items.get(0).getAgency(), is((Object) null));
	}

	/**
	 * Pickup locations. A pickup location's localId is the Koha branch code, and it is
	 * the only field that carries one - pickupLocationCode holds the pickup Location's
	 * UUID, which Koha's library_id (10 characters) cannot hold.
	 */
	@Test
	void shouldPlaceAHoldAtTheKohaBranchTheLocationNames() {
		// Arrange
		mockKohaFixture.mockPlaceHold(KohaHoldResponse.builder()
			.holdId(501L)
			.itemId(1L)
			.status("placed")
			.pickupLibraryId("BRANCH-N")
			.build());

		final var pickupLocation = Location.builder()
			.id(randomUUID())
			.code("PICKUP-NORTH")
			.name("North Branch")
			.localId("BRANCH-N")
			.build();

		// Act
		final var localRequest = singleValueFrom(client.placeHoldRequestAtPickupAgency(
			holdParameters(pickupLocation)));

		// Assert
		assertThat(localRequest.getLocalId(), is("501"));

		mockKohaFixture.verifyHoldPlaced("{\"pickup_library_id\": \"BRANCH-N\"}");
	}

	@Test
	void shouldReportTheHeldItemsBarcodeKohaDoesNotEcho() {
		// Koha's POST /holds answers with a bare hold - the operation takes no
		// x-koha-embed - so there is no item on the response to read a barcode from.
		// Reading through it failed every Koha hold placement, pickup location or not.
		mockKohaFixture.mockPlaceHold(KohaHoldResponse.builder()
			.holdId(503L)
			.itemId(1L)
			.status("placed")
			.build());

		final var localRequest = singleValueFrom(client.placeHoldRequestAtPickupAgency(
			PlaceHoldRequestParameters.builder()
				.localPatronId("1234")
				.localBibId(BIBLIO_ID)
				.localItemId("1")
				.localItemBarcode("held-item-barcode")
				.pickupLocation(Location.builder()
					.id(randomUUID())
					.code("PICKUP-NORTH")
					.name("North Branch")
					.localId("BRANCH-N")
					.build())
				.patronRequestId(randomUUID().toString())
				.build()));

		assertThat("Downstream tracking needs the barcode, and DCB already knows it",
			localRequest.getRequestedItemBarcode(), is("held-item-barcode"));
	}

	@Test
	void shouldNeverSendThePickupLocationsUuidAsAKohaBranch() {
		// The pickup location UUID used to be the fallback when localId was absent.
		// Koha's library_id is at most 10 characters, so it could only ever be rejected -
		// and a rejected hold at this point looks like a Koha fault rather than a pickup
		// location missing its branch code.
		mockKohaFixture.mockPlaceHold(KohaHoldResponse.builder()
			.holdId(502L)
			.itemId(1L)
			.status("placed")
			.build());

		final var pickupLocationWithNoLocalId = Location.builder()
			.id(randomUUID())
			.code("PICKUP-NORTH")
			.name("North Branch")
			.build();

		singleValueFrom(client.placeHoldRequestAtPickupAgency(
			holdParameters(pickupLocationWithNoLocalId)));

		// Falls back to the sharing library, which is a real branch, rather than a UUID
		mockKohaFixture.verifyHoldPlaced("{\"pickup_library_id\": \"DCB-SHARING\"}");
	}

	private PlaceHoldRequestParameters holdParameters(Location pickupLocation) {
		return PlaceHoldRequestParameters.builder()
			.localPatronId("1234")
			.localBibId(BIBLIO_ID)
			.localItemId("1")
			.pickupLocation(pickupLocation)
			// Deliberately the Location's UUID, which is what the request actually carries
			.pickupLocationCode(pickupLocation.getId().toString())
			.patronRequestId(randomUUID().toString())
			.build();
	}

	private BibRecord bibRecord() {
		return BibRecord.builder()
			.id(randomUUID())
			.sourceRecordId(BIBLIO_ID)
			.build();
	}
}
