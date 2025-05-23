package services.k_int.interaction.alma.types.userRequest;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import services.k_int.interaction.alma.types.CodeValuePair;
import services.k_int.interaction.alma.types.WithAttr;

@Data
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@Serdeable
public class AlmaRequestResponse {

	@JsonProperty("title")
	String title;

	@JsonProperty("author")
	String author;

	@ToString.Include
	@JsonProperty("request_id")
	String requestId;

	@JsonProperty("additional_id")
	String additionalId;

	@ToString.Include
	@JsonProperty("request_status")
	String requestStatus;

	@JsonProperty("user_primary_id")
	String userPrimaryId;

	@JsonProperty("request_type")
	String requestType;

	@JsonProperty("request_sub_type")
	CodeValuePair requestSubType;

	@JsonProperty("description")
	String description;

	@JsonProperty("volume")
	String volume;

	@JsonProperty("issue")
	String issue;

	@JsonProperty("part")
	String part;

	@JsonProperty("comment")
	String comment;

	@JsonProperty("barcode")
	String itemBarcode;

	@JsonProperty("item_id")
	String itemId;

	@JsonProperty("mms_id")
	String mmsId;

	@JsonProperty("pickup_location")
	String pickupLocation;

	@JsonProperty("pickup_location_type")
	String pickupLocationType;

	@JsonProperty("pickup_location_library")
	String pickupLocationLibrary;

	@JsonProperty("managed_by_library")
	String managedByLibrary;

	@JsonProperty("managed_by_circulation_desk")
	String managedByCirculationDesk;

	@JsonProperty("managed_by_library_code")
	String managedByLibraryCode;

	@JsonProperty("managed_by_circulation_desk_code")
	String managedByCirculationDeskCode;

	@JsonProperty("material_type")
	WithAttr materialType;

	@JsonProperty("date_of_publication")
	String dateOfPublication;

	@JsonProperty("request_date")
	String requestDate;

	@JsonProperty("request_time")
	String requestTime;

	@JsonProperty("task_name")
	String taskName;

	@JsonProperty("expiry_date")
	String expiryDate;
}
