package org.olf.dcb.request.resolution;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalId;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.Agency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.PatronRequest;

@Slf4j
@TestInstance(PER_CLASS)
class ManualSelectionTests {

	ManualSelection manualSelection = new ManualSelection();

	@Test
	void shouldChooseManuallySelectedItemWhenOnlyItem() {
		// Arrange
		final var localItemId = "78458456";

		final var item = createItem(localItemId);

		final var patronRequest = PatronRequest.builder()
			.localItemId(localItemId)
			.localItemAgencyCode("agencyCode")
			.localItemHostlmsCode("hostLmsCode")
			.build();

		// Act
		final var chosenItem = chooseItem(List.of(item), patronRequest);

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

		final var patronRequest = PatronRequest.builder()
			.localItemId(localItemId)
			.localItemAgencyCode("agencyCode")
			.localItemHostlmsCode("hostLmsCode")
			.build();

		// Act
		final var items = List.of(firstAvailableItem, secondAvailableItem);

		final var chosenItem = chooseItem(items, patronRequest);

		// Assert
		assertThat(chosenItem, allOf(
			hasLocalId("97848745")
		));
	}

	@Test
	void shouldChooseNoItemWhenNoItemsAreProvided() {
		// Arrange
		final var patronRequest = PatronRequest.builder()
			.localItemId("97848745")
			.localItemAgencyCode("agencyCode")
			.localItemHostlmsCode("hostLmsCode")
			.build();

		// Act
		final var chosenItem = chooseItem(List.of(), patronRequest);

		// Assert
		assertThat("Empty publisher returned when no item can be chosen",
			chosenItem, nullValue());
	}

	private Item chooseItem(List<Item> items, PatronRequest patronRequest) {

		Resolution resolution = Resolution.forPatronRequest(patronRequest).trackAllItems(items);

		return manualSelection.chooseItem(resolution);
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
