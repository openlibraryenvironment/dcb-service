package org.olf.dcb.core.svc;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.olf.dcb.core.model.DataAgency;
import org.olf.dcb.core.model.DataHostLms;
import org.olf.dcb.core.model.Location;
import org.olf.dcb.core.model.Workflow;
import org.olf.dcb.storage.LocationRepository;

import reactor.core.publisher.Mono;

/**
 * Recording the locations DCB has seen.
 * <p>
 * memoize existed for years and never created anything: it required the reported
 * location to carry an agency, and no adapter has ever set one. It therefore rejected
 * exactly the unmapped locations its own javadoc said it existed to capture, which is
 * why onboarding a shared system meant enumerating its branches by hand.
 */
class LocationServiceTests {
	private static final DataHostLms SHARED_KOHA = DataHostLms.builder()
		.id(UUID.randomUUID())
		.code("shared-koha")
		.build();

	private LocationRepository locationRepository;
	private LocationService locationService;

	@BeforeEach
	void beforeEach() {
		locationRepository = mock(LocationRepository.class);
		locationService = new LocationService(locationRepository);

		when(locationRepository.findOneByHostSystemAndCode(any(), any()))
			.thenReturn(Mono.empty());

		when(locationRepository.save(any(Location.class)))
			.thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
	}

	@Test
	void shouldRecordALocationThatDidNotResolveToAnAgency() {
		// The case the whole thing exists for. An unmapped branch is what an operator
		// needs to be told about; a mapped one DCB already understands.
		final var recorded = memoize(reportedLocation("BRANCH-NORTH"), null);

		assertThat(recorded, is(notNullValue()));
		assertThat(recorded.getAgency(), is(nullValue()));
		assertThat(recorded.getNeedsAttention(), is(Boolean.TRUE));
		assertThat(recorded.getActiveWorkflows().containsKey("DynamicLocation"), is(true));
		assertThat(recorded.getHostSystem().getCode(), is("shared-koha"));
	}

	@Test
	void shouldRecordALocationThatResolvedWithoutFlaggingItForReview() {
		// Every location an ILS reports arrives here the first time it is seen, and on
		// an established system most of them map perfectly well. Flagging those too
		// would bury the handful that need a mapping under everything that does not.
		final var agency = DataAgency.builder().code("north-library").build();

		final var recorded = memoize(reportedLocation("BRANCH-NORTH"), agency);

		assertThat(recorded.getAgency().getCode(), is("north-library"));
		assertThat(recorded.getNeedsAttention(), is(nullValue()));
		assertThat(recorded.getActiveWorkflows().containsKey("DynamicLocation"), is(false));
	}

	@Test
	void shouldFillInTheFieldsAnItemMapperDoesNotReport() {
		// An adapter reports little more than a code, and name and type are non-null in
		// the schema - so a save would fail validation rather than record anything.
		final var recorded = memoize(Location.builder().code("BRANCH-NORTH").build(), null);

		assertThat(recorded.getName(), is("BRANCH-NORTH"));
		assertThat(recorded.getType(), is("UNKNOWN"));
	}

	@Test
	void shouldNotWriteAgainWhenTheLocationIsAlreadyKnown() {
		final var existing = Location.builder()
			.code("BRANCH-NORTH")
			.name("North Branch")
			.type("Library")
			.hostSystem(SHARED_KOHA)
			.build();

		when(locationRepository.findOneByHostSystemAndCode(any(), any()))
			.thenReturn(Mono.just(existing));

		final var recorded = memoize(reportedLocation("BRANCH-NORTH"), null);

		assertThat(recorded.getName(), is("North Branch"));
		verify(locationRepository, never()).save(any());
	}

	@Test
	void shouldNotMutateTheReportedLocation() {
		// The reported location belongs to an Item on the availability path and is about
		// to be serialised into the response. Recording it must not rewrite it.
		final var reported = reportedLocation("BRANCH-NORTH");

		memoize(reported, DataAgency.builder().code("north-library").build());

		assertThat(reported.getId(), is(nullValue()));
		assertThat(reported.getAgency(), is(nullValue()));
		assertThat(reported.getHostSystem(), is(nullValue()));
	}

	@Test
	void shouldIgnoreALocationWithNothingToIdentifyIt() {
		assertThat(singleValueFrom(locationService.memoize(null, null, SHARED_KOHA)),
			is(nullValue()));

		assertThat(singleValueFrom(locationService.memoize(
				Location.builder().name("No code").build(), null, SHARED_KOHA)),
			is(nullValue()));

		// A location code means nothing without the system that reported it
		assertThat(singleValueFrom(locationService.memoize(
				reportedLocation("BRANCH-NORTH"), null, null)),
			is(nullValue()));

		verify(locationRepository, never()).save(any());
	}

	@Test
	void shouldNotReadTheSameLocationTwice() {
		// This sits on the per-item availability path. Without the cache, remembering a
		// location costs a select per item per check for ever - the first check does the
		// useful work and every one after it repeats a query that cannot have changed.
		memoize(reportedLocation("BRANCH-NORTH"), null);
		memoize(reportedLocation("BRANCH-NORTH"), null);
		memoize(reportedLocation("BRANCH-NORTH"), null);

		verify(locationRepository, times(1)).findOneByHostSystemAndCode(any(), any());
		verify(locationRepository, times(1)).save(any());
	}

	@Test
	void shouldKeepWorkflowsAlreadyOnTheReportedLocation() {
		// dynamicCreateLocation copies into a fresh map because Lombok's @Singular
		// builder produces an immutable one
		final var reported = Location.builder()
			.code("BRANCH-NORTH")
			.name("North Branch")
			.type("Library")
			.activeWorkflow("Existing", Workflow.builder().code("Existing").status("DONE").build())
			.build();

		final var recorded = memoize(reported, null);

		assertThat(recorded.getActiveWorkflows().containsKey("Existing"), is(true));
		assertThat(recorded.getActiveWorkflows().containsKey("DynamicLocation"), is(true));
	}

	private Location memoize(Location reported, DataAgency agency) {
		return singleValueFrom(locationService.memoize(reported, agency, SHARED_KOHA));
	}

	private static Location reportedLocation(String code) {
		return Location.builder()
			.code(code)
			.name(code)
			.type("Library")
			.build();
	}
}
