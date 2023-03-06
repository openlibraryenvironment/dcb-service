package org.olf.reshare.dcb.core.model;

import java.util.UUID;
import javax.validation.constraints.NotNull;
import org.olf.reshare.dcb.core.model.DataAgency.DataAgencyBuilder;
import io.micronaut.core.annotation.NonNull;

public interface Agency {

	@NonNull
	@NotNull
	public UUID getId();

	@NonNull
	@NotNull
	public String getName();

	public HostLms getHostLms();

	public static DataAgencyBuilder builder() {
		return DataAgency.builder();
	}
}
