package org.olf.dcb.core.api.discovery;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.olf.dcb.security.discovery.DiscoveryConfig;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * Institution sign-ins resolved per trusted discovery service, counted in one-minute windows
 * that open at a service's first call.
 */
@Singleton
@Requires(property = DiscoveryConfig.ENABLED, value = "true")
public class PatronResolveRateLimiter {
	static final Duration WINDOW = Duration.ofMinutes(1);

	private final DiscoveryConfig properties;

	// Keyed by trusted service id: a handful, from configuration, never caller input
	private final Cache<String, AtomicInteger> windows = Caffeine.newBuilder()
		.maximumSize(1_000)
		.expireAfterWrite(WINDOW)
		.build();

	public PatronResolveRateLimiter(DiscoveryConfig properties) {
		this.properties = properties;
	}

	public boolean tryAcquire(String serviceId) {
		return windows.get(serviceId, id -> new AtomicInteger()).incrementAndGet()
			<= properties.getPatronResolvesPerMinute();
	}
}
