package org.olf.dcb.core.svc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.Workflow;
import org.olf.dcb.storage.LocationRepository;
import reactor.core.publisher.Mono;

class LocationServiceTests {
	@Test
	void memoizeCanCreateDynamicLocationFromBuilderImmutableWorkflowMap() {
		LocationRepository locationRepository = mock(LocationRepository.class);
		LocationService locationService = new LocationService(locationRepository);
		when(locationRepository.findById(any(UUID.class)))
			.thenReturn(Mono.empty());
		when(locationRepository.save(any(Location.class)))
			.thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

		Location saved = singleValueFrom(locationService.memoize(Location.builder()
			.code("study")
			.name("Study")
			.type("UNKNOWN")
			.agency(DataAgency.builder().code("ors-unseen").build())
			.hostSystem(DataHostLms.builder().code("ors-host").build())
			.activeWorkflow("Existing", Workflow.builder().code("Existing").status("DONE").build())
			.build()));

		assertThat(saved.getNeedsAttention(), is(Boolean.TRUE));
		assertThat(saved.getActiveWorkflows().containsKey("Existing"), is(true));
		assertThat(saved.getActiveWorkflows().containsKey("DynamicLocation"), is(true));
	}
}
