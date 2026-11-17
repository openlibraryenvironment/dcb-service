package org.olf.dcb.core.interaction.koha.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** GET /api/v1/sysprefs (Koha 26.05 and later): one system preference. */
@Serdeable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KohaSystemPreference {
	@JsonProperty("variable")
	private String variable;

	@JsonProperty("value")
	private String value;
}
