package org.olf.dcb.core.model;

import java.util.Map;
import java.util.UUID;

import org.olf.dcb.core.interaction.HostLmsClient;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Id;

@Introspected
public interface HostLms {
	
	@NonNull
	@Id
	public UUID getId();
	
	@NonNull
	public String getCode();
	
	@NonNull
	public String getName();
	
	@NonNull
	public Class<? extends HostLmsClient> getType();
	
	@NonNull
	Map<String, Object> getClientConfig();
}
