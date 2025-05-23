package services.k_int.interaction.alma.types.userRequest;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import services.k_int.interaction.alma.types.WithAttr;
import services.k_int.interaction.alma.types.CodeValuePair;
import services.k_int.interaction.alma.types.AlmaBib;

// https://developers.exlibrisgroup.com/alma/apis/docs/xsd/rest_users.xsd
// https://developers.exlibrisgroup.com/alma/apis/bibs/

@Data
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@Serdeable
public class AlmaRequest {

	// URL components
	@JsonIgnore
	String mmsId;
	// URL also uses HoldingID - but this param is also in the body of the Json Post
	@JsonIgnore
	String pId;

	// These are OUTPUT parameters - do not set on POST requests
	@JsonProperty("request_id")
	String requestId;
	@JsonProperty("additional_id")
	String additionalId;
	@JsonProperty("request_status")
	CodeValuePair requestStatus;


	@JsonProperty("user_primary_id")
	String userPrimaryId;
	@JsonProperty("request_type")
	String requestType;   // HOLD, DIGITIZATION, BOOKING, MOVE, WORK_ORDER.  POST action: Possible values are: HOLD, DIGITIZATION, BOOKING.
  @JsonProperty("description")
	String description;
  @JsonProperty("manual_description")
	String manualDescription;
  @JsonProperty("holding_id")
	String holdingId;
  @JsonProperty("pickup_location_type")
	String pickupLocationType; // LIBRARY, CIRCULATION_DESK, INSTITUTION, USER_HOME_ADDRESS, USER_WORK_ADDRESS.  Relevant and mandatory when request_type = HOLD or BOOKING.
  @JsonProperty("pickup_location_library")
	String pickupLocationLibrary; // See alma Get Libraries API 
  @JsonProperty("pickup_location_circulation_desk")
	String pickupLocationCirculationDesk; // The pickup location circulation desk code.  Relevant when request_type = HOLD or BOOKING, if pickup_location_type = CIRCULATION_DESK.
  @JsonProperty("pickup_location_institution")
	String pickupLocationInstitution; // The pickup location institution code.  Relevant when request_type = HOLD or BOOKING, if the request is to be picked up in a different institution.
  @JsonProperty("target_destination")
	WithAttr targetDestination;
  @JsonProperty("material_type")
	WithAttr materialType;
  @JsonProperty("last_interest_date")
	String lastInterestDate; // <last_interest_date>2015-07-20</last_interest_date>
  @JsonProperty("partial_digitization")
	Boolean partialDigitization;
  @JsonProperty("chapter_or_article_title")
	String chapterOrArticleTitle;
  @JsonProperty("volume")
	String volume;
  @JsonProperty("issue")
	String issue;
  @JsonProperty("part")
	String part;
  @JsonProperty("date_of_publication")
	String dateOfPublication;
  @JsonProperty("chapter_or_article_author")
	String chapterOrArticleAuthor;
  @JsonProperty("required_pages")
	AlmaPageRange requiredPages;
  @JsonProperty("full_chapter")
	String full_chapter;
  @JsonProperty("comment")
	String comment;
  @JsonProperty("booking_start_date") //   "2019-12-13T14:36:48.659Z" 
	String bookingStartDate;
  @JsonProperty("booking_end_date")
	String bookingEndDate;
  @JsonProperty("destination_location")
	WithAttr destinationLocation;
  @JsonProperty("call_number_type")
	WithAttr callNumberType;
  @JsonProperty("call_number")
	String callNumber;
  @JsonProperty("item_policy")
	WithAttr itemPolicy;
  @JsonProperty("due_back_date")
	String dueBackDate;
  @JsonProperty("copyrights_declaration_signed_by_patro")
	String copyDeclarationSigned;
	@JsonProperty("barcode")
	String itemBarcode;
	@JsonProperty("item_id")
	String itemId;
}
