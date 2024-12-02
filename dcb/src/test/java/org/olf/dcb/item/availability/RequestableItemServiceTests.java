package org.olf.dcb.item.availability;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.core.model.FunctionalSettingType.SELECT_UNAVAILABLE_ITEMS;
import static org.olf.dcb.core.model.ItemStatusCode.*;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.olf.dcb.core.model.*;
import org.olf.dcb.test.ConsortiumFixture;
import org.olf.dcb.test.DcbTest;

@Slf4j
@TestInstance(PER_CLASS)
@DcbTest
class RequestableItemServiceTests {

	@Inject private RequestableItemService requestableItemService;
	@Inject private ConsortiumFixture consortiumFixture;

	@BeforeAll void beforeAll() { consortiumFixture.deleteAll(); }
	@AfterEach void afterEach() { consortiumFixture.deleteAll(); }

	@Test
	@DisplayName("Available item should be requestable")
	void availableItemShouldBeRequestable() {

		final var item = createItem("id", AVAILABLE, "BOOK");

		final var result = isRequestable(item);

		assertThat(result, is(true));
	}

	@Test
	@DisplayName("Unavailable item should not be requestable")
	void unavailableItemShouldNotBeRequestable() {

		final var item = createItem("id", UNAVAILABLE, "BOOK");

		final var result = isRequestable(item);

		assertThat(result, is(false));
	}

	@Test
	@DisplayName("NONCIRC item should not be requestable")
	void noncircItemShouldNotBeRequestable() {

		final var item = createItem("id", AVAILABLE, "NONCIRC");
		final var result = isRequestable(item);

		assertThat(result, is(false));
	}

	@Test
	@DisplayName("SELECT_UNAVAILABLE_ITEMS switched ON with Available item should be requestable")
	void selectUnavailableItemsSwitchedOnShouldIncludeAvailableItems() {
		// Given
		enableSelectUnavailableItems(true);
		final var item = createItem("id", AVAILABLE, "BOOK");

		// When
		final var result = isRequestable(item);

		// Then
		assertThat(result, is(true));
	}

	@Test
	@DisplayName("SELECT_UNAVAILABLE_ITEMS switched OFF with Available item should be requestable")
	void selectUnavailableItemsSwitchedOffShouldIncludeAvailableItems() {
		// Given
		enableSelectUnavailableItems(false);
		final var item = createItem("id", AVAILABLE, "BOOK");

		// When
		final var result = isRequestable(item);

		// Then
		assertThat(result, is(true));
	}

	@Test
	@DisplayName("SELECT_UNAVAILABLE_ITEMS switched ON with CHECKED_OUT items included should allow selection")
	void selectUnavailableItemsSwitchedOnShouldIncludeCheckedOutItems() {
		// Given
		enableSelectUnavailableItems(true);
		final var item = createItem("id", CHECKED_OUT, "BOOK");

		// When
		final var result = isRequestable(item);

		// Then
		assertThat(result, is(true));
	}

	@Test
	@DisplayName("SELECT_UNAVAILABLE_ITEMS switched OFF should exclude CHECKED_OUT items")
	void selectUnavailableItemsSwitchedOffShouldExcludeCheckedOutItems() {
		// Given
		enableSelectUnavailableItems(false);
		final var item = createItem("id", CHECKED_OUT, "BOOK");

		// When
		final var result = isRequestable(item);

		// Then
		assertThat(result, is(false));
	}

	@Test
	@DisplayName("SELECT_UNAVAILABLE_ITEMS ON with no CHECKED_OUT items should include only AVAILABLE items")
	void selectUnavailableItemsSwitchedOnWithNoCheckedOutItemsShouldIncludeOnlyAvailableItems() {
		// Given
		enableSelectUnavailableItems(true);
		final var item = createItem("id", UNAVAILABLE, "BOOK");

		// When
		final var result = isRequestable(item);

		// Then
		assertThat(result, is(false));
	}

	@Test
	@DisplayName("SELECT_UNAVAILABLE_ITEMS OFF with no CHECKED_OUT items should include only AVAILABLE items")
	void selectUnavailableItemsSwitchedOffWithNoCheckedOutItemsShouldIncludeOnlyAvailableItems() {
		// Given
		enableSelectUnavailableItems(false);
		final var item = createItem("id", UNAVAILABLE, "BOOK");

		// When
		final var result = isRequestable(item);

		// Then
		assertThat(result, is(false));
	}

	@Test
	@DisplayName("Undefined SELECT_UNAVAILABLE_ITEMS setting should default to OFF and exclude CHECKED_OUT items")
	void undefinedSelectUnavailableItemsSettingShouldDefaultToOff() {
		// Given
		final var item = createItem("id", CHECKED_OUT, "BOOK");

		// When
		final var result = isRequestable(item);

		// Then
		assertThat(result, is(false));
	}

	private Boolean isRequestable(Item item) {
		return singleValueFrom(requestableItemService.isRequestable(item));
	}

	private void enableSelectUnavailableItems(boolean b) {
		consortiumFixture.createConsortiumWithFunctionalSetting(SELECT_UNAVAILABLE_ITEMS, b);
	}

	private static Item createItem(String id, ItemStatusCode statusCode, String canonicalItemType) {
		return Item.builder()
			.localId(id)
			.status(new ItemStatus(statusCode))
			.canonicalItemType(canonicalItemType)
			.build();
	}
}
