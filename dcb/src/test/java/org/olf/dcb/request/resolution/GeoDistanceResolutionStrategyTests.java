package org.olf.dcb.request.resolution;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.UNKNOWN;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ThrowableMatchers.hasMessage;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.ItemStatusCode;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.LocationFixture;

import jakarta.inject.Inject;

@DcbTest
class GeoDistanceResolutionStrategyTests {
	@Inject
	private GeoDistanceResolutionStrategy resolutionStrategy;

	@Inject
	private LocationFixture locationFixture;

	@BeforeEach
	void beforeEach() {
		locationFixture.deleteAll();
	}

	@Test
	void shouldChooseNoItemWhenNoItemsAreProvided() {
		// Arrange
		final var pickupLocationId = locationFixture.createPickupLocation(
			"Pickup Location", "pickup-location").getId();

		// Act
		final var chosenItem = chooseItem(emptyList(), pickupLocationId.toString());

		// Assert
		assertThat(chosenItem, nullValue());
	}

	@Test
	void shouldChooseNoItemNoRequestableItemsAreProvided() {
		// Arrange
		final var pickupLocationId = locationFixture.createPickupLocation(
			"Pickup Location", "pickup-location").getId();

		final var unavailableItem = createItem("23721346", UNAVAILABLE, false,
			"example-agency");
		final var unknownStatusItem = createItem("54737664", UNKNOWN, false,
			"example-agency");
		final var checkedOutItem = createItem("28375763", CHECKED_OUT, false,
			"example-agency");

		// Act
		final var items = List.of(unavailableItem, unknownStatusItem, checkedOutItem);

		final var chosenItem = chooseItem(items, pickupLocationId.toString());

		// Assert
		assertThat(chosenItem, nullValue());
	}

	@Test
	void shouldChooseNoItemNoItemHasAnAgencyCode() {
		// Arrange
		final var pickupLocationId = locationFixture.createPickupLocation(
			"Pickup Location", "pickup-location").getId();

		// Act
		final var items = List.of(createItem("6736564", AVAILABLE, true, null));

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

	private static Item createItem(String id, ItemStatusCode statusCode,
		Boolean requestable, String agencyCode) {

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
			.holdCount(0)
			.agencyCode(agencyCode)
			.build();
	}

}
