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
public class KohaCheckout {

	@JsonProperty("checkout_id")
	private Long checkoutId;

	@JsonProperty("patron_id")
	private Long patronId;

	@JsonProperty("item_id")
	private Long itemId;

	@JsonProperty("external_id")
	private String externalId;

	@JsonProperty("library_id")
	private String libraryId;

	@JsonProperty("due_date")
	private String dueDate;

	@JsonProperty("checkin_date")
	private String checkinDate;

	@JsonProperty("checkout_date")
	private String checkoutDate;

	@JsonProperty("auto_renew")
	private Boolean autoRenew;

	@JsonProperty("renewals_count")
	private Integer renewalsCount;

	@Override
	public String toString() {
		return "KohaCheckout{" +
			"checkoutId=" + checkoutId +
			", patronId=" + patronId +
			", externalId='" + externalId + '\'' +
			", dueDate='" + dueDate + '\'' +
			", libraryId='" + libraryId + '\'' +
			'}';
	}
}
