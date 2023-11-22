package org.olf.dcb.core.model;

import java.util.Map;
import java.util.UUID;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected
public interface HostLms {
	UUID getId();

	String getCode();

	String getName();

	Class<?> getType();
	Class<?> getIngestSourceType();

	Map<String, Object> getClientConfig();
}
