package org.olf.dcb.core.interaction.koha.dto;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Serdeable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KohaError {

	@JsonProperty("error")
	private String error;

	@JsonProperty("error_code")
	private String errorCode;

	@Override
	public String toString() {
		return "KohaError{" +
			"errorCode='" + errorCode + '\'' +
			", error='" + error + '\'' +
			'}';
	}
}
