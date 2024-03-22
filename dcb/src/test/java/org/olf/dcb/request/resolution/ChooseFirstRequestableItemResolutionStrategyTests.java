package org.olf.dcb.request.resolution;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
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
		final var item = createItem("78458456", AVAILABLE, true);

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
		final var unavailableItem = createItem("23721346", UNAVAILABLE, false);
		final var unknownStatusItem = createItem("54737664", UNKNOWN, false);
		final var checkedOutItem = createItem("28375763", CHECKED_OUT, false);
		final var firstAvailableItem = createItem("47463572", AVAILABLE, true);
		final var secondAvailableItem = createItem("97848745", AVAILABLE, true);

		// Act
		final var items = List.of(unavailableItem, unknownStatusItem, checkedOutItem,
			firstAvailableItem, secondAvailableItem);

		final var chosenItem = chooseItem(items, randomUUID());

		// Assert
		assertThat(chosenItem, allOf(
			hasLocalId("47463572")
		));
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
		assertThat(exception, hasMessage(
			"No requestable items could be found for cluster record: " + clusterId));
	}

	@Test
	void shouldFailWhenNoItemsAreProvided() {
		// Act
		final var clusterId = randomUUID();

		final var exception = assertThrows(NoItemsRequestableAtAnyAgency.class,
			() -> chooseItem(List.of(), clusterId));

		// Assert
		assertThat(exception, hasMessage(
			"No requestable items could be found for cluster record: " + clusterId));
	}

	private Item chooseItem(List<Item> items, UUID clusterRecordId) {
		return singleValueFrom(resolutionStrategy.chooseItem(items, clusterRecordId, null));
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
