package org.olf.reshare.dcb.request.resolution.fake;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class FakeLiveAvailabilityServiceTests {
	@Test
	void shouldAlwaysReturnListOfItems() {
		final var fakeLiveAvailabilityService = new FakeLiveAvailabilityService();

		final var bibRecordId = UUID.randomUUID().toString();
		final var hostLmsCode = "hostLmsCode";

		final var returnedListOfItems =
			fakeLiveAvailabilityService.getAvailableItems(bibRecordId, hostLmsCode).block();

		assertThat(returnedListOfItems, is(notNullValue()));
		assertThat(returnedListOfItems.size(), is(3));

		final var returnedItem = returnedListOfItems.get(0);

		assertThat(returnedItem.getId(), is("FAKE_ID_0"));
		assertThat(returnedItem.getBarcode(), is("FAKE_BARCODE"));
		assertThat(returnedItem.getCallNumber(), is("FAKE_CALL_NUMBER"));

		final var itemStatus = returnedItem.getStatus();

		assertThat(itemStatus.getCode(), is("FAKE_STATUS_CODE"));
		assertThat(itemStatus.getDisplayText(), is("FAKE_STATUS_DISPLAY_TEXT"));
		assertThat(itemStatus.getDueDate(), is("FAKE_STATUS_DUE_DATE"));

		final var itemLocation = returnedItem.getLocation();

		assertThat(itemLocation.getCode(), is("FAKE_LOCATION_CODE"));
		assertThat(itemLocation.getName(), is("FAKE_LOCATION_NAME"));
	}
}
