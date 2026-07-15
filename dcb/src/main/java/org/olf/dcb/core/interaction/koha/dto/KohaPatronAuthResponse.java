package org.olf.dcb.core.interaction.koha.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import lombok.*;

/** DTO for the response we get from Koha when we try and authenticate a patron with it**/
@Serdeable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KohaPatronAuthResponse {
	@JsonProperty("patron_id")
	private Long patronId;

	@JsonProperty("cardnumber")
	private String cardnumber;

	@JsonProperty("userid")
	private String userid;
}
