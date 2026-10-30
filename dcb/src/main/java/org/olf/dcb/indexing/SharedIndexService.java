package org.olf.dcb.indexing;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.olf.dcb.core.clustering.model.ClusterRecord;
import org.olf.dcb.indexing.bulk.IndexOperation;
import org.reactivestreams.Publisher;

import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySource.Origin;
import io.micronaut.context.env.PropertySource.PropertyConvention;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.management.endpoint.info.InfoSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SharedIndexService extends InfoSource {

	static final String PROPERTY_PREFIX = "dcb.index.backend";

	@SingleResult
	@NonNull
	Publisher<Void> initialize();

	@SingleResult
	@NonNull
	Publisher<Void> deleteAll();

	void add(UUID clusterID);

	void update(UUID clusterID);

	void delete(UUID clusterID);

	Mono<Void> deleteDocsIndexedBefore(Instant before);

	Publisher<List<IndexOperation<UUID, ClusterRecord>>> expandAndProcess(Flux<List<UUID>> idFlux);

	/**
	 * This method should allow errors to propagate as returning an empty will cause
	 * the emtpy value to be cached indefinitely. Returning an error will cause a
	 * retry.
	 * 
	 * @return Reported backend distribution i.e. Elasticsearch
	 */
	@NonNull
	@SingleResult
	Publisher<String> getBackendDistribution();

	/**
	 * This method should allow errors to propagate as returning an empty will cause
	 * the emtpy value to be cached indefinitely. Returning an error will cause a
	 * retry.
	 * 
	 * @return Reported backend version
	 */
	@NonNull
	@SingleResult
	Publisher<String> getBackendVersion();

	@NonNull
	@SingleResult
	default Publisher<Map<String, String>> getExtraInfo() {
		return Mono.empty();
	}

	@SuppressWarnings("unused")
	private <T> Mono<T> cacheFromStringPublisher(Supplier<Publisher<T>> supplier) {
		return Mono.fromSupplier(supplier)
				.flatMap(Mono::from)
				.cache(
					// Cache the value forever
					_val -> Duration.ofMillis(Long.MAX_VALUE),
					// Errors are not cached but retried.
					e -> Duration.ZERO,
					// Cache empty forever
					() -> Duration.ofMillis(Long.MAX_VALUE))

				// Silence the above error AFTER the cache
				.onErrorComplete();
	}

	final Map<String, Mono<String>> infoMap = new HashMap<>();

	@SuppressWarnings("unused")
	public default Publisher<PropertySource> getSource() {

		final var dist = infoMap
				.computeIfAbsent("distribution", key -> cacheFromStringPublisher(this::getBackendDistribution))
				.map(d -> Map.entry("distribution", d));

		final var version = infoMap.computeIfAbsent("version", key -> cacheFromStringPublisher(this::getBackendVersion))
				.map(v -> Map.entry("version", v));

		final var extras = cacheFromStringPublisher(this::getExtraInfo).flatMapIterable(Map::entrySet);

		return Flux.concat(dist, version, extras)
				.collect(Collectors.toMap(entry -> PROPERTY_PREFIX + "." + entry.getKey(), entry -> (Object) entry.getValue()))
				.map(map -> {
					PropertySource.of(PROPERTY_PREFIX, map, PropertyConvention.JAVA_PROPERTIES, Origin.of(PROPERTY_PREFIX));
					return PropertySource.of(PROPERTY_PREFIX, map);
				});

	}
}
