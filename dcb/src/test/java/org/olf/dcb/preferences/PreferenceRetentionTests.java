package org.olf.dcb.preferences;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.olf.dcb.test.PublisherUtils.singleValueFrom;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.olf.dcb.core.model.AdminSavedView;
import org.olf.dcb.core.model.PatronRequest;
import org.olf.dcb.storage.AdminPreferenceRepository;
import org.olf.dcb.storage.AdminSavedViewRepository;
import org.olf.dcb.test.DcbTest;
import org.olf.dcb.test.PatronRequestsFixture;

import jakarta.inject.Inject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Retention is measured from last use; watches also go when their request goes or ended long ago. */
@DcbTest
@TestInstance(PER_CLASS)
class PreferenceRetentionTests {
	private static final String DORMANT = "retention-tests-dormant-key";
	private static final String ACTIVE = "retention-tests-active-key";

	@Inject
	private AdminPreferenceRepository preferences;
	@Inject
	private AdminSavedViewRepository savedViews;
	@Inject
	private PatronRequestsFixture patronRequestsFixture;

	private final Instant chosen = Instant.parse("2024-01-01T00:00:00Z");

	@BeforeEach
	void beforeEach() {
		forget();
		patronRequestsFixture.deleteAll();
	}

	@AfterAll
	void afterAll() {
		forget();
		patronRequestsFixture.deleteAll();
	}

	@Test
	void someoneWhoStillUsesTheAppIsNeverSweptHoweverOldTheirChoice() {
		storedAt(ACTIVE, chosen);
		storedAt(DORMANT, chosen);
		final var twoYearsOn = chosen.plus(Duration.ofDays(730));
		singleValueFrom(preferences.touch(ACTIVE, twoYearsOn, twoYearsOn));

		assertThat(sweepAt(twoYearsOn, 500), is(1L));
		assertThat(isStored(ACTIVE), is(true));
		assertThat(isStored(DORMANT), is(false));
	}

	@Test
	void nobodyIsSweptInsideTheRetentionPeriod() {
		storedAt(DORMANT, chosen);

		assertThat(sweepAt(chosen.plus(Duration.ofDays(334)), 500), is(0L));
		assertThat(isStored(DORMANT), is(true));
	}

	@Test
	void sweepingSomeoneTakesTheirSavedFiltersAndWatchesWithThem() {
		storedAt(DORMANT, chosen);
		singleValueFrom(savedViews.save(AdminSavedView.builder()
			.id(randomUUID()).adminKey(DORMANT).name("Long forgotten").viewType("libraries.borrowing")
			.queryState(Map.of()).dateCreated(chosen).dateUpdated(chosen).build()));
		singleValueFrom(preferences.watch(DORMANT, liveRequest(), chosen));

		sweepAt(chosen.plus(Duration.ofDays(730)), 500);

		assertThat(singleValueFrom(savedViews.countByAdminKey(DORMANT)), is(0L));
		assertThat(singleValueFrom(preferences.countWatched(DORMANT)), is(0L));
	}

	@Test
	void theSweepKeepsGoingPastOneBatch() {
		storedAt(DORMANT, chosen);
		storedAt(ACTIVE, chosen);

		assertThat(sweepAt(chosen.plus(Duration.ofDays(730)), 1), is(2L));
	}

	@Test
	void aWatchWhoseRequestHasGoneOrFinishedLongAgoIsSweptAndOthersAreKept() {
		final var now = Instant.now();
		storedAt(ACTIVE, now);
		final var live = liveRequest();
		final var finishedLongAgo = request(PatronRequest.Status.FINALISED, now.minus(Duration.ofDays(120)));
		final var finishedLately = request(PatronRequest.Status.COMPLETED, now.minus(Duration.ofDays(10)));
		final var gone = randomUUID();
		for (UUID id : new UUID[] { live, finishedLongAgo, finishedLately, gone }) {
			singleValueFrom(preferences.watch(ACTIVE, id, now));
		}

		final var removed = singleValueFrom(job(now, 1).sweep());

		assertThat(removed.getT2(), is(2L));
		assertThat(watchedIds(ACTIVE), is(Set.of(live, finishedLately)));
	}

	private UUID liveRequest() {
		return request(PatronRequest.Status.LOANED, Instant.now());
	}

	private UUID request(PatronRequest.Status status, Instant since) {
		return patronRequestsFixture.savePatronRequest(PatronRequest.builder()
			.id(randomUUID())
			.status(status)
			.currentStatusTimestamp(since)
			.build()).getId();
	}

	private void storedAt(String adminKey, Instant when) {
		singleValueFrom(preferences.upsertDisplay(adminKey, "dark", null, null, null, null, null, when));
	}

	private long sweepAt(Instant now, int batchSize) {
		return singleValueFrom(job(now, batchSize).sweep()).getT1();
	}

	private PreferenceRetentionJob job(Instant now, int batchSize) {
		final var config = new PreferencesConfig();
		config.setSweepBatchSize(batchSize);
		return new PreferenceRetentionJob(preferences, config, Clock.fixed(now, ZoneOffset.UTC));
	}

	private boolean isStored(String adminKey) {
		return singleValueFrom(Mono.from(preferences.findById(adminKey)).hasElement());
	}

	private Set<UUID> watchedIds(String adminKey) {
		return singleValueFrom(Flux.from(preferences.findWatched(adminKey, 100, 0))
			.map(AdminPreferenceRepository.WatchedRow::patron_request_id)
			.collect(Collectors.toSet()));
	}

	private void forget() {
		singleValueFrom(Mono.from(preferences.deleteByAdminKey(DORMANT)).thenReturn(0L));
		singleValueFrom(Mono.from(preferences.deleteByAdminKey(ACTIVE)).thenReturn(0L));
	}
}
