package org.olf.dcb.core.model;

import java.util.Map;
import java.util.UUID;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public interface HostLms {
	UUID getId();

	String getCode();

	String getName();

	Class<?> getClientType();
	Class<?> getIngestSourceType();
	
	String getSuppressionRulesetName();

	Map<String, Object> getClientConfig();
}
