package org.olf.reshare.dcb.request.resolution.fake;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.AVAILABLE;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.request.resolution.ClusteredBib;

public class FakeLiveAvailabilityServiceTests {
	@Test
	void shouldAlwaysReturnListOfItems() {
		final var fakeLiveAvailabilityService = new FakeLiveAvailabilityService();

		final var clusteredBib =
			new ClusteredBib(randomUUID(), "title", List.of());

		final var items = fakeLiveAvailabilityService
			.getAvailableItems(clusteredBib).block();

		assertThat(items, is(notNullValue()));
		assertThat(items.size(), is(3));

		final var returnedItem = items.get(0);

		assertThat(returnedItem.getId(), is("FAKE_ID_0"));
		assertThat(returnedItem.getBarcode(), is("FAKE_BARCODE"));
		assertThat(returnedItem.getCallNumber(), is("FAKE_CALL_NUMBER"));
		assertThat(returnedItem.getDueDate(), is(nullValue()));

		final var itemStatus = returnedItem.getStatus();

		assertThat(itemStatus.getCode(), is(AVAILABLE));

		final var itemLocation = returnedItem.getLocation();

		assertThat(itemLocation.getCode(), is("FAKE_LOCATION_CODE"));
		assertThat(itemLocation.getName(), is("FAKE_LOCATION_NAME"));
	}
}
