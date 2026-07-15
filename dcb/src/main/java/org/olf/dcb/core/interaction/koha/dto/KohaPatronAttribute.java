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
public class KohaPatronAttribute {

	@JsonProperty("extended_attribute_id")
	private Long extendedAttributeId;

	@JsonProperty("type")
	private String type;

	@JsonProperty("value")
	private String value;

	@Override
	public String toString() {
		return "KohaPatronAttribute{" +
			"extendedAttributeId=" + extendedAttributeId +
			", type='" + type + '\'' +
			", value='" + value + '\'' +
			'}';
	}
}
