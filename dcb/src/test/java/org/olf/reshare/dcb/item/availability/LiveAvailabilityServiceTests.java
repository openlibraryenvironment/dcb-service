package org.olf.reshare.dcb.item.availability;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.AVAILABLE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.reshare.dcb.core.model.*;
import org.olf.reshare.dcb.request.resolution.Bib;
import org.olf.reshare.dcb.request.resolution.ClusteredBib;

import org.olf.reshare.dcb.request.resolution.SharedIndexService;
import reactor.core.publisher.Mono;

public class LiveAvailabilityServiceTests {
	private final HostLmsService hostLmsService = mock(HostLmsService.class);
	private final SharedIndexService sharedIndexService = mock(SharedIndexService.class);
	private final HostLmsClient hostLmsClient = mock(HostLmsClient.class);

	private final LiveAvailabilityService liveAvailabilityService =
		new LiveAvailabilityService(hostLmsService);

	@Test
	void shouldGetAvailableItemsViaHostLmsService() {
		final var hostLms = new FakeHostLms(randomUUID(), "hostLmsCode",
			"Fake Host LMS", SierraLmsClient.class, Map.of());

		final var clusterRecordId = randomUUID();

		final var clusterRecord = new ClusteredBib(clusterRecordId, "title",
			List.of(new Bib(randomUUID(), "bibRecordId", hostLms)));

		when(sharedIndexService.findClusteredBib(clusterRecordId))
			.thenAnswer(invocation -> Mono.just(clusterRecord));

		when(hostLmsService.getClientFor(hostLms))
			.thenAnswer(invocation -> Mono.just(hostLmsClient));

		final var dueDate = ZonedDateTime.now();

		final var item = new Item("testid", new ItemStatus(AVAILABLE), dueDate,
			Location.builder().code("testLocationCode").name("testLocationName").build(),
			"testBarcode", "testCallNumber", "hostLmsCode");

		when(hostLmsClient.getItemsByBibId("bibRecordId", "hostLmsCode"))
			.thenAnswer(invocation -> Mono.just(List.of(item)));

		final var items = liveAvailabilityService
			.getAvailableItems(clusterRecord).block();

		assertThat(items, is(notNullValue()));
		assertThat(items.size(), is(1));

		final var onlyItem = items.get(0);

		assertThat(onlyItem.getId(), is("testid"));
		assertThat(onlyItem.getBarcode(), is("testBarcode"));
		assertThat(onlyItem.getCallNumber(), is("testCallNumber"));
		assertThat(onlyItem.getHostLmsCode(), is("hostLmsCode"));
		assertThat(onlyItem.getDueDate(), is(dueDate));

		final var status = onlyItem.getStatus();

		assertThat(status, is(notNullValue()));
		assertThat(status.getCode(), is(AVAILABLE));

		final var location = onlyItem.getLocation();

		assertThat(location, is(notNullValue()));
		assertThat(location.getCode(), is("testLocationCode"));
		assertThat(location.getName(), is("testLocationName"));

		verify(hostLmsService).getClientFor(hostLms);
		verify(hostLmsClient).getItemsByBibId(any(), any());
	}

	@Test
	void shouldFailWhenHostLMSIsNull() {
		final var clusterRecordId = randomUUID();

		final var clusterRecord = new ClusteredBib(clusterRecordId, "title",
			List.of(new Bib(randomUUID(), "bibRecordId", null)));

		when(sharedIndexService.findClusteredBib(clusterRecordId))
			.thenAnswer(invocation -> Mono.just(clusterRecord));

		final var exception = assertThrows(IllegalArgumentException.class,
			() -> liveAvailabilityService.getAvailableItems(clusterRecord)
				.block());

		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(), is("hostLMS cannot be null"));
	}

	@Test
	void noBibsInClusteredBibWillReturnEmptyItemList() {

		final var clusterRecordWithNoBibs = new ClusteredBib(randomUUID(), "title", List.of());

		final var items = liveAvailabilityService
			.getAvailableItems(clusterRecordWithNoBibs).block();

		assertThat(items, is(notNullValue()));
		assertThat(items.size(), is(0));

		// if there are no bibs in clustered bib the hostlms service will not be called
		verify(hostLmsService, times(0)).getClientFor(any(HostLms.class));
	}

	@Test
	void nullBibsInClusteredBibWillReturnEmptyItemList() {
		final var clusterRecordId = randomUUID();

		final var clusterRecordWithNullBibs = new ClusteredBib(clusterRecordId, "title", null);

		when(sharedIndexService.findClusteredBib(clusterRecordId))
			.thenAnswer(invocation -> Mono.just(clusterRecordWithNullBibs));

		final var exception = assertThrows(IllegalArgumentException.class,
			() -> liveAvailabilityService.getAvailableItems(clusterRecordWithNullBibs)
				.block());

		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(), is("Bibs cannot be null"));
	}
}
