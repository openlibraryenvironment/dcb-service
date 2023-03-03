package org.olf.reshare.dcb.core.model;

import java.util.Map;
import java.util.UUID;

import javax.validation.constraints.NotNull;

import org.olf.reshare.dcb.core.interaction.HostLmsClient;

import io.micronaut.core.annotation.NonNull;


public interface HostLms {
	
	@NonNull
	@NotNull
	public UUID getId();
	
	@NonNull
	@NotNull
	public String getName();
	
	@NonNull
	@NotNull
	public Class<? extends HostLmsClient> getType();

	
	@NonNull
	@NotNull
	Map<String, Object> getClientConfig();
}
