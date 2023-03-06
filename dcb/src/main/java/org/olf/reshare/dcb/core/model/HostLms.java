package org.olf.reshare.dcb.core.model;

import java.util.Map;
import java.util.UUID;

import org.olf.reshare.dcb.core.interaction.HostLmsClient;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Id;

public interface HostLms {
	
	@NonNull
	@Id
	public UUID getId();
	
	@NonNull
	public String getName();
	
	@NonNull
	public Class<? extends HostLmsClient> getType();
	
	@NonNull
	Map<String, Object> getClientConfig();
}
