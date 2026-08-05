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
public class KohaRecall {

	@JsonProperty("recall_id")
	private Long recallId;

	@JsonProperty("patron_id")
	private Long patronId;

	@JsonProperty("created_date")
	private String createdDate;

	@JsonProperty("biblio_id")
	private Long biblioId;

	@JsonProperty("pickup_library_id")
	private String pickupLibraryId;

	@JsonProperty("completed_date")
	private String completedDate;

	@JsonProperty("notes")
	private String notes;

	@JsonProperty("priority")
	private Integer priority;

	@JsonProperty("status")
	private String status;

	@JsonProperty("timestamp")
	private String timestamp;

	@JsonProperty("item_id")
	private Long itemId;

	@JsonProperty("waiting_date")
	private String waitingDate;

	@JsonProperty("expiration_date")
	private String expirationDate;

	@JsonProperty("completed")
	private Boolean completed;

	@JsonProperty("item_level")
	private Boolean itemLevel;
}
