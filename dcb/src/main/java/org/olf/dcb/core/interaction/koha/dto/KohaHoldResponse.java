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
public class KohaHoldResponse {

	@JsonProperty("hold_id")
	private Long holdId;

	@JsonProperty("patron_id")
	private Long patronId;

	@JsonProperty("biblio_id")
	private Long biblioId;

	@JsonProperty("item_id")
	private Long itemId;

	@JsonProperty("pickup_library_id")
	private String pickupLibraryId;

	@JsonProperty("status")
	private String status;

	@JsonProperty("priority")
	private Integer priority;

	@JsonProperty("hold_date")
	private String holdDate;

	@JsonProperty("waiting_date")
	private String waitingDate;

	@JsonProperty("expiration_date")
	private String expirationDate;

	@JsonProperty("cancellation_date")
	private String cancellationDate;

	@JsonProperty("cancellation_reason")
	private String cancellationReason;

	@JsonProperty("suspended")
	private Boolean suspended;

	@JsonProperty("item_level")
	private Boolean itemLevel;

	@JsonProperty("item")
	private KohaItem item;

	@Override
	public String toString() {
		return "KohaHoldResponse{" +
			"holdId=" + holdId +
			", patronId=" + patronId +
			", status='" + status + '\'' +
			", pickupLibraryId='" + pickupLibraryId + '\'' +
			", biblioId=" + biblioId +
			", itemId=" + itemId +
			'}';
	}
}
