package org.olf.reshare.dcb.item.availability;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.model.Item;
import org.olf.reshare.dcb.core.model.ItemStatus;
import org.olf.reshare.dcb.core.model.ItemStatusCode;
import org.olf.reshare.dcb.core.model.Location;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.AVAILABLE;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.UNAVAILABLE;

@MicronautTest(transactional = false, propertySources = { "classpath:configs/RequestableItemServiceTests.yml" }, rebuildContext = true)
public class RequestableItemServiceTests {
	@Inject
	private RequestableItemService requestableItemService;

	@Test
	void itemIsAtALocationInTheAllowListAndIsAvailable() {

		final var item = createFakeItem("id", "hostLmsCode",
			AVAILABLE, "allowed-code");

		final var requestableItems =
			requestableItemService.determineRequestable(List.of(item));

		assertThat(requestableItems, is(notNullValue()));
		assertThat(requestableItems.size(), is(1));

		final var onlyItem = requestableItems.get(0);

		assertThat(onlyItem.getId(), is("id"));
		assertThat(onlyItem.getIsRequestable(), is(true));
		assertThat(onlyItem.getHoldCount(), is(0));
	}

	@Test
	void itemIsAtALocationInTheAllowListAndIsUnavailable() {

		final var item = createFakeItem("id", "hostLmsCode",
			UNAVAILABLE, "allowed-code");

		final var requestableItems =
			requestableItemService.determineRequestable(List.of(item));

		assertThat(requestableItems, is(notNullValue()));
		assertThat(requestableItems.size(), is(1));

		final var onlyItem = requestableItems.get(0);

		assertThat(onlyItem.getId(), is("id"));
		assertThat(onlyItem.getIsRequestable(), is(false));
		assertThat(onlyItem.getHoldCount(), is(0));
	}

	@Test
	void itemIsAtALocationButNotInTheAllowList() {

		final var item1 = createFakeItem("id1", "hostLmsCode",
			UNAVAILABLE, "disallowed-code");

		final var item2 = createFakeItem("id2", "hostLmsCode",
			AVAILABLE, "disallowed-code");

		final var requestableItems =
			requestableItemService.determineRequestable(List.of(item1, item2));

		assertThat(requestableItems, is(notNullValue()));
		assertThat(requestableItems.size(), is(2));

		final var requestableItem1 = requestableItems.get(0);

		assertThat(requestableItem1.getId(), is("id1"));
		assertThat(requestableItem1.getIsRequestable(), is(false));
		assertThat(requestableItem1.getHoldCount(), is(0));

		final var requestableItem2 = requestableItems.get(1);

		assertThat(requestableItem2.getId(), is("id2"));
		assertThat(requestableItem2.getIsRequestable(), is(false));
		assertThat(requestableItem2.getHoldCount(), is(0));

	}

	@Test
	void itemRequestabiityWithoutConfigValues() {

		final var serviceWithoutConfig = new RequestableItemService(List.of(), false);

		final var item1 = createFakeItem("id1", "hostLmsCode",
			UNAVAILABLE, "code");

		final var item2 = createFakeItem("id2", "hostLmsCode",
			AVAILABLE, "code");

		final var requestableItems =
			serviceWithoutConfig.determineRequestable(List.of(item1, item2));

		assertThat(requestableItems, is(notNullValue()));
		assertThat(requestableItems.size(), is(2));

		final var requestableItem1 = requestableItems.get(0);

		assertThat(requestableItem1.getId(), is("id1"));
		assertThat(requestableItem1.getIsRequestable(), is(false));
		assertThat(requestableItem1.getHoldCount(), is(0));

		final var requestableItem2 = requestableItems.get(1);

		assertThat(requestableItem2.getId(), is("id2"));
		assertThat(requestableItem2.getIsRequestable(), is(true));
		assertThat(requestableItem2.getHoldCount(), is(0));
	}

	private static Item createFakeItem(
		String id, String hostLmsCode, ItemStatusCode statusCode,
		String code) {

		return new Item(id,
			new ItemStatus(statusCode), null, Location.builder()
			.code(code)
			.name("name")
			.build(),
			"barcode", "callNumber",
			hostLmsCode, null, 0);
	}
}
