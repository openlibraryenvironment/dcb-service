package org.olf.dcb.core.interaction.foundation.customisations.evergreen.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Serdeable
public class EvergreenPayload {
	// This maps to the actual "circ" object inside the event payload
	// if the renewal was successful.
	@JsonProperty("due_date")
	private String dueDate;

	@JsonProperty("circ_lib")
	private Object circLib; // Can be ID or object depending on depth
}
