package org.olf.dcb.item.availability;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.core.model.ItemStatusCode.UNAVAILABLE;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.ItemStatusCode;
import org.olf.dcb.core.model.Location;

class RequestableItemServiceTests {
	private final RequestableItemService requestableItemService
		= new RequestableItemService(List.of("allowed-code"), true);

	@Test
	@DisplayName("Available item at allowed location should be requestable")
	void availableItemAtAllowedLocationShouldBeRequestable() {
		final var item = createItem("id", AVAILABLE, "allowed-code", "BOOK");

		assertThat(requestableItemService.isRequestable(item), is(true));
	}

	@Test
	@DisplayName("Unavailable item at allowed location should not be requestable")
	void unavailableItemAtAllowedLocationShouldNotBeRequestable() {
		final var item = createItem("id", UNAVAILABLE, "allowed-code", "BOOK");

		assertThat(requestableItemService.isRequestable(item), is(false));
	}

	@Test
	@DisplayName("Available item at disallowed location should not be requestable")
	void availableItemAtDisallowedLocationShouldNotBeRequestable() {
		final var item = createItem("id", AVAILABLE, "disallowed-code", "BOOK");

		assertThat(requestableItemService.isRequestable(item), is(false));
	}

	@Test
	@DisplayName("Unavailable item at disallowed location should not be requestable")
	void unavailableItemAtDisallowedLocationShouldNotBeRequestable() {
		final var item = createItem("id", UNAVAILABLE, "disallowed-code", "BOOK");

		assertThat(requestableItemService.isRequestable(item), is(false));
	}

	@Test
	@DisplayName("Available item should be requestable when location filtering is disabled")
	void availableItemShouldBeRequestableWhenLocationFilteringIsDisabled() {
		final var serviceWithoutConfig = new RequestableItemService(List.of(), false);

		final var item = createItem("id", AVAILABLE, "allowed-code", "BOOK");

		assertThat(serviceWithoutConfig.isRequestable(item), is(true));
	}

	@Test
	@DisplayName("Unavailable item should not be requestable when location filtering is disabled")
	void unavailableItemShouldNotBeRequestableWhenLocationFilteringIsDisabled() {
		final var serviceWithoutConfig = new RequestableItemService(List.of(), false);

		final var item = createItem("id", UNAVAILABLE, "allowed-code", "BOOK");

		assertThat(serviceWithoutConfig.isRequestable(item), is(false));
	}

  @Test
  @DisplayName("NONCIRC item should not be requestable")
  void noncircItemShouldNotBeRequestableWhenLocationFilteringIsDisabled() {
    final var serviceWithoutConfig = new RequestableItemService(List.of(), false);
		final var item = createItem("id", AVAILABLE, "allowed-code", "NONCIRC");
    assertThat(serviceWithoutConfig.isRequestable(item), is(false));
  }


	private static Item createItem(String id, ItemStatusCode statusCode, String locationCode, String canonicalItemType) {
		return Item.builder()
			.localId(id)
			.location(Location.builder()
				.code(locationCode)
				.build())
			.status(new ItemStatus(statusCode))
			.canonicalItemType(canonicalItemType)
			.build();
	}
}
