package org.olf.dcb.core.model;

import java.util.UUID;

import org.olf.dcb.core.model.DataAgency.DataAgencyBuilder;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Id;

public interface Agency {
	@NonNull
	@Id
	UUID getId();

	@NonNull
	String getCode();

	@NonNull
	String getName();

	@NonNull
	<T extends HostLms> T getHostLms();

	static DataAgencyBuilder builder() {
		return DataAgency.builder();
	}
}
