package org.olf.dcb.request.resolution;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.CHECKED_OUT;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.UNKNOWN;

import java.util.List;

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
		final var item = createItem("78458456", AVAILABLE, true);

		// Act
		final var chosenItem = resolutionStrategy.chooseItem(List.of(item), randomUUID(), null).block();

		// Assert
		assertThat("Should have expected local ID",
			chosenItem.getLocalId(), is("78458456"));

		assertThat("Should have expected host LMS",
			chosenItem.getHostLmsCode(), is("FAKE_HOST"));
	}

	@Test
	void shouldChooseFirstRequestableItemWhenMultipleItemsAreProvided() {
		// Arrange
		final var unavailableItem = createItem("23721346", UNAVAILABLE, false);
		final var unknownStatusItem = createItem("54737664", UNKNOWN, false);
		final var checkedOutItem = createItem("28375763", CHECKED_OUT, false);
		final var firstAvailableItem = createItem("47463572", AVAILABLE, true);
		final var secondAvailableItem = createItem("97848745", AVAILABLE, true);

		// Act
		final var items = List.of(unavailableItem, unknownStatusItem, checkedOutItem,
			firstAvailableItem, secondAvailableItem);

		final var chosenItem = resolutionStrategy.chooseItem(items, randomUUID(), null).block();

		// Assert
		assertThat("Should have expected local ID",
			chosenItem.getLocalId(), is("47463572"));

		assertThat("Should have expected host LMS",
			chosenItem.getHostLmsCode(), is("FAKE_HOST"));
	}

	@Test
	void shouldFailWhenNoRequestableItemsAreProvided() {
		// Arrange
		final var unavailableItem = createItem("23721346", UNAVAILABLE, false);
		final var unknownStatusItem = createItem("54737664", UNKNOWN, false);
		final var checkedOutItem = createItem("28375763", CHECKED_OUT, false);

		// Act
		final var items = List.of(unavailableItem, unknownStatusItem, checkedOutItem);

		final var clusterId = randomUUID();

		final var exception = assertThrows(NoItemsRequestableAtAnyAgency.class,
			() -> resolutionStrategy.chooseItem(items, clusterId, null).block());

		// Assert
		assertThat("Should get message associated with cluster record",
			exception.getMessage(),
			is("No requestable items could be found for cluster record: " + clusterId));
	}

	@Test
	void shouldFailWhenNoItemsAreProvided() {
		// Act
		final var clusterId = randomUUID();

		final var exception = assertThrows(NoItemsRequestableAtAnyAgency.class,
			() -> resolutionStrategy.chooseItem(List.of(), clusterId, null));

		// Assert
		assertThat("Should get message associated with cluster record",
			exception.getMessage(),
			is("No requestable items could be found for cluster record: " + clusterId));
	}

	private static Item createItem(String id,
		ItemStatusCode statusCode, Boolean requestable) {

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
			.build();
	}
}
