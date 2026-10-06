package org.olf.dcb.core.svc;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import org.olf.dcb.core.api.serde.ClusterSizeStat;
import org.olf.dcb.core.api.serde.CollectionOverlapStat;
import org.olf.dcb.core.api.serde.CollectionProfileStat;
import org.olf.dcb.core.api.serde.CollectionTotalsStat;
import org.olf.dcb.core.api.serde.SourceFormatStat;
import org.olf.dcb.storage.BibRepository;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.micronaut.context.annotation.Value;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The collection-analysis queries, each shared by every caller while it runs and cached once it
 * has answered. Why a shared computation rather than a permit, and why on demand rather than a
 * scheduled rollup: {@code docs/insights.md} part 5.
 */
@Slf4j
@Singleton
public class CollectionAnalysisService {

	/** Overlap is keyed by the selected Host LMS codes; the cache caps how many are kept. */
	private static final int MAX_CACHED_OVERLAPS = 1_000;

	private final BibRepository bibRepository;
	private final Duration ttl;
	private final String statementTimeoutMillis;
	private final Mono<CollectionSnapshot> snapshot;
	private final Cache<String, Mono<List<CollectionOverlapStat>>> overlaps;

	/**
	 * No defaults on the annotations. application.yml declares both, so a default here could
	 * never fire, and the unreachable copy is the one that drifts.
	 */
	@Inject
	public CollectionAnalysisService(BibRepository bibRepository,
		@Value("${dcb.insights.collection-analysis.cache-ttl}") Duration ttl,
		@Value("${dcb.insights.collection-analysis.statement-timeout}") Duration statementTimeout) {

		this.bibRepository = bibRepository;
		this.ttl = ttl;
		this.statementTimeoutMillis = String.valueOf(statementTimeout.toMillis());

		this.snapshot = shared("snapshot", () ->
			Flux.from(bibRepository.getCollectionSnapshot()).collectList().map(CollectionSnapshot::from));

		this.overlaps = Caffeine.newBuilder()
			.maximumSize(MAX_CACHED_OVERLAPS)
			.expireAfterWrite(ttl)
			.build();

		log.info("Collection analysis: {} cache, {} statement timeout", ttl, statementTimeout);
	}

	/** The consortium headline: distinct titles, singly held titles, holdings, sources. */
	public Mono<CollectionTotalsStat> totals() {
		return snapshot.map(CollectionSnapshot::totals);
	}

	/** Per source system: works contributed, and how many of those nobody else holds. */
	public Mono<List<CollectionProfileStat>> profile() {
		return snapshot.map(CollectionSnapshot::profile);
	}

	/** How many source systems hold each work - the honesty check on {@link #profile()}. */
	public Mono<List<ClusterSizeStat>> clusterSizeDistribution() {
		return snapshot.map(CollectionSnapshot::clusterSizes);
	}

	/** Format mix per source system, counted per work so it reconciles with the profile. */
	public Mono<List<SourceFormatStat>> formatProfile() {
		return snapshot.map(CollectionSnapshot::formats);
	}

	/**
	 * Who duplicates these libraries. One selection against all others, never the full matrix -
	 * see BibRepository.getCollectionOverlapForLibrary.
	 *
	 * @param libraryCode comma-separated Host LMS codes from StatsScopeGuard, never straight
	 *   from the query string.
	 */
	public Mono<List<CollectionOverlapStat>> overlapFor(String libraryCode) {
		final var codes = String.join(",", Arrays.stream(libraryCode.split(","))
			.map(String::trim)
			.filter(code -> !code.isEmpty())
			.distinct()
			.sorted()
			.toList());

		return overlaps.get(codes, key -> shared("overlap:" + key, () ->
			Flux.from(bibRepository.getCollectionOverlapForLibrary(key)).collectList()));
	}

	// Mono.cache does not cancel its source when a subscriber cancels, so a computation that
	// outlives a proxy timeout still finishes and fills the cache, and every caller in the
	// meantime joins it instead of starting another pass. A failure is not kept.
	private <T> Mono<T> shared(String name, Supplier<Mono<T>> work) {
		return Mono.defer(() -> {
				final var startedAt = System.nanoTime();

				return withStatementTimeout(work)
					.doOnSuccess(value -> log.info("Collection analysis [{}] took {}ms", name,
						(System.nanoTime() - startedAt) / 1_000_000))
					.doOnError(error -> log.warn("Collection analysis [{}] failed after {}ms", name,
						(System.nanoTime() - startedAt) / 1_000_000, error));
			})
			.cache(value -> ttl, error -> Duration.ZERO, () -> Duration.ZERO);
	}

	// One connection for the timeout and the query it bounds: set_config(..., true) lasts
	// only until this transaction ends.
	@Transactional(readOnly = true)
	protected <T> Mono<T> withStatementTimeout(Supplier<Mono<T>> work) {
		return Mono.from(bibRepository.setLocalStatementTimeout(statementTimeoutMillis))
			.then(Mono.defer(work));
	}
}
