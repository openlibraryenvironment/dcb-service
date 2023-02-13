package org.olf.reshare.dcb.core.api.datavalidation;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;

@Serdeable
public class PatronRequestCommand {

	@Nullable
	UUID id;

	@NotNull
	@NotBlank
	CitationCommand citation;

	@NotNull
	@NotBlank
	RequestorCommand requestor;
	@NotNull
	@NotBlank
	PickupLocationCommand pickupLocation;

	public PatronRequestCommand() {}


	public PatronRequestCommand(@Nullable UUID id, @NotNull CitationCommand citation, @NotNull RequestorCommand requestor, @NotNull PickupLocationCommand pickupLocation) {
		this.id = id;
		this.citation = citation;
		this.requestor = requestor;
		this.pickupLocation = pickupLocation;
	}

	@Nullable
	public UUID getId() {
		return id;
	}

	public void setId(@Nullable UUID id) {
		this.id = id;
	}

	public CitationCommand getCitation() {
		return citation;
	}

	public void setCitation(CitationCommand citation) {
		this.citation = citation;
	}

	public RequestorCommand getRequestor() {
		return requestor;
	}

	public void setRequestor(RequestorCommand requestor) {
		this.requestor = requestor;
	}

	public PickupLocationCommand getPickupLocation() {
		return pickupLocation;
	}

	public void setPickupLocation(PickupLocationCommand pickupLocation) {
		this.pickupLocation = pickupLocation;
	}
}
