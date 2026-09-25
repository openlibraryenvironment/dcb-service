package org.olf.dcb.item.availability;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.olf.dcb.availability.job.AvailabilityCheckJob;
import org.olf.dcb.core.HostLmsService;
import org.olf.dcb.core.interaction.HostLmsClient;
import org.olf.dcb.core.model.BibRecord;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Item;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.svc.LocationService;
import org.olf.dcb.request.resolution.SharedIndexService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.context.BeanProvider;
import reactor.core.publisher.Mono;

class LiveAvailabilityServiceBackfillTests {
	private final HostLmsService hostLmsService = mock(HostLmsService.class);
	private final LocationService locationService = mock(LocationService.class);
	private final HostLmsClient hostLmsClient = mock(HostLmsClient.class);

	@SuppressWarnings("unchecked")
	private final BeanProvider<AvailabilityCheckJob> availability = mock(BeanProvider.class);

	private final LiveAvailabilityService service = new LiveAvailabilityService(hostLmsService,
		mock(RequestableItemService.class), mock(SharedIndexService.class), locationService,
		new SimpleMeterRegistry(), availability);

	@Test
	void backfillDoesNotRecordLocationsOrPopulateTheLiveCache() {
		final var sourceSystem = DataHostLms.builder()
			.id(UUID.randomUUID())
			.code("source")
			.build();
		final var bib = BibRecord.builder()
			.id(UUID.randomUUID())
			.sourceSystemId(sourceSystem.getId())
			.sourceRecordId("record")
			.build();
		final var item = Item.builder()
			.location(Location.builder().code("BRANCH-NORTH").build())
			.build();

		when(hostLmsService.findById(sourceSystem.getId())).thenReturn(Mono.just(sourceSystem));
		when(hostLmsService.getClientFor(sourceSystem)).thenReturn(Mono.just(hostLmsClient));
		when(hostLmsClient.getHostLmsCode()).thenReturn(sourceSystem.getCode());
		when(hostLmsClient.getItems(bib)).thenReturn(Mono.just(List.of(item)));

		singleValueFrom(service.fetchBibAvailabilityForBackfill(bib, Duration.ofSeconds(1), "all"));

		verify(locationService, never()).memoize(any(), any(), any());
		assertThat(service.availabilityCache.getIfPresent(bib.getId().toString()), nullValue());
	}
}
