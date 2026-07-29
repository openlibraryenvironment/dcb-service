package org.olf.dcb.core.interaction.foundation.customisations.evergreen.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Serdeable
public class EvergreenResponse {
	// OpenSRF gateway usually wraps the actual content in a wrapper
	// or sometimes returns the object directly. This structure matches
	// a standard gateway response wrapper.
	@JsonProperty("content")
	private EvergreenEvent content;
}
