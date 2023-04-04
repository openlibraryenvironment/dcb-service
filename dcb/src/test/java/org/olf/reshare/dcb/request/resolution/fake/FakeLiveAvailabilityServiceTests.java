package org.olf.reshare.dcb.request.resolution.fake;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.reshare.dcb.core.model.FakeHostLms;

public class FakeLiveAvailabilityServiceTests {
	@Test
	void shouldAlwaysReturnListOfItems() {
		final var fakeLiveAvailabilityService = new FakeLiveAvailabilityService();

		final var bibRecordId = randomUUID().toString();
		final var hostLmsCode = "hostLmsCode";

		final var hostLms = new FakeHostLms(randomUUID(), hostLmsCode, "Fake Host LMS",
			SierraLmsClient.class, Map.of());

		final var items = fakeLiveAvailabilityService
			.getAvailableItems(bibRecordId, hostLms).block();

		assertThat(items, is(notNullValue()));
		assertThat(items.size(), is(3));

		final var returnedItem = items.get(0);

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
