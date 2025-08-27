package org.olf.dcb.request.resolution;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalId;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.Agency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.Location;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@TestInstance(PER_CLASS)
class ManualSelectionTests {
	ManualSelection manualSelection = new ManualSelection();

	@Test
	void shouldChooseManuallySelectedItemWhenOnlyItem() {
		// Arrange
		final var localItemId = "78458456";

		final var item = createItem(localItemId);

		final var itemSelection = ManualItemSelection.builder()
			.localItemId(localItemId)
			.hostLmsCode("hostLmsCode")
			.agencyCode("agencyCode")
			.build();

		// Act
		final var chosenItem = chooseItem(List.of(item), itemSelection);

		// Assert
		assertThat(chosenItem, allOf(
			notNullValue(),
			hasLocalId("78458456")
		));
	}

	@Test
	void shouldChooseManuallySelectedItemWhenMultipleItemsAreProvided() {
		// Arrange
		final var localItemId = "97848745";

		final var firstAvailableItem = createItem("47463572");
		final var secondAvailableItem = createItem(localItemId);

		final var items = List.of(firstAvailableItem, secondAvailableItem);

		final var itemSelection = ManualItemSelection.builder()
			.localItemId(localItemId)
			.hostLmsCode("hostLmsCode")
			.agencyCode("agencyCode")
			.build();

		// Act
		final var chosenItem = chooseItem(items, itemSelection);

		// Assert
		assertThat(chosenItem, hasLocalId("97848745"));
	}

	@Test
	void shouldChooseNoItemWhenNoItemsAreProvided() {
		// Arrange
		final var itemSelection = ManualItemSelection.builder()
			.localItemId("97848745")
			.hostLmsCode("hostLmsCode")
			.agencyCode("agencyCode")
			.build();

		// Act
		final var chosenItem = chooseItem(List.of(), itemSelection);

		// Assert
		assertThat("Empty publisher returned when no item can be chosen",
			chosenItem, nullValue());
	}

	private Item chooseItem(List<Item> items, ManualItemSelection itemSelection) {
		return manualSelection.chooseItem(items, itemSelection);
	}

	private static Item createItem(String localId) {
		return Item.builder()
			.localId(localId)
			.agency(Agency.builder()
				.code("agencyCode")
				.hostLms(DataHostLms.builder()
					.code("hostLmsCode")
					.build())
				.build())
			.status(new ItemStatus(AVAILABLE))
			.location(Location.builder()
				.code("code")
				.name("name")
				.build())
			.barcode("barcode")
			.callNumber("callNumber")
			.isRequestable(true)
			.holdCount(0)
			.build();
	}
}
