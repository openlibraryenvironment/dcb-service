package org.olf.dcb.request.resolution;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.UNKNOWN;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalId;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.ItemStatusCode;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.test.AgencyFixture;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.LocationFixture;

import jakarta.inject.Inject;

@DcbTest
class GeoDistanceResolutionStrategyTests {
	@Inject
	private GeoDistanceResolutionStrategy resolutionStrategy;

	@Inject
	private LocationFixture locationFixture;
	@Inject
	private AgencyFixture agencyFixture;

	@BeforeEach
	void beforeEach() {
		locationFixture.deleteAll();
		agencyFixture.deleteAll();
	}

	@Test
	void shouldChooseOnlyProvidedItem() {
		// Arrange
		final var pickupLocationId = definePickupLocationAtRoyalAlbertDock().getId();

		final var agency = defineAgencyLocatedAtChatsworth("example-agency");

		// Act
		final var items = List.of(createItem("23721346", AVAILABLE, true,
			0, agency));

		final var chosenItem = chooseItem(items, pickupLocationId.toString());

		// Assert
		assertThat(chosenItem, allOf(
			notNullValue(),
			hasLocalId("23721346")
		));
	}

	@Test
	void shouldChooseClosestItemToPickupLocation() {
		// Arrange
		final var pickupLocationId = definePickupLocationAtRoyalAlbertDock().getId();

		final var marbleArchAgency = defineAgencyLocatedAtMarbleArch("marble-arch");

		final var marbleArchItem = createItem("5634379", AVAILABLE, true,
			0, marbleArchAgency);

		final var chatsworthAgency = defineAgencyLocatedAtChatsworth("chatsworth");

		final var chatsworthItemId = "5639532";

		final var chatsworthItem = createItem(chatsworthItemId, AVAILABLE, true,
			0, chatsworthAgency);

		// Act
		final var items = List.of(marbleArchItem, chatsworthItem);

		final var chosenItem = chooseItem(items, pickupLocationId.toString());

		// Assert
		assertThat(chosenItem, allOf(
			notNullValue(),
			hasLocalId(chatsworthItemId)
		));
	}

	@Test
	void shouldChooseOnlyProvidedItemEvenWhenPickupLocationHasNoGeoLocation() {
		// Arrange
		final var pickupLocationId = locationFixture.createPickupLocation(
			"Pickup Location", "pickup-location").getId();

		final var agency = defineAgencyLocatedAtMarbleArch("example-agency");

		// Act
		final var items = List.of(createItem("536524", AVAILABLE, true,
			0, agency));

		final var chosenItem = chooseItem(items, pickupLocationId.toString());

		// Assert
		assertThat(chosenItem, allOf(
			notNullValue(),
			hasLocalId("536524")
		));
	}

	@Test
	void shouldChooseOnlyProvidedItemEvenWhenAgencyHasNoGeoLocation() {
		// Arrange
		final var pickupLocationId = definePickupLocationAtRoyalAlbertDock().getId();

		final var agency = agencyFixture.defineAgency("example-agency",
			"Example Agency", null);

		// Act
		final var items = List.of(createItem("536524", AVAILABLE, true,
			0, agency));

		final var chosenItem = chooseItem(items, pickupLocationId.toString());

		// Assert
		assertThat(chosenItem, allOf(
			notNullValue(),
			hasLocalId("536524")
		));
	}

	@Test
	void shouldChooseNoItemWhenNoItemsAreProvided() {
		// Arrange
		final var pickupLocationId = definePickupLocationAtRoyalAlbertDock().getId();

		// Act
		final var chosenItem = chooseItem(emptyList(), pickupLocationId.toString());

		// Assert
		assertThat(chosenItem, nullValue());
	}

	@Test
	void shouldChooseNoItemNoRequestableItemsAreProvided() {
		// Arrange
		final var pickupLocationId = definePickupLocationAtRoyalAlbertDock().getId();

		final var agency = defineAgencyLocatedAtChatsworth("example-agency");

		final var unavailableItem = createItem("23721346", UNAVAILABLE, false,
			0, agency);
		final var unknownStatusItem = createItem("54737664", UNKNOWN, false,
			0, agency);
		final var checkedOutItem = createItem("28375763", CHECKED_OUT, false,
			0, agency);

		// Act
		final var items = List.of(unavailableItem, unknownStatusItem, checkedOutItem);

		final var chosenItem = chooseItem(items, pickupLocationId.toString());

		// Assert
		assertThat(chosenItem, nullValue());
	}

	@Test
	void shouldChooseNoItemWhenNoItemHasAnAgency() {
		// Arrange
		final var pickupLocationId = definePickupLocationAtRoyalAlbertDock().getId();

		// Act
		final var items = List.of(
			createItem("6736564", AVAILABLE, true, 0, null));

		final var chosenItem = chooseItem(items, pickupLocationId.toString());

		// Assert
		assertThat(chosenItem, nullValue());
	}

	@Test
	void shouldChooseNoItemWhenOnlyItemsWithExistingHoldsAreProvided() {
		// Arrange
		final var pickupLocationId = definePickupLocationAtRoyalAlbertDock().getId();

		final var agency = defineAgencyLocatedAtChatsworth("example-agency");

		// Act
		final var items = List.of(
			createItem("6736564", AVAILABLE, true, 1, agency));

		final var chosenItem = chooseItem(items, pickupLocationId.toString());

		// Assert
		assertThat(chosenItem, nullValue());
	}

	@Test
	void shouldChooseNoItemWhenNoPickupLocationDoesNotExist() {
		// Act
		final var chosenItem = chooseItem(emptyList(), randomUUID().toString());

		// Assert
		assertThat(chosenItem, nullValue());
	}

	@Test
	void shouldFailWhenPatronRequestHasNoPickupLocation() {
		// Act
		final var exception = assertThrows(RuntimeException.class,
			() -> chooseItem(emptyList(), null));

		// Assert
		assertThat(exception, hasMessage("No pickup location code"));
	}

	private Item chooseItem(List<Item> items, String pickupLocationId) {
		return singleValueFrom(resolutionStrategy.chooseItem(items, randomUUID(),
			PatronRequest.builder()
				.pickupLocationCode(pickupLocationId)
				.build()));
	}

	private DataAgency defineAgencyLocatedAtChatsworth(String code) {
		// Is located at Chatsworth House, UK
		return agencyFixture.defineAgency(code, "Example Agency", null,
			53.227558, -1.611566);
	}

	private DataAgency defineAgencyLocatedAtMarbleArch(String code) {
		// Is located at Marble Arch, London, UK
		return agencyFixture.defineAgency(code, "Example Agency", null,
			51.513222, -0.159015);
	}

	private Location definePickupLocationAtRoyalAlbertDock() {
		// Is located at Royal Albert Dock, Liverpool, UK
		return locationFixture.createPickupLocation(
			"Pickup Location", "pickup-location", 53.399433, -2.992117);
	}

	private static Item createItem(String id, ItemStatusCode statusCode,
		Boolean requestable, int holdCount, DataAgency agency) {

		return Item.builder()
			.localId(id)
			.status(new ItemStatus(statusCode))
			.location(Location.builder()
				.code("code")
				.name("name")
				.build())
			.barcode("barcode")
			.callNumber("callNumber")
			.isRequestable(requestable)
			.holdCount(holdCount)
			.agency(agency)
			.build();
	}
}
