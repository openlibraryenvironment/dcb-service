package org.olf.dcb.request.resolution;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalId;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.ItemStatusCode;
import org.olf.dcb.core.model.Location;

class ChooseFirstRequestableItemResolutionStrategyTests {
	private final FirstRequestableItemResolutionStrategy resolutionStrategy
		= new FirstRequestableItemResolutionStrategy();

	@Test
	void shouldChooseOnlyRequestableItem() {
		// Arrange
		final var item = createItem("78458456", AVAILABLE, true, 0);

		// Act
		final var chosenItem = chooseItem(List.of(item), randomUUID());

		// Assert
		assertThat(chosenItem, allOf(
			hasLocalId("78458456")
		));
	}

	@Test
	void shouldChooseFirstRequestableItemWhenMultipleItemsAreProvided() {
		// Arrange
		final var firstAvailableItem = createItem("47463572", AVAILABLE, true, 0);
		final var secondAvailableItem = createItem("97848745", AVAILABLE, true, 0);

		// Act
		final var items = List.of(firstAvailableItem, secondAvailableItem);

		final var chosenItem = chooseItem(items, randomUUID());

		// Assert
		assertThat(chosenItem, allOf(
			hasLocalId("47463572")
		));
	}

	@Test
	void shouldReturnEmptyWhenOnlyItemsWithExistingHoldsAreProvided() {
		// Act
		final var items = List.of(createItem("23721346", AVAILABLE, true, 1));

		final var chosenItem = chooseItem(items, randomUUID());

		// Assert
		assertThat("Empty publisher returned when no item can be chosen",
			chosenItem, nullValue());
	}

	@Test
	void shouldReturnEmptyWhenNoItemsAreProvided() {
		// Act
		final var chosenItem = chooseItem(List.of(), randomUUID());

		// Assert
		assertThat("Empty publisher returned when no item can be chosen",
			chosenItem, nullValue());
	}

	private Item chooseItem(List<Item> items, UUID clusterRecordId) {
		return singleValueFrom(resolutionStrategy.chooseItem(items, clusterRecordId, null));
	}

	private static Item createItem(String id,
		ItemStatusCode statusCode, Boolean requestable, int holdCount) {

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
			.build();
	}
}
