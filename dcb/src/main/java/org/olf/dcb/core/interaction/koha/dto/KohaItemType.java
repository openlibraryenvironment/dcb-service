package org.olf.dcb.core.interaction.koha.dto;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Serdeable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KohaItemType {

	@JsonProperty("item_type_id")
	private String itemTypeId;

	@JsonProperty("parent_type")
	private String parentType;

	@JsonProperty("description")
	private String description;

	@JsonProperty("bookable")
	private Boolean bookable;

	@JsonProperty("rentalcharge")
	private Double rentalCharge;

	@JsonProperty("daily_rental_charge")
	private Double dailyRentalCharge;

	@JsonProperty("daily_rental_charge_calendar")
	private Boolean dailyRentalChargeCalendar;

	@JsonProperty("hourly_rental_charge")
	private Double hourlyRentalCharge;

	@JsonProperty("hourly_rental_charge_calendar")
	private Boolean hourlyRentalChargeCalendar;

	@JsonProperty("default_replacement_cost")
	private Double defaultReplacementCost;

	@JsonProperty("process_fee")
	private Double processFee;

	@JsonProperty("not_for_loan_status")
	private Boolean notForLoanStatus;

	@JsonProperty("image_url")
	private String imageUrl;

	@JsonProperty("summary")
	private String summary;

	@JsonProperty("checkin_message")
	private String checkinMessage;

	@JsonProperty("checkin_message_type")
	private String checkinMessageType;

	@JsonProperty("sip_media_type")
	private String sipMediaType;

	@JsonProperty("hide_in_opac")
	private Boolean hideInOpac;

	@JsonProperty("searchcategory")
	private String searchCategory;

	@JsonProperty("automatic_checkin")
	private Boolean automaticCheckin;

	@JsonProperty("translated_descriptions")
	private List<Object> translatedDescriptions;

	@JsonProperty("checkprevcheckout")
	private String checkPrevCheckout;
}
