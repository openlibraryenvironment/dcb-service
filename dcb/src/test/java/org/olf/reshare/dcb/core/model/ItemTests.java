package org.olf.reshare.dcb.core.model;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class ItemTests {

	@Test
	void naturalSortIsByLocationCodeAndCallNumber() {
		// Arrange
		final var firstItem = createItem(createLocation("CDE"), "FSF-FD");
		final var secondItem = createItem(createLocation("GHI"), "HGD-VC");
		final var thirdItem = createItem(createLocation("ABC"), "FDG-VD");
		final var fourthItem = createItem(createLocation("GHI"), "DEF-BC");

		// Act
		final var sortedList = sort(firstItem, secondItem, thirdItem, fourthItem);

		// Assert
		assertThat(sortedList, contains(thirdItem, firstItem, fourthItem, secondItem));
	}

	@Test
	void naturalSortToleratesNullCallNumber() {
		// Arrange
		final var nonNullCallNumberItem = createItem(createLocation("CDE"), "FSF-FD");
		final var nullCallNumberItem = createItem(createLocation("CDE"), null);

		// Act
		final var sortedList = sort(nullCallNumberItem, nonNullCallNumberItem);

		// Assert
		assertThat(sortedList, contains(nonNullCallNumberItem, nullCallNumberItem));
	}

	@Test
	void naturalSortToleratesNullLocationCode() {
		// Arrange
		final var nonNullLocationCodeItem = createItem(createLocation("CDE"), "ABC-DF");
		final var nullLocationCodeItem = createItem(createLocation(null), "ABC-DF");

		// Act
		final var sortedList = sort(nonNullLocationCodeItem, nullLocationCodeItem);

		// Assert
		assertThat(sortedList, contains(nonNullLocationCodeItem, nullLocationCodeItem));
	}

	@Test
	void naturalSortToleratesNullLocation() {
		// Arrange
		final var nonNullLocationCodeItem = createItem(createLocation("ABC-DF"), "CDE");
		final var nullLocationCodeItem = createItem(null, "CDE");

		// Act
		final var sortedList = sort(nullLocationCodeItem, nonNullLocationCodeItem);

		// Assert
		assertThat(sortedList, contains(nonNullLocationCodeItem, nullLocationCodeItem));
	}

	@Test
	void naturalSortToleratesNullItem() {
		// Arrange
		final var nonNullItem = createItem(createLocation("ABC-DF"), "CDE");

		// Act
		final var sortedList = sort(null, nonNullItem);

		// Assert
		assertThat(sortedList, contains(nonNullItem, null));
	}

	private static List<Item> sort(Item... items) {
		final var itemsList = new ArrayList<>(Arrays.asList(items));

		return sort(itemsList);
	}

	private static List<Item> sort(List<Item> list) {
		return list.stream().sorted().toList();
	}

	private static Item createItem(Location location, String callNumber) {
		return Item.builder()
			.callNumber(callNumber)
			.location(location)
			.build();
	}

	private static Location createLocation(String locationCode) {
		return Location.builder()
			.code(locationCode)
			.build();
	}
}
