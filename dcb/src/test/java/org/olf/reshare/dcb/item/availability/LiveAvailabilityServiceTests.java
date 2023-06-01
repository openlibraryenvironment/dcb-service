package org.olf.reshare.dcb.item.availability;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.AVAILABLE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.olf.reshare.dcb.core.HostLmsService;
import org.olf.reshare.dcb.core.interaction.HostLmsClient;
import org.olf.reshare.dcb.core.interaction.sierra.SierraLmsClient;
import org.olf.reshare.dcb.core.model.FakeHostLms;
import org.olf.reshare.dcb.core.model.HostLms;
import org.olf.reshare.dcb.core.model.Item;
import org.olf.reshare.dcb.core.model.ItemStatus;
import org.olf.reshare.dcb.core.model.Location;
import org.olf.reshare.dcb.request.resolution.Bib;
import org.olf.reshare.dcb.request.resolution.ClusteredBib;
import org.olf.reshare.dcb.request.resolution.SharedIndexService;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import reactor.core.publisher.Mono;
import services.k_int.interaction.sierra.SierraError;

public class LiveAvailabilityServiceTests {
	private final HostLmsService hostLmsService = mock(HostLmsService.class);
	private final SharedIndexService sharedIndexService = mock(SharedIndexService.class);
	private final RequestableItemService requestableItemService = mock(RequestableItemService.class);

	private final LiveAvailabilityService liveAvailabilityService =
		new LiveAvailabilityService(hostLmsService, requestableItemService);

	@Test
	void shouldGetAvailableItemsViaHostLmsService() {
		final var hostLms = createHostLms("hostLmsCode");

		final var clusterRecordId = randomUUID();

		final var clusterRecord = new ClusteredBib(clusterRecordId, "title",
			List.of(createBib(hostLms, "bibRecordId")));

		when(sharedIndexService.findClusteredBib(clusterRecordId))
			.thenReturn(Mono.just(clusterRecord));

		final var client = mock(HostLmsClient.class);

		when(hostLmsService.getClientFor(hostLms))
			.thenReturn(Mono.just(client));

		final var dueDate = ZonedDateTime.now();

		final Item item = createItem(dueDate);

		final var listOfItems = List.of(item);

		when(client.getItemsByBibId("bibRecordId", "hostLmsCode"))
			.thenReturn(Mono.just(listOfItems));

		when(requestableItemService.determineRequestable(List.of(item)))
			.thenAnswer(invocation -> listOfItems);

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
		assertThat(onlyItem.getHoldCount(), is(0));

		final var status = onlyItem.getStatus();

		assertThat(status, is(notNullValue()));
		assertThat(status.getCode(), is(AVAILABLE));

		final var location = onlyItem.getLocation();

		assertThat(location, is(notNullValue()));
		assertThat(location.getCode(), is("testLocationCode"));
		assertThat(location.getName(), is("testLocationName"));

		verify(hostLmsService).getClientFor(hostLms);
		verify(client).getItemsByBibId(any(), any());
	}

	@Test
	void shouldTolerateFailuresFetchingItems() {
		// Arrange
		final var workingHostLms = createHostLms("workingHostLms");
		final var brokenHostLms = createHostLms("failingHostLms");

		final var clusterRecordId = randomUUID();

		final var clusterRecord = new ClusteredBib(clusterRecordId, "title",
			List.of(createBib(workingHostLms, "workingBib"),
				createBib(brokenHostLms, "failingBib")));

		when(sharedIndexService.findClusteredBib(clusterRecordId))
			.thenReturn(Mono.just(clusterRecord));

		final var workingClient = mock(HostLmsClient.class);

		when(hostLmsService.getClientFor(workingHostLms))
			.thenReturn(Mono.just(workingClient));

		final var brokenClient = mock(HostLmsClient.class);

		when(hostLmsService.getClientFor(brokenHostLms))
			.thenReturn(Mono.just(brokenClient));

		final var dueDate = ZonedDateTime.now();

		final Item item = createItem(dueDate);

		when(workingClient.getItemsByBibId("workingBib", "workingHostLms"))
			.thenReturn(Mono.just(List.of(item)));

		when(brokenClient.getItemsByBibId("failingBib", "failingHostLms"))
			.thenReturn(Mono.error(new HttpClientResponseException("",
				HttpResponse.serverError().body(createSierraError()))));

		when(requestableItemService.determineRequestable(any()))
			.thenReturn(List.of(item));

		// Act
		final var items = liveAvailabilityService.getAvailableItems(clusterRecord).block();

		// Assert
		assertThat("Items returned should not be null", items, is(notNullValue()));
		assertThat("Should have some items", items, hasSize(1));
		assertThat("Should have item from working client", items.get(0), is(item));
	}

	@Test
	void shouldFailWhenHostLMSIsNull() {
		final var clusterRecordId = randomUUID();

		final var clusterRecord = new ClusteredBib(clusterRecordId, "title",
			List.of(createBib(null, "bibRecordId")));

		when(sharedIndexService.findClusteredBib(clusterRecordId))
			.thenReturn(Mono.just(clusterRecord));

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
			.thenReturn(Mono.just(clusterRecordWithNullBibs));

		final var exception = assertThrows(IllegalArgumentException.class,
			() -> liveAvailabilityService.getAvailableItems(clusterRecordWithNullBibs)
				.block());

		assertThat(exception, is(notNullValue()));
		assertThat(exception.getMessage(), is("Bibs cannot be null"));
	}

	private static Bib createBib(FakeHostLms hostLms, String bibRecordId) {
		return Bib.builder()
			.id(randomUUID())
			.bibRecordId(bibRecordId)
			.hostLms(hostLms)
			.build();
	}

	private static Item createItem(ZonedDateTime dueDate) {
		return new Item("testid", new ItemStatus(AVAILABLE), dueDate,
			Location.builder().code("testLocationCode").name("testLocationName").build(),
			"testBarcode", "testCallNumber",
			"hostLmsCode", true,
			0);
	}

	private static FakeHostLms createHostLms(String code) {
		return new FakeHostLms(randomUUID(), code,
			"Fake Host LMS", SierraLmsClient.class, Map.of());
	}

	private static SierraError createSierraError() {
		return new SierraError("", 654, 0, "", "");
	}
}
