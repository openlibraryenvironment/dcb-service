package org.olf.dcb.request.resolution;

import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.*;

import java.util.List;
import java.util.UUID;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.core.model.ItemStatusCode.*;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalId;

class ManualSelectionResolutionStrategyTests {
	private final ManualSelectionStrategy resolutionStrategy
		= new ManualSelectionStrategy();

	@Test
	void shouldChooseManuallySelectedItemThatIsRequestable() {
		// Arrange
		final var localItemId = "78458456";
		final var localItemAgencyCode = "localItemAgencyCode";
		final var localItemHostlmsCode = "localItemHostlmsCode";

		final var item = createItem("78458456", AVAILABLE, true, 0, localItemAgencyCode, localItemHostlmsCode);

		final var patronRequest = PatronRequest.builder()
			.localItemId(localItemId)
			.localItemAgencyCode(localItemAgencyCode)
			.localItemHostlmsCode(localItemHostlmsCode)
			.build();

		// Act
		final var chosenItem = chooseItem(List.of(item), randomUUID(), patronRequest);

		// Assert
		assertThat(chosenItem, allOf(
			hasLocalId("78458456")
		));
	}

	@Test
	void shouldChooseManuallySelectedAndRequestableItemWhenMultipleItemsAreProvided() {
		// Arrange
		final var localItemId = "97848745";
		final var localItemAgencyCode = "localItemAgencyCode";
		final var localItemHostlmsCode = "localItemHostlmsCode";

		final var unavailableItem = createItem("23721346", UNAVAILABLE, false, 0, localItemAgencyCode, localItemHostlmsCode);
		final var unknownStatusItem = createItem("54737664", UNKNOWN, false, 0, localItemAgencyCode, localItemHostlmsCode);
		final var checkedOutItem = createItem("28375763", CHECKED_OUT, false, 0, localItemAgencyCode, localItemHostlmsCode);
		final var firstAvailableItem = createItem("47463572", AVAILABLE, true, 0, localItemAgencyCode, localItemHostlmsCode);
		final var secondAvailableItem = createItem("97848745", AVAILABLE, true, 0, localItemAgencyCode, localItemHostlmsCode);

		final var patronRequest = PatronRequest.builder()
			.localItemId(localItemId)
			.localItemAgencyCode(localItemAgencyCode)
			.localItemHostlmsCode(localItemHostlmsCode)
			.build();

		// Act
		final var items = List.of(unavailableItem, unknownStatusItem, checkedOutItem,
			firstAvailableItem, secondAvailableItem);

		final var chosenItem = chooseItem(items, randomUUID(), patronRequest);

		// Assert
		assertThat(chosenItem, allOf(
			hasLocalId("97848745")
		));
	}

	@Test
	void shouldReturnEmptyWhenNoRequestableItemsAreProvided() {
		// Arrange
		final var localItemId = "97848745";
		final var localItemAgencyCode = "localItemAgencyCode";
		final var localItemHostlmsCode = "localItemHostlmsCode";

		final var unavailableItem = createItem("23721346", UNAVAILABLE, false, 0, localItemAgencyCode, localItemHostlmsCode);
		final var unknownStatusItem = createItem("54737664", UNKNOWN, false, 0, localItemAgencyCode, localItemHostlmsCode);
		final var checkedOutItem = createItem("28375763", CHECKED_OUT, false, 0, localItemAgencyCode, localItemHostlmsCode);

		final var patronRequest = PatronRequest.builder()
			.localItemId(localItemId)
			.localItemAgencyCode(localItemAgencyCode)
			.localItemHostlmsCode(localItemHostlmsCode)
			.build();

		// Act
		final var items = List.of(unavailableItem, unknownStatusItem, checkedOutItem);

		final var chosenItem = chooseItem(items, randomUUID(), patronRequest);

		// Assert
		assertThat("Empty publisher returned when no item can be chosen",
			chosenItem, nullValue());
	}

	@Test
	void shouldReturnEmptyWhenOnlyItemsWithExistingHoldsAreProvided() {
		// Arrange
		final var localItemId = "97848745";
		final var localItemAgencyCode = "localItemAgencyCode";
		final var localItemHostlmsCode = "localItemHostlmsCode";

		final var patronRequest = PatronRequest.builder()
			.localItemId(localItemId)
			.localItemAgencyCode(localItemAgencyCode)
			.localItemHostlmsCode(localItemHostlmsCode)
			.build();

		// Act
		final var items = List.of(createItem("23721346", AVAILABLE, true, 1, localItemAgencyCode, localItemHostlmsCode));

		final var chosenItem = chooseItem(items, randomUUID(), patronRequest);

		// Assert
		assertThat("Empty publisher returned when no item can be chosen",
			chosenItem, nullValue());
	}

	@Test
	void shouldReturnEmptyWhenNoItemsAreProvided() {
		// Arrange
		final var localItemId = "97848745";
		final var localItemAgencyCode = "localItemAgencyCode";
		final var localItemHostlmsCode = "localItemHostlmsCode";

		final var patronRequest = PatronRequest.builder()
			.localItemId(localItemId)
			.localItemAgencyCode(localItemAgencyCode)
			.localItemHostlmsCode(localItemHostlmsCode)
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

	private static Item createItem(String id,
		ItemStatusCode statusCode, Boolean requestable,
		int holdCount, String agencyCode, String hostlmsCode) {

		return Item.builder()
			.localId(id)
			.agency(Agency.builder()
				.code(agencyCode)
				.hostLms(DataHostLms.builder()
					.code(hostlmsCode)
					.build())
				.build())
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
