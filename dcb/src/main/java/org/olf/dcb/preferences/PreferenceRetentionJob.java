package org.olf.dcb.preferences;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Supplier;

import org.olf.dcb.storage.AdminPreferenceRepository;
import org.reactivestreams.Publisher;

import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import services.k_int.micronaut.scheduling.processor.AppTask;

/**
 * Removes the preferences of staff who have not used either app for the retention period (saved
 * views and watches go with them), and watches whose request has gone or finished long ago.
 * Does nothing while preferences are off, so switching off cannot quietly delete everyone's settings.
 * No cluster lock: every delete is idempotent, as for notes.
 */
@Slf4j
@Singleton
public class PreferenceRetentionJob {
	private final AdminPreferenceRepository preferences;
	private final PreferencesConfig config;
	private final Clock clock;

	public PreferenceRetentionJob(AdminPreferenceRepository preferences, PreferencesConfig config) {
		this(preferences, config, Clock.systemUTC());
	}

	PreferenceRetentionJob(AdminPreferenceRepository preferences, PreferencesConfig config, Clock clock) {
		this.preferences = preferences;
		this.config = config;
		this.clock = clock;
	}

	@AppTask
	@Scheduled(initialDelay = "10m", fixedDelay = "24h")
	public void scheduledSweep() {
		if (!config.isOn()) {
			return;
		}

		sweep()
			.doOnSuccess(removed -> log.info("Preference retention: removed {} dormant user(s) and {} watch(es)",
				removed.getT1(), removed.getT2()))
			.doOnError(error -> log.error("Preference retention sweep failed", error))
			.subscribe();
	}

	/** @return how many users, then watches, were removed */
	public Mono<Tuple2<Long, Long>> sweep() {
		final Instant now = clock.instant();
		final Instant cutoff = now.minus(config.getRetention());
		final Instant endedBefore = now.minus(config.getFinishedWatchRetention());
		final int batch = config.getSweepBatchSize();

		return inBatches(() -> preferences.deleteNotSeenSince(cutoff, batch), 0L)
			.zipWith(inBatches(() -> preferences.deleteWatchesGoneOrEndedBefore(
				AdminPreferenceService.FINISHED_STATUSES, endedBefore, batch), 0L));
	}

	// Batched, so the first pass after a long gap is never one huge transaction
	private static Mono<Long> inBatches(Supplier<Publisher<Long>> batch, long soFar) {
		return Mono.from(batch.get())
			.defaultIfEmpty(0L)
			.flatMap(deleted -> deleted == 0 ? Mono.just(soFar) : inBatches(batch, soFar + deleted));
	}
}
