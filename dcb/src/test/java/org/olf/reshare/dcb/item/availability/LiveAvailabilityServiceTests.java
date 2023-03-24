package org.olf.reshare.dcb.item.availability;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.interaction.Item;
import org.olf.reshare.dcb.core.interaction.Status;
import org.olf.reshare.dcb.core.model.HostLms;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class LiveAvailabilityServiceTests {
	@Test
	void shouldGetAvailableItemsViaHostLmsService() {
		final var hostLmsService = mock(HostLmsService.class);
		final var hostLmsClient = mock(HostLmsClient.class);
		final var hostLms = mock(HostLms.class);

		final var liveAvailabilityService = new LiveAvailabilityService(hostLmsService);

		when(hostLmsService.findByCode("testLmsCode"))
			.thenAnswer(invocation -> Mono.just(hostLms));

		when(hostLmsService.getClientFor(hostLms))
			.thenAnswer(invocation -> Mono.just(hostLmsClient));

		final var item = new Item("testid",
			new Status("testCode", "testText", "testDate"));

		when(hostLmsClient.getAllItemDataByBibRecordId("testBibId"))
			.thenAnswer(invocation -> Flux.just(item));

		final var items = liveAvailabilityService.getAvailableItems("testBibId",
				"testLmsCode").block();

		assertThat(items, is(notNullValue()));
		assertThat(items.size(), is(1));

		final var onlyItem = items.get(0);

		assertThat(onlyItem.getId(), is("testid"));

		final var status = onlyItem.getStatus();

		assertThat(status, is(notNullValue()));
		assertThat(status.getCode(), is("testCode"));
		assertThat(status.getDisplayText(), is("testText"));
		assertThat(status.getDueDate(), is("testDate"));

		verify(hostLmsService).findByCode(any());
		verify(hostLmsService).getClientFor(any());
		verify(hostLmsClient).getAllItemDataByBibRecordId(any());
	}
}
