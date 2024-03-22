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
		final var pickupLocationId = definePickupLocation().getId();

		defineExampleAgency();

		// Act
		final var items = List.of(createItem("23721346", AVAILABLE, true,
			"example-agency", 0));

		final var chosenItem = chooseItem(items, pickupLocationId.toString());

		// Assert
		assertThat(chosenItem, allOf(
			notNullValue(),
			hasLocalId("23721346")
		));
	}

	@Test
	void shouldChooseOnlyProvidedItemEvenWhenPickupLocationHasNoGeoLocation() {
		// Arrange
		final var pickupLocationId = locationFixture.createPickupLocation(
			"Pickup Location", "pickup-location").getId();

		defineExampleAgency();

		// Act
		final var items = List.of(createItem("536524", AVAILABLE, true,
			"example-agency", 0));

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
		final var pickupLocationId = definePickupLocation().getId();

		agencyFixture.defineAgency("example-agency", "Example Agency", null);

		// Act
		final var items = List.of(createItem("536524", AVAILABLE, true,
			"example-agency", 0));

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
		final var pickupLocationId = definePickupLocation().getId();

		// Act
		final var chosenItem = chooseItem(emptyList(), pickupLocationId.toString());

		// Assert
		assertThat(chosenItem, nullValue());
	}

	@Test
	void shouldChooseNoItemNoRequestableItemsAreProvided() {
		// Arrange
		final var pickupLocationId = definePickupLocation().getId();

		defineExampleAgency();

		final var unavailableItem = createItem("23721346", UNAVAILABLE, false,
			"example-agency", 0);
		final var unknownStatusItem = createItem("54737664", UNKNOWN, false,
			"example-agency", 0);
		final var checkedOutItem = createItem("28375763", CHECKED_OUT, false,
			"example-agency", 0);

		// Act
		final var items = List.of(unavailableItem, unknownStatusItem, checkedOutItem);

		final var chosenItem = chooseItem(items, pickupLocationId.toString());

		// Assert
		assertThat(chosenItem, nullValue());
	}

	@Test
	void shouldChooseNoItemNoItemHasAnAgencyCode() {
		// Arrange
		final var pickupLocationId = definePickupLocation().getId();

		// Act
		final var items = List.of(
			createItem("6736564", AVAILABLE, true, null, 0));

		final var chosenItem = chooseItem(items, pickupLocationId.toString());

		// Assert
		assertThat(chosenItem, nullValue());
	}

	@Test
	void shouldChooseNoItemNoItemHasAnAgency() {
		// Arrange
		final var pickupLocationId = definePickupLocation().getId();

		// Act
		final var items = List.of(
			createItem("6736564", AVAILABLE, true, "unknown-agency", 0));

		final var chosenItem = chooseItem(items, pickupLocationId.toString());

		// Assert
		assertThat(chosenItem, nullValue());
	}

	@Test
	void shouldChooseNoItemWhenOnlyItemsWithExistingHoldsAreProvided() {
		// Arrange
		final var pickupLocationId = definePickupLocation().getId();

		defineExampleAgency();

		// Act
		final var items = List.of(
			createItem("6736564", AVAILABLE, true, "example-agency", 1));

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

	private void defineExampleAgency() {
		// Is located at Chatsworth House, UK
		agencyFixture.defineAgency("example-agency", "Example Agency", null,
			53.227558, -1.611566);
	}

	private Location definePickupLocation() {
		// Is located at Royal Albert Dock, Liverpool, UK
		return locationFixture.createPickupLocation(
			"Pickup Location", "pickup-location", 53.399433, -2.992117);
	}

	private static Item createItem(String id, ItemStatusCode statusCode,
		Boolean requestable, String agencyCode, int holdCount) {

		return Item.builder()
			.localId(id)
			.status(new ItemStatus(statusCode))
			.location(Location.builder()
				.code("code")
				.name("name")
				.build())
			.barcode("barcode")
			.callNumber("callNumber")
			.hostLmsCode("FAKE_HOST")
			.isRequestable(requestable)
			.holdCount(holdCount)
			.agencyCode(agencyCode)
			.build();
	}

}
