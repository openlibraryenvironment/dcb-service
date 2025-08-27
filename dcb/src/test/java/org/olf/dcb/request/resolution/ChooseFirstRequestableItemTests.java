package org.olf.dcb.request.resolution;

import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.olf.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;
import static org.olf.dcb.test.matchers.ItemMatchers.hasLocalId;

import java.util.List;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.ItemStatus;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.test.DcbTest;

import jakarta.inject.Inject;

@DcbTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChooseFirstRequestableItemTests {
	@Inject PatronRequestResolutionService requestResolutionService;

	@Test
	void shouldChooseOnlyRequestableItem() {
		// Arrange
		final var item = createItem("78458456");

		final var resolution = buildResolution(List.of(item));

		// Act
		final var returnedResolution = chooseItem(resolution);

		// Assert
		assertThat(returnedResolution, hasChosenItem("78458456"));
	}

	@Test
	void shouldChooseFirstRequestableItemWhenMultipleItemsAreProvided() {
		// Arrange
		final var firstAvailableItem = createItem("47463572");
		final var secondAvailableItem = createItem("97848745");

		final var items = List.of(firstAvailableItem, secondAvailableItem);

		final var resolution = buildResolution(items);

		// Act
		final var returnedResolution = chooseItem(resolution);

		// Assert
		assertThat(returnedResolution, hasChosenItem("47463572"));
	}

	@Test
	void shouldChooseNoItemWhenNoItemsAreProvided() {
		// Arrange
		final var resolution = buildResolution(emptyList());

		// Act
		final var returnedResolution = chooseItem(resolution);

		// Assert
		assertThat(returnedResolution, hasNoChosenItem());
	}


	private Resolution chooseItem(Resolution resolution) {
		return singleValueFrom(requestResolutionService.firstRequestableItem(resolution));
	}

	private static Item createItem(String id) {
		return Item.builder()
			.localId(id)
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

	private static Resolution buildResolution(List<Item> items) {
		final var patronRequest = PatronRequest.builder().id(randomUUID()).build();

		// Sorted items are required for selection to choose from
		return Resolution.forParameters(patronRequest, emptyList())
			.trackSortedItems(items);
	}

	public static Matcher<Resolution> hasChosenItem(String localItemId) {
		return hasProperty("chosenItem", hasLocalId(localItemId));
	}

	public static Matcher<Resolution> hasNoChosenItem() {
		return hasProperty("chosenItem", nullValue());
	}
}
