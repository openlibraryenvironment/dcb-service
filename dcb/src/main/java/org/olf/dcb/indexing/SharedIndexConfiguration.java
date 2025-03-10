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
	
	Optional<Integer> maxResourceListSize,
	Optional<Duration> minUpdateFrequency
		) {
	public static final String PREFIX = "dcb.index";
	
	// This is the default version of the index we are using
	public static final int LATEST_INDEX_VERSION = 1;
	
}
