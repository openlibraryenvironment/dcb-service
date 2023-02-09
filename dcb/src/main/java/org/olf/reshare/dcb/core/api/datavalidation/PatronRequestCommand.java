package org.olf.reshare.dcb.core.api.datavalidation;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;
import java.util.function.Consumer;

@Introspected
@Serdeable
public class PatronRequestCommand {
	@NotBlank
	@NotNull
	UUID id;

	CitationCommand citation;

	RequestorCommand requestor;

	PickupLocationCommand pickupLocation;

	// constructor
	public PatronRequestCommand(UUID id, CitationCommand citation, RequestorCommand requestor, PickupLocationCommand pickupLocation) {
		this.id = id;
		this.citation = citation;
		this.requestor = requestor;
		this.pickupLocation = pickupLocation;
	}

	// getters and setters
	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
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
