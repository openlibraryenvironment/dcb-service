package org.olf.reshare.dcb.core.model;

import java.util.UUID;

import org.olf.reshare.dcb.core.model.DataAgency.DataAgencyBuilder;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Id;

public interface Agency {

	@NonNull
	@Id
	public UUID getId();

	@NonNull
	public String getName();

	@NonNull
	public <T extends HostLms> T getHostLms();

	public static DataAgencyBuilder builder() {
		return DataAgency.builder();
	}
}
