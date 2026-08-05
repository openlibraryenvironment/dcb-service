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
public class KohaHoldRequest {

	@JsonProperty("patron_id")
	private Integer patronId;

	@JsonProperty("pickup_library_id")
	private String pickupLibraryId;

	@JsonProperty("biblio_id")
	private Integer biblioId;

	@JsonProperty("item_id")
	private Integer itemId;

	@JsonProperty("hold_date")
	private String holdDate;

	@JsonProperty("expiration_date")
	private String expirationDate;

	@JsonProperty("notes")
	private String notes;

	@JsonProperty("item_type_id")
	private String itemTypeId;

	@JsonProperty("non_priority")
	private Boolean nonPriority;

	@Override
	public String toString() {
		return "KohaHoldRequest{" +
			"patronId=" + patronId +
			", pickupLibraryId='" + pickupLibraryId + '\'' +
			", biblioId=" + biblioId +
			", itemId=" + itemId +
			", holdDate='" + holdDate + '\'' +
			'}';
	}
}
