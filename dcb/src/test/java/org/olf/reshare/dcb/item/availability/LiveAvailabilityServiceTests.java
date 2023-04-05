package org.olf.reshare.dcb.item.availability;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.interaction.Item;
import org.olf.reshare.dcb.core.interaction.Location;
import org.olf.reshare.dcb.core.interaction.Status;
import org.olf.reshare.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.reshare.dcb.core.model.FakeHostLms;

import reactor.core.publisher.Mono;

public class LiveAvailabilityServiceTests {
	private final HostLmsService hostLmsService = mock(HostLmsService.class);
	private final HostLmsClient hostLmsClient = mock(HostLmsClient.class);
	private final LiveAvailabilityService liveAvailabilityService = new LiveAvailabilityService(hostLmsService);

	@Test
	void shouldGetAvailableItemsViaHostLmsService() {
		final var hostLms = new FakeHostLms(randomUUID(), "hostLmsCode", "Fake Host LMS",
			SierraLmsClient.class, Map.of());

		when(hostLmsService.getClientFor(hostLms))
			.thenAnswer(invocation -> Mono.just(hostLmsClient));

		final var item = new Item("testid",
			new Status("testCode", "testText", "testDate"),
			new Location("testLocationCode", "testLocationName"),
			"testBarcode", "testCallNumber", "hostLmsCode");

		when(hostLmsClient.getItemsByBibId("testBibId", "hostLmsCode"))
			.thenAnswer(invocation -> Mono.just(List.of(item)));

		final var items = liveAvailabilityService
			.getAvailableItems("testBibId", hostLms).block();

		assertThat(items, is(notNullValue()));
		assertThat(items.size(), is(1));

		final var onlyItem = items.get(0);

		assertThat(onlyItem.getId(), is("testid"));
		assertThat(onlyItem.getBarcode(), is("testBarcode"));
		assertThat(onlyItem.getCallNumber(), is("testCallNumber"));
		assertThat(onlyItem.getHostLmsCode(), is("hostLmsCode"));

		final var status = onlyItem.getStatus();

		assertThat(status, is(notNullValue()));
		assertThat(status.getCode(), is("testCode"));
		assertThat(status.getDisplayText(), is("testText"));
		assertThat(status.getDueDate(), is("testDate"));

		final var location = onlyItem.getLocation();

		assertThat(location, is(notNullValue()));
		assertThat(location.getCode(), is("testLocationCode"));
		assertThat(location.getName(), is("testLocationName"));

		verify(hostLmsService).getClientFor(hostLms);
		verify(hostLmsClient).getItemsByBibId(any(), any());
	}

	@Test
	void shouldFailWhenHostLMSIsNull() {
		final var exception = assertThrows(IllegalArgumentException.class,
			() -> liveAvailabilityService.getAvailableItems("34356576", null)
				.block());

		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(), is("hostLMS cannot be null"));
	}
}
