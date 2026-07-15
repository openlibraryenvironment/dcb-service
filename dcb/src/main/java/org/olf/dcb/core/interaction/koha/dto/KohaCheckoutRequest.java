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
public class KohaCheckoutRequest {

	@JsonProperty("patron_id")
	private Long patronId;

	@JsonProperty("item_id")
	private Long itemId;

	@JsonProperty("external_id")
	private String externalId;

	@Override
	public String toString() {
		return "KohaCheckoutRequest{" +
			"patronId=" + patronId +
			", itemId=" + itemId +
			", externalId='" + externalId + '\'' +
			'}';
	}
}
