package org.olf.dcb.health;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * A custom health indicator that exposes environment variables through the health endpoint.
 */
@Singleton
@Requires(beans = HealthEndpoint.class)
public class EnvironmentVariablesHealthIndicator implements HealthIndicator {

	private final Environment environment;

	// Core sensitive keywords to mask
	private final List<String> sensitiveKeywords = Arrays.asList("password", "secret", "pass", "pwd");

	// Add separate check for "key" to exclude "keycloak" from being flagged as sensitive
	private final Pattern keyPattern = Pattern.compile("key(?!cloak)", Pattern.CASE_INSENSITIVE);

	private final String maskedValue = "****";

	public EnvironmentVariablesHealthIndicator(Environment environment) {
		this.environment = environment;
	}

	@Override
	public Publisher<HealthResult> getResult() {
		return Mono.just(buildHealthResult());
	}

	private HealthResult buildHealthResult() {
		Map<String, Object> systemEnv = new TreeMap<>();
		Map<String, String> sysEnv = System.getenv();

		for (Map.Entry<String, String> entry : sysEnv.entrySet()) {
			String key = entry.getKey();
			String keyLower = key.toLowerCase();

			// Check if the key contains sensitive words
			boolean containsSensitiveWord = sensitiveKeywords.stream().anyMatch(keyLower::contains);

			// Check for "key" but not as part of "keycloak"
			boolean containsKey = keyPattern.matcher(keyLower).find();

			// If it contains any sensitive words or has "key" (but not just in "keycloak")
			if (containsSensitiveWord || containsKey) {
				systemEnv.put(key, maskedValue);
			} else {
				systemEnv.put(key, entry.getValue());
			}
		}

		// Create details with environment information
		Map<String, Object> details = new HashMap<>();
		details.put("systemEnvironment", systemEnv);
		details.put("activeProfiles", environment.getActiveNames());

		return HealthResult.builder("environment-variables")
			.status(HealthStatus.UP)
			.details(details)
			.build();
	}
}
