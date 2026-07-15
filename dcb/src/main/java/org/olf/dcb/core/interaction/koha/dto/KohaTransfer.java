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
public class KohaTransfer {

	@JsonProperty("transfer_id")
	private Long transferId;

	@JsonProperty("from_library_id")
	private String fromLibraryId;

	@JsonProperty("to_library_id")
	private String toLibraryId;

	@JsonProperty("date_sent")
	private String dateSent;

	@JsonProperty("date_arrived")
	private String dateArrived;

	@JsonProperty("reason")
	private String reason;
}
