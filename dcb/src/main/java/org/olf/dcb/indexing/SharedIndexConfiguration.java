package org.olf.dcb.indexing;

import java.util.Optional;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties(SharedIndexConfiguration.PREFIX)
public record SharedIndexConfiguration (
		
	String name,
	Optional<String> username,
	Optional<String> password
		) {
	public static final String PREFIX = "dcb.index";
	
	
}
