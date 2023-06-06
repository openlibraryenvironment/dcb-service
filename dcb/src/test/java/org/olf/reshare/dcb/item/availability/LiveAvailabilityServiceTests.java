package org.olf.reshare.dcb.item.availability;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.olf.reshare.dcb.core.model.ItemStatusCode.AVAILABLE;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
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

class LiveAvailabilityServiceTests {
	private final HostLmsService hostLmsService = mock(HostLmsService.class);
	private final SharedIndexService sharedIndexService = mock(SharedIndexService.class);
	private final RequestableItemService requestableItemService = mock(RequestableItemService.class);

	private final LiveAvailabilityService liveAvailabilityService =
		new LiveAvailabilityService(hostLmsService, requestableItemService);

	@Test
	@DisplayName("Should get items from host LMS for multiple bibs")
	void shouldGetAvailableItemsForMultipleBibs() {
		// Arrange
		final var firstHostLms = createHostLms("firstHost");
		final var secondHostLms = createHostLms("secondHost");

		final var clusterRecordId = randomUUID();

		final var clusterRecord = new ClusteredBib(clusterRecordId, "title",
			List.of(createBib(firstHostLms, "firstHostBibRecord"),
				createBib(secondHostLms, "secondHostBibRecord")));

		when(sharedIndexService.findClusteredBib(clusterRecordId))
			.thenReturn(Mono.just(clusterRecord));

		final var firstHostClient = mock(HostLmsClient.class);

		when(hostLmsService.getClientFor(firstHostLms))
			.thenReturn(Mono.just(firstHostClient));

		final var itemFromFirstHost = createItem("firstItemId", "BCF543");

		when(firstHostClient.getItemsByBibId("firstHostBibRecord", "firstHost"))
			.thenReturn(Mono.just(List.of(itemFromFirstHost)));

		final var secondHostClient = mock(HostLmsClient.class);

		when(hostLmsService.getClientFor(secondHostLms))
			.thenReturn(Mono.just(secondHostClient));

		final var itemFromSecondHost = createItem("secondItemId", "ABC123");

		when(secondHostClient.getItemsByBibId("secondHostBibRecord", "secondHost"))
			.thenReturn(Mono.just(List.of(itemFromSecondHost)));

		when(requestableItemService.isRequestable(any()))
			.thenReturn(true);

		// Act
		final var report = liveAvailabilityService
			.getAvailableItems(clusterRecord).block();

		// Assert
		assertThat("Report should not be null", report, is(notNullValue()));

		final var items = report.getItems();

		assertThat("Items returned should not be null", items, is(notNullValue()));
		assertThat("Should have two items", items, hasSize(2));

		// Relies on instance matching which will could fail
		// if we decide to return new items instead of setting values
		assertThat("Should have expected item in natural sort order",
			items, contains(itemFromSecondHost, itemFromFirstHost));

		verify(hostLmsService).getClientFor(firstHostLms);
		verify(hostLmsService).getClientFor(secondHostLms);
		verify(firstHostClient).getItemsByBibId("firstHostBibRecord", "firstHost");
		verify(secondHostClient).getItemsByBibId("secondHostBibRecord", "secondHost");

		verifyNoMoreInteractions(hostLmsService, firstHostClient, secondHostClient);
	}

	@Test
	@DisplayName("Should report failures when fetching items from host LMS")
	void shouldReportFailuresFetchingItemsFromHostLms() {
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

		final Item item = createItem("itemId", "ABC123");

		when(workingClient.getItemsByBibId("workingBib", "workingHostLms"))
			.thenReturn(Mono.just(List.of(item)));

		when(brokenClient.getItemsByBibId("failingBib", "failingHostLms"))
			.thenReturn(Mono.error(new HttpClientResponseException("",
				HttpResponse.serverError().body(createSierraError()))));

		when(requestableItemService.isRequestable(item))
			.thenReturn(true);

		// Act
		final var report = liveAvailabilityService
			.getAvailableItems(clusterRecord).block();

		// Assert
		assertThat("Report should not be null", report, is(notNullValue()));

		final var items = report.getItems();

		assertThat("Items returned should not be null", items, is(notNullValue()));
		assertThat("Should have one item", items, hasSize(1));
		assertThat("Should have item from working client", items.get(0), is(item));

		final var errors = report.getErrors();

		assertThat("Reported errors should not be null", errors, is(notNullValue()));
		assertThat("Should have one error", errors, hasSize(1));
		assertThat("Should have one error", errors.get(0).getMessage(),
			is("Failed to fetch items for bib: failingBib from host: failingHostLms"));
	}

	@Test
	@DisplayName("Should fail when host LMS for bib is null")
	void shouldFailWhenHostLMSForBibIsNull() {
		// Arrange
		final var clusterRecordId = randomUUID();

		final var clusterRecord = new ClusteredBib(clusterRecordId, "title",
			List.of(createBib(null, "bibRecordId")));

		when(sharedIndexService.findClusteredBib(clusterRecordId))
			.thenReturn(Mono.just(clusterRecord));

		// Act
		final var exception = assertThrows(IllegalArgumentException.class,
			() -> liveAvailabilityService.getAvailableItems(clusterRecord).block());

		// Assert
		assertThat("Exception should not be null", exception, is(notNullValue()));
		assertThat(exception.getMessage(), is("hostLMS cannot be null"));
	}

	@Test
	@DisplayName("Should find no items when no bibs in cluster record")
	void shouldFindNoItemsWhenNoBibsInClusterRecord() {
		// Arrange
		final var clusterWithNoBibs = new ClusteredBib(randomUUID(), "title", List.of());

		// Act
		final var report = liveAvailabilityService
			.getAvailableItems(clusterWithNoBibs).block();

		// Assert
		assertThat("Report should not be null", report, is(notNullValue()));

		final var items = report.getItems();

		assertThat("Items returned should not be null", items, is(notNullValue()));
		assertThat("Should have no items", items, hasSize(0));

		// if there are no bibs in clustered bib, the host LMS service should not be called
		verify(hostLmsService, never()).getClientFor(any(HostLms.class));
	}

	@Test
	@DisplayName("Should find no items when cluster record bibs is null")
	void ShouldFindNoItemsWhenClusterRecordBibsIsNull() {
		// Arrange
		final var clusterRecordId = randomUUID();

		final var clusterWithNullBibs = new ClusteredBib(clusterRecordId, "title", null);

		when(sharedIndexService.findClusteredBib(clusterRecordId))
			.thenReturn(Mono.just(clusterWithNullBibs));

		// Act
		final var exception = assertThrows(IllegalArgumentException.class,
			() -> liveAvailabilityService.getAvailableItems(clusterWithNullBibs).block());

		// Assert
		assertThat("Exception should not be null", exception, is(notNullValue()));
		assertThat(exception.getMessage(), is("Bibs cannot be null"));
	}

	private static Bib createBib(FakeHostLms hostLms, String bibRecordId) {
		return Bib.builder()
			.id(randomUUID())
			.bibRecordId(bibRecordId)
			.hostLms(hostLms)
			.build();
	}

	private static Item createItem(String itemId, String locationCode) {
		return Item.builder()
			.id(itemId)
			.status(new ItemStatus(AVAILABLE))
			.location(Location.builder()
				.code(locationCode)
				.build())
			.isRequestable(true)
			.holdCount(0)
			.build();
	}

	private static FakeHostLms createHostLms(String code) {
		return new FakeHostLms(randomUUID(), code,
			"Fake Host LMS", SierraLmsClient.class, Map.of());
	}

	private static SierraError createSierraError() {
		return new SierraError("", 654, 0, "", "");
	}
}
