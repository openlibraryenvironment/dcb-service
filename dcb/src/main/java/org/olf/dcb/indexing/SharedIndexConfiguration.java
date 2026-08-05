package org.olf.dcb.indexing;

import java.time.Duration;
import java.util.Optional;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;

@Requires(property = SharedIndexConfiguration.PREFIX)
@ConfigurationProperties(SharedIndexConfiguration.PREFIX)
public record SharedIndexConfiguration (
		
	String name,
	Optional<String> username,
	Optional<String> password,
	Optional<Integer> version,
	Optional<Integer> numberOfReplicas,
	
	Optional<Integer> maxResourceListSize,
	Optional<Duration> minUpdateFrequency
		) {
	public static final String PREFIX = "dcb.index";
	public static final int DEFAULT_NUMBER_OF_REPLICAS = 1;
	
	// This is the default version of the index we are using
	public static final int LATEST_INDEX_VERSION = 2;

	public int effectiveNumberOfReplicas() {
		final int replicas = numberOfReplicas.orElse(DEFAULT_NUMBER_OF_REPLICAS);

		if (replicas < 0) {
			throw new IllegalArgumentException("dcb.index.number-of-replicas must be zero or greater");
		}

		return replicas;
	}
	
}
