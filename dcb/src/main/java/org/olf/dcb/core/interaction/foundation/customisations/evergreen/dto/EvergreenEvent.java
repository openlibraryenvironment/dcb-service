package org.olf.dcb.core.interaction.foundation.customisations.evergreen.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import lombok.Data;

@Data
@Serdeable
public class EvergreenEvent {
	@JsonProperty("ilsevent")
	private int id;

	@JsonProperty("textcode")
	private String textCode;

	@JsonProperty("desc")
	private String desc;

	@JsonProperty("payload")
	private EvergreenPayload payload;
}
