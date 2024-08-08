package org.olf.dcb.request.resolution;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalId;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.Agency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.PatronRequest;

class ManualSelectionResolutionStrategyTests {
	private final ResolutionStrategy resolutionStrategy = new ManualSelectionStrategy();

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
		final var chosenItem = chooseItem(List.of(item), randomUUID(), patronRequest);

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

		final var chosenItem = chooseItem(items, randomUUID(), patronRequest);

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
		final var chosenItem = chooseItem(List.of(), randomUUID(), patronRequest);

		// Assert
		assertThat("Empty publisher returned when no item can be chosen",
			chosenItem, nullValue());
	}

	private Item chooseItem(List<Item> items, UUID clusterRecordId, PatronRequest patronRequest) {
		return singleValueFrom(resolutionStrategy.chooseItem(items, clusterRecordId, patronRequest));
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
